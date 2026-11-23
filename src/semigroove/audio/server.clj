(ns semigroove.audio.server
  "Bring up a local scsynth and connect Overtone to it. Needed on Apple silicon,
   where Overtone's internal booter cannot start scsynth."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [overtone.core :as o]
            [overtone.libs.deps :as deps]
            [semigroove.audio :as a]
            [semigroove.synthdefs :refer [semigroove-note]]
            [semigroove.wait :as wait]))

(def scsynth-path "/Applications/SuperCollider.app/Contents/Resources/scsynth")

(def port 57110)

(def boot-timeout-ms
  "How long boot! waits for scsynth to bind its port before giving up."
  10000)

(defn- port-bound?
  "Whether something already holds UDP PORT. scsynth opens no TCP listener, so
   readiness is detected by failing to bind the port ourselves."
  [port]
  (try (doto (java.net.DatagramSocket. (int port)) .close) false
       (catch java.net.BindException _ true)))

(defn boot!
  "Start scsynth, connect Overtone, and unmute. Safe to call when scsynth is
   already running: the second process fails to bind the port and exits, and
   this one just connects. Throws, naming the path and port, when scsynth is
   missing or doesn't come up within boot-timeout-ms."
  []
  (when-not (.exists (io/file scsynth-path))
    (throw (ex-info (str "No scsynth at " scsynth-path "; is SuperCollider installed?")
                    {:path scsynth-path})))
  (future (sh scsynth-path "-u" (str port) "-i" "0"))
  (when (= :timed-out (wait/until #(port-bound? port) boot-timeout-ms 100))
    (throw (ex-info (str "scsynth did not bind UDP port " port " within "
                         (quot boot-timeout-ms 1000) " s")
                    {:path scsynth-path :port port})))
  (o/connect-external-server port)
  (deps/wait-until-deps-satisfied :server-ready)
  ;; loading semigroove-note syncs with the server, flushing the mixer's
  ;; pending /s_new so the volume call below lands on a real node
  (o/kill (semigroove-note :amp 0))
  (o/volume 1)
  (a/open!))
