(ns user
  (:require [overtone.core :refer :all]
            [overtone.libs.deps :as deps]
            [clojure.java.shell :refer [sh]]))

(def ^:private scsynth
  "/Applications/SuperCollider.app/Contents/Resources/scsynth")

(defn- wait-for-scsynth [port]
  (loop []
    (when-not (try (doto (java.net.Socket. "localhost" port) .close) true
                   (catch Exception _ false))
      (Thread/sleep 100)
      (recur))))

(defn boot!
  "Start scsynth, connect Overtone to it, and unmute. Safe to call if
  scsynth is already running — it just connects to the existing process."
  []
  (future (sh scsynth "-u" "57110" "-i" "0"))
  (wait-for-scsynth 57110)
  (connect-external-server 57110)
  (deps/wait-until-deps-satisfied :server-ready)
  ; load-synthdef (called inside demo) uses with-server-sync, which flushes all
  ; pending /s_new messages for the mixer nodes before we set volume.
  ; Without this, (volume 1) hits an empty output group and has no effect.
  (demo 0.001 (sin-osc 440))
  (volume 1))
