(ns semigroove.hardware.midi
  (:require [semigroove.audio :as a]
            [semigroove.audio.scheduler :as sched]
            [clojure.core.async :refer [chan put! go-loop <! close!]]
            [overtone.midi :refer [midi-in midi-sources midi-handle-events]]))

(defn midi->action
  "Converts overtone.midi event map to a scheduler action map or nil to ignore.
  Returns {:time-nanos long :type :on/:off :pitch int :vel double}"
  [{:keys [note velocity command]}]
  (let [now (System/nanoTime)]
    (cond
      (and (= command :note-on) (pos? velocity))
      {:time-nanos now :type :on :pitch note :vel (/ velocity 127.0)}

      (or (= command :note-off)
          (and (= command :note-on) (zero? velocity)))
      {:time-nanos now :type :off :pitch note}

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
   it picks the FIRST source by name — it never pops the Swing chooser that
   bare (midi-in) would. Calls (a/open!) so scsynth is connected first."
  ([] (start! (-> (midi-sources) first :name)))
  ([dev]
   (stop!)
   (a/open!)
   (let [device (midi-in dev)          ; dev is always a name string here
         ch     (chan 64)]
     (midi-handle-events device (fn [event] (put! ch event)))
     (reset! midi-state {:device device :chan ch})
     (go-loop []
              (when-let [ev (<! ch)]
                (when-let [action (midi->action ev)]
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