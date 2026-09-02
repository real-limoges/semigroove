(ns semigroove.harmony.voicing
  (:require
   [semigroove.harmony :as h]
   [semigroove.core.stream :as s]
   [semigroove.core.types :as t]))

(def drop-types
  "The voicing shapes I generate: close, plus drop-2 and drop-3."
  [:close :drop2 :drop3])

(defn inversions
  "Every inversion of a set of pitches, each one lifting the bottom voice up an
   octave until we land back where we started."
  [pitches]
  (let [invert (fn [[p & more]] (vec (concat more [(+ p 12)])))]
    (vec (take (count pitches) (iterate invert (vec pitches))))))

(defn drop-nth-from-top
  "Drop the nth voice from the top down an octave. An out-of-range n is a no-op
   rather than an error, since all-voicings sweeps the shapes blindly."
  [n pitches]
  (let [sorted (vec (sort pitches))
        cnt    (count sorted)]
    (if (or (<= n 0) (> n cnt))
      pitches
      (let [idx (- cnt n)]
        (vec (sort (assoc sorted idx (- (sorted idx) 12))))))))

(defn apply-drop
  "Turn a drop-type keyword into the voicing it names."
  [drop-type pitches]
  (case drop-type
    :close pitches
    :drop2 (drop-nth-from-top 2 pitches)
    :drop3 (drop-nth-from-top 3 pitches)))

(def default-range
  "The pitch window I voice into by default: C2 up to C6."
  {:low 36 :high 84})

(defn in-range?
  "True when every voice sits inside the range."
  [{:keys [low high]} voicing]
  (every? #(<= low % high) voicing))

(defn all-voicings
  "Every in-range candidate voicing of a chord: each inversion, each drop shape,
   across a few octaves. Deliberately over-generates and lets the caller pick."
  [range chord]
  (let [tones (h/chord-tones chord)]
    (for [inv       (inversions tones)
          shape     drop-types
          oct       [-2 -1 0 1 2]
          :let      [candidate (mapv #(+ % (* 12 oct)) (apply-drop shape inv))]
          :when     (in-range? range candidate)]
      candidate)))

(defn voice-leading-cost
  "Total semitones the voices have to move from a to b. Mismatched voice counts
   cost Long/MAX_VALUE so min-key never picks them."
  [a b]
  (if (not= (count a) (count b))
    Long/MAX_VALUE
    (reduce + (map (fn [x y] (Math/abs (long (- x y))))
                   (sort a) (sort b)))))

(defn best-voicing
  "The candidate voicing of target that moves least from previous. Falls back to
   the plain chord tones when nothing lands in range."
  [range previous target]
  (let [candidates (all-voicings range target)]
    (if (seq candidates)
      (apply min-key #(voice-leading-cost previous %) candidates)
      (h/chord-tones target))))

(defn voice-lead
  "Voice a whole progression, each chord picked to move least from the one
   before. Seeds on the first chord's lowest in-range voicing."
  [range chords]
  (if (empty? chords)
    []
    (let [seed (or (first (all-voicings range (first chords)))
                   (h/chord-tones (first chords)))]
      (vec (reductions (partial best-voicing range) seed (rest chords))))))

(defn voicing->stream
  "Sound a single voicing for period beats: one looping note per voice, stacked."
  [voicing period]
  (if (seq voicing)
    (s/stack (mapv (fn [p] (s/periodic period [(t/event (t/arc 0 period) p)]))
                   voicing))
    (s/silence)))

(defn progression->stream
  "Voice-lead a progression and play it back, one chord per period beats."
  [range chords period]
  (let [voicings (voice-lead range chords)]
    (if (seq voicings)
      (s/cat (mapv #(voicing->stream % period) voicings) period)
      (s/silence))))
