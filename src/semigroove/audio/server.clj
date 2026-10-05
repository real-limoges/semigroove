(ns semigroove.audio.server
  "Bring up a local scsynth and connect Overtone to it. Needed on Apple silicon,
   where Overtone's internal booter cannot start scsynth."
  (:require [clojure.java.shell :refer [sh]]
            [overtone.core :as o]
            [overtone.libs.deps :as deps]
            [semigroove.audio :as a]
            [semigroove.synthdefs :refer [semigroove-note]]))

(def scsynth-path "/Applications/SuperCollider.app/Contents/Resources/scsynth")

(def port 57110)

(defn- wait-for-scsynth
  "Block until scsynth has bound UDP PORT. scsynth opens no TCP listener, so
   readiness is detected by failing to bind the port ourselves."
  [port]
  (loop []
    (when (try (doto (java.net.DatagramSocket. (int port)) .close) true
               (catch java.net.BindException _ false))
      (Thread/sleep 100)
      (recur))))

(defn boot!
  "Start scsynth, connect Overtone, and unmute. Safe to call when scsynth is
   already running: the second process fails to bind the port and exits, and
   this one just connects."
  []
  (future (sh scsynth-path "-u" (str port) "-i" "0"))
  (wait-for-scsynth port)
  (o/connect-external-server port)
  (deps/wait-until-deps-satisfied :server-ready)
  ;; loading semigroove-note syncs with the server, flushing the mixer's
  ;; pending /s_new so the volume call below lands on a real node
  (o/kill (semigroove-note :amp 0))
  (o/volume 1)
  (a/open!))
