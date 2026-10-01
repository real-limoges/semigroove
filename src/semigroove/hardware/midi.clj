(ns semigroove.hardware.midi
  (:require [semigroove.audio :as a]
            [semigroove.audio.scheduler :as sched]
            [clojure.core.async :refer [chan put! go-loop <! close!]]
            [overtone.midi :refer [midi-in midi-sources midi-handle-events]]))

(def default-track
  "Track live MIDI input plays on. Stamping every action with a real track name
   is what lets keyboard voices steal, gate off, and get released independently
   of the scheduled tracks; without it they key under nil and release-track,
   drop-track, and remove-track's pending scrub can never find them."
  :midi)

(defn midi->action
  "Converts an overtone.midi event map into a scheduler action on TRACK, or nil
  to ignore. Same shape the scheduler emits: an :on carries
  :controls {:note int :gain double}, an :off carries just :note."
  [track {:keys [note velocity command]}]
  (let [now (System/nanoTime)]
    (cond
      (and (= command :note-on) (pos? velocity))
      {:time-nanos now :type :on :track track
       :controls {:note note :gain (/ velocity 127.0)}}

      (or (= command :note-off)
          (and (= command :note-on) (zero? velocity)))
      {:time-nanos now :type :off :track track :note note}

      :else nil)))

(defn list-inputs
  "Connected MIDI input devices, as [{:name :description}]. Use a :name
   substring with start! to target one."
  []
  (mapv #(select-keys % [:name :description]) (midi-sources)))

;; lifecycle

(defonce ^:private midi-state
         (atom {:device nil
                :chan   nil}))

(declare stop!)

(defn start!
  "Open a MIDI input device and start the router. DEV is a name substring
   (case-insensitive regex), matched against (list-inputs). With no argument
   it picks the FIRST source by name; it never pops the Swing chooser that
   bare (midi-in) would. Calls (a/open!) so scsynth is connected first. Notes
   play on TRACK, which defaults to `default-track`."
  ([] (start! (-> (midi-sources) first :name) default-track))
  ([dev] (start! dev default-track))
  ([dev track]
   (stop!)
   (a/open!)
   (let [device (midi-in dev)          ; dev is always a name string here
         ch     (chan 64)]
     (midi-handle-events device (fn [event] (put! ch event)))
     (reset! midi-state {:device device :chan ch})
     (go-loop []
              (when-let [ev (<! ch)]
                (when-let [action (midi->action track ev)]
                  (swap! sched/scheduler-state update :pending conj action))
                (recur)))
     :started)))

(defn stop!
  "Close the MIDI channel. Go-loop exits on its next take"
  []
  (when-let [ch (:chan @midi-state)]
    (close! ch))
  (reset! midi-state {:device nil :chan nil})
  :stopped)