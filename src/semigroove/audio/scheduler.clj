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

(def lookahead-nanos
  "How far ahead of the clock step queries and sends. Trades latency for
   immunity to JVM jitter; see architecture.md."
  (* 100 1000000))

(defn nanos->beat
  "The transport beat at wall-clock NANOS: the inverse of beat->nanos. Exact,
   so window edges stay Ratio like every other beat; rationalize lets a
   fractional bpm such as 92.5 stay exact too."
  [start-nanos bpm nanos]
  (/ (* (- nanos start-nanos) (rationalize bpm)) 60000000000))

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

(defn audible?
  "Whether a track should sound right now, decided by mute/solo alone. It does
   not consult :tracks, so it answers for live-input tracks (e.g. MIDI) that own
   no stream just as well as for scheduled ones. Any solo wins outright and mutes
   are ignored; otherwise a track sounds unless it is muted."
  [{:keys [muted solo]} track]
  (if (seq solo)
    (contains? solo track)
    (not (contains? muted track))))

(defn audible-tracks
  "The stream-owning tracks that should actually sound. Any solo wins outright and
   mutes are ignored; otherwise everything plays except the muted set."
  [{:keys [tracks] :as state}]
  (into {} (filter (fn [[t _]] (audible? state t)) tracks)))

(defn- events->actions
  "Turn queried events into timed :on/:off actions. The :on carries the whole
   control map; the :off needs only the note to find the voice."
  [start-n bpm track events]
  (mapcat
   (fn [e]
     (let [c0 (t/controls (:value e))
           ;; An event's :velocity stands in for :gain unless the map sets its own.
           c  (if (and (:velocity e) (not (contains? c0 :gain)))
                (assoc c0 :gain (:velocity e))
                c0)]
       [{:time-nanos (beat->nanos start-n bpm (-> e :whole :start))
         :type :on  :controls c :track track}
        {:time-nanos (beat->nanos start-n bpm (-> e :whole :end))
         :type :off :note (:note c) :track track}]))
   events))

(defn step
  "Pure scheduler tick. Returns [new-state due-actions].
   Queries every audible track over [cursor, beat at now + lookahead), moves
   the cursor to the end of that window, then splits pending into due (within
   the lookahead) and future. Due note-ons on a track that is not audible are
   dropped, so the mixer gates live input too; note-offs always pass, so muting
   mid-note never leaves a voice hanging."
  [state now-nanos]
  (let [bpm          (:tempo state)
        start-n      (:start-nanos state)
        lo           (:beat state)
        ;; The window ends where the clock will be one lookahead from now, so the
        ;; cursor follows wall time; max keeps it from ever moving backward.
        hi           (max lo (nanos->beat start-n bpm (+ now-nanos lookahead-nanos)))
        new-actions  (when (< lo hi)
                       (mapcat
                        (fn [[track stream]]
                          (events->actions start-n bpm track (s/query stream (t/arc lo hi))))
                        (audible-tracks state)))
        all-pending  (sort-by :time-nanos (concat (:pending state) new-actions))
        cutoff       (+ now-nanos lookahead-nanos)
        [due future] (split-with #(<= (:time-nanos %) cutoff) all-pending)
        due          (filterv #(or (not= :on (:type %)) (audible? state (:track %)))
                              due)]
    [(assoc state :beat hi :pending (vec future))
     due]))

(defn retempo
  "Change tempo without moving the present. Re-anchors :start-nanos so the
   beat sounding at NOW-NANOS keeps its wall-clock time and every later beat is
   spaced at the new BPM."
  [state bpm now-nanos]
  (let [{:keys [start-nanos tempo]} state
        beat-now (nanos->beat start-nanos tempo now-nanos)]
    (assoc state
           :tempo bpm
           :start-nanos (- now-nanos (long (* (double beat-now) (/ 60e9 bpm)))))))
