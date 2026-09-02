(ns semigroove.live
  (:require [semigroove.audio.scheduler :as sched]
            [semigroove.audio :as a]
            [semigroove.core.stream :as s]
            [semigroove.hardware.midi :as midi]
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
  "Schedule one note action with Overtone's `at`, converting the scheduler's
  monotonic-nanos timestamp into the wall-clock ms `at` expects."
  [{:keys [type pitch vel time-nanos]}]
  (let [t (nanos->epoch-ms time-nanos)]
    (case type
      :on  (at t (a/note-on  pitch vel))
      :off (at t (a/note-off pitch)))))

(defn- tick-loop
  "The scheduler heartbeat: pull everything due, fire it, sleep 10ms, and repeat
  until :running goes false. Runs on its own future."
  []
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
  "Play STREAM as the whole session on the :main track, releasing every current
  voice and resetting the transport first. This wipes any other tracks and the
  mute/solo state; layer extra streams on afterward with add-track."
  [stream]
  (a/open!)
  (a/release-all)
  (swap! sched/scheduler-state assoc
         :tracks      {:main stream}
         :muted       #{}
         :solo        #{}
         :beat        0
         :start-nanos (System/nanoTime)
         :pending     [])
  (when-not (:running @sched/scheduler-state)
    (swap! sched/scheduler-state assoc :running true)
    (future (tick-loop)))
  :playing)

(defn stop
  "Clear every track and release every voice. Leaves the tick-loop running, so
  play picks straight back up."
  []
  (swap! sched/scheduler-state assoc
         :tracks  {}
         :pending [])
  (a/release-all)
  :stopped)

(defn set-tempo
  "Changes BPM. Takes effect on the next tick. No restart."
  [bpm]
  (swap! sched/scheduler-state assoc :tempo bpm)
  bpm)

;; --- Tracks and mixer --------------------------------------------------------
;; Pure state transformers live in semigroove.audio.scheduler; these are the
;; imperative surface you drive by hand. The scheduler ones stay testable; the
;; swapping and voice-gating happens here.

(defn add-track
  "Layer a named stream onto the running session, leaving the other tracks and
  the transport alone. Replaces whatever was on NAME."
  [name stream]
  (swap! sched/scheduler-state sched/install-track name stream)
  name)

(defn drop-track
  "Remove a named track and gate off whatever it was still sounding."
  [name]
  (a/release-track name)
  (swap! sched/scheduler-state sched/remove-track name)
  name)

(defn mute
  "Mute a track. Like a tempo change, it lands within the scheduler's lookahead
  window; notes already queued up to ~100ms out still fire."
  [track]
  (swap! sched/scheduler-state sched/mute-track track)
  track)

(defn unmute
  "Unmute a track; scheduling resumes on the next tick."
  [track]
  (swap! sched/scheduler-state sched/unmute-track track)
  track)

(defn solo
  "Solo one track: everything else drops out until unsolo. Replaces any existing
  solo rather than adding to it."
  [track]
  (swap! sched/scheduler-state sched/solo-track track)
  track)

(defn unsolo
  "Clear the solo, so mutes decide again."
  []
  (swap! sched/scheduler-state sched/unsolo)
  :unsoloed)

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
