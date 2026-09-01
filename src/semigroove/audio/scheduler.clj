(ns semigroove.audio.scheduler
  (:require [semigroove.core.stream :as s]
            [semigroove.core.types :as t]))

(defonce scheduler-state
  (atom {:tempo        120
         :stream       (s/silence)
         :beat         0
         :start-nanos  0
         :pending      []
         :running      false}))

(defn- beat->nanos
  [start-nanos bpm beat]
  (+ start-nanos (long (* (double beat) (/ 60e9 bpm)))))

(defn step
  "Pure scheduler tick. Returns [new-state due-actions].
   Advances beat cursor by L-beats, queries the stream, splits pending into
   due (within 100ms lookahead) and future."
  [state now-nanos]
  (let [bpm          (:tempo state)
        lo           (:beat state)
        L-beats      (/ bpm 600)
        hi           (+ lo L-beats)
        window       (t/arc lo hi)
        events       (s/query (:stream state) window)
        start-n      (:start-nanos state)
        lookahead-ns (* 100 1000000)
        new-actions  (mapcat
                       (fn [e]
                         [{:time-nanos (beat->nanos start-n bpm (-> e :whole :start))
                           :type       :on
                           :pitch      (:value e)
                           :vel        (get e :velocity 1.0)}
                          {:time-nanos (beat->nanos start-n bpm (-> e :whole :end))
                           :type       :off
                           :pitch      (:value e)}])
                       events)
        all-pending  (sort-by :time-nanos (concat (:pending state) new-actions))
        cutoff       (+ now-nanos lookahead-ns)
        [due future] (split-with #(<= (:time-nanos %) cutoff) all-pending)]
    [(assoc state :beat hi :pending (vec future))
     due]))
