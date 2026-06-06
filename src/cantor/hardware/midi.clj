(ns cantor.hardware.midi
  (:require [cantor.audio :as a]
            [cantor.audio.scheduler :as sched]
            [clojure.core.async :refer [chan put! go-loop <! close!]]
            [overtone.midi :refer [midi-in midi-handle-events]]))

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

;; lifecycle

(defonce ^:private midi-state
         (atom {:device nil
                :chan   nil}))

(defn start!
  "Opens a MIDI device and start the router.
   Calls (a/open!) to ensure scsynth is connected first.
   Without argument, it opens the first available device"
  ([]     (start! nil))
  ([dev]
   (stop!)
   (a/open!)
   (let [device (if dev (midi-in dev) (midi-in))
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