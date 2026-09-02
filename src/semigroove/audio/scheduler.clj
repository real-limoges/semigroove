(ns semigroove.audio.scheduler
  (:require [semigroove.core.stream :as s]
            [semigroove.core.types :as t]))

(defonce scheduler-state
  (atom {:tempo        120
         :tracks       {}
         :muted        #{}
         :solo         #{}
         :beat         0
         :start-nanos  0
         :pending      []
         :running      false}))

(defn- beat->nanos
  "Absolute wall-clock time of a beat, in nanoseconds since the transport start."
  [start-nanos bpm beat]
  (+ start-nanos (long (* (double beat) (/ 60e9 bpm)))))

(defn install-track
  "Bind a stream to a track name, replacing whatever was there."
  [state track stream]
  (assoc-in state [:tracks track] stream))

(defn remove-track
  "Drop a track and scrub every trace of it: its stream, its mute/solo flags, and
   any of its actions still sitting in the pending queue."
  [state track]
  (-> state
      (update :tracks dissoc track)
      (update :muted  disj track)
      (update :solo   disj track)
      (update :pending (fn [p] (filterv #(not= track (:track %)) p)))))

(defn mute-track   "Add a track to the muted set." [state track] (update state :muted (fnil conj #{}) track))
(defn unmute-track "Take a track back out of the muted set." [state track] (update state :muted disj track))
(defn solo-track   "Solo one track; replaces any existing solo rather than adding to it." [state track] (assoc  state :solo #{track}))
(defn unsolo       "Clear solo, so mutes decide again." [state]       (assoc  state :solo #{}))

(defn audible-tracks
  "The tracks that should actually sound. Any solo wins outright and mutes are
   ignored; otherwise everything plays except the muted set."
  [{:keys [tracks muted solo]}]
  (cond
    (seq solo) (select-keys tracks solo)
    :else      (apply assoc tracks muted)))

(defn- events->actions [start-n bpm track events]
  (mapcat
   (fn [e]
     [{:time-nanos (beat->nanos start-n bpm (-> e :whole :start))
       :type :on  :pitch (:value e) :vel (get e :velocity 1.0) :track track}
      {:time-nanos (beat->nanos start-n bpm (-> e :whole :end))
       :type :off :pitch (:value e) :track track}])
   events))

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
                      (fn [[track stream]]
                        (events->actions start-n bpm track (s/query stream window)))
                      (audible-tracks state))
        all-pending  (sort-by :time-nanos (concat (:pending state) new-actions))
        cutoff       (+ now-nanos lookahead-ns)
        [due future] (split-with #(<= (:time-nanos %) cutoff) all-pending)]
    [(assoc state :beat hi :pending (vec future))
     due]))
