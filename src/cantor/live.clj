(ns cantor.live
  (:require [cantor.audio.scheduler :as sched]
            [cantor.audio :as a]
            [cantor.core.stream :as s]
            [overtone.core :refer [at]]))

(defn- nanos->ms [nanos]
  (/ nanos 1e6))

(defn- fire-action!
  [{:keys [type pitch vel time-nanos]}]
  (let [t (nanos->ms time-nanos)]
    (case type
      :on  (at t (a/note-on  pitch vel))
      :off (at t (a/note-off pitch)))))

(defn- tick-loop []
  (loop []
    (when (:running @sched/scheduler-state)
      (let [now      (System/nanoTime)
            [s' due] (sched/step @sched/scheduler-state now)]
        (reset! sched/scheduler-state s')
        (doseq [action due]
          (try (fire-action! action)
               (catch Exception e
                 (println "scheduler fire-action! error:" (.getMessage e))))))
      (Thread/sleep 10)
      (recur))))

(defn play
  "Installs the stream and (re)starts the scheduler.
  Clears pending actions + releases all current voices first."
  [stream]
  (a/open!)
  (a/release-all)
  (swap! sched/scheduler-state assoc
         :stream      stream
         :beat        0
         :start-nanos (System/nanoTime)
         :pending     [])
  (when-not (:running @sched/scheduler-state)
    (swap! sched/scheduler-state assoc :running true)
    (future (tick-loop)))
  :playing)

(defn stop
  "Releases all voices and swaps silence into the scheduler."
  []
  (swap! sched/scheduler-state assoc
         :stream  (s/silence)
         :pending [])
  (a/release-all)
  :stopped)

(defn set-tempo
  "Changes BPM. Takes effect on the next tick. No restart."
  [bpm]
  (swap! sched/scheduler-state assoc :tempo bpm)
  bpm)
