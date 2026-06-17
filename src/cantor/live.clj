(ns cantor.live
  (:require [cantor.audio.scheduler :as sched]
            [cantor.audio :as a]
            [cantor.core.stream :as s]
            [cantor.hardware.midi :as midi]
            [overtone.core :refer [at]]))

(defn- nanos->epoch-ms
  "Map a scheduler timestamp (System/nanoTime monotonic clock) into the
  epoch-millisecond domain Overtone's `at` expects. (target - nanoTime) is the
  time-until-event; adding it to the current epoch ms yields the absolute wall
  time at which the OSC bundle should fire."
  [target-nanos]
  (+ (System/currentTimeMillis)
     (/ (- target-nanos (System/nanoTime)) 1e6)))

(defn- fire-action!
  [{:keys [type pitch vel time-nanos]}]
  (let [t (nanos->epoch-ms time-nanos)]
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

;; --- MIDI control surface (M7) ------------------------------------------------

(defn list-midi-inputs
  "Print connected MIDI input devices. Use a name substring with start-midi."
  []
  (let [devs (midi/list-inputs)]
    (if (empty? devs)
      (println "No MIDI input devices found.")
      (doseq [{:keys [name description]} devs]
        (println (str "  " name (when description (str "  (" description ")"))))))
    devs))

(defn start-midi
  "Route a MIDI keyboard into the live pipeline. DEV is an optional name
   substring (see list-midi-inputs). Boots a silent session first if nothing
   is playing, since notes only sound while the tick-loop runs."
  ([] (start-midi nil))
  ([dev]
   (when-not (:running @sched/scheduler-state)
     (play (s/silence)))
   (if dev (midi/start! dev) (midi/start!))
   :midi-started))

(defn stop-midi
  "Tear down MIDI input. Leaves the audio session running."
  []
  (midi/stop!)
  :midi-stopped)
