(ns semigroove.harmony
  (:require [semigroove.core.types  :as t]
            [semigroove.core.stream :as s]))


(def qualities
  "Semitone offsets above the root for each chord quality. :half-diminished
  and :minor7b5 are the same shape; I keep both names because I reach for both."
  {:major7          [0 4 7 11]
   :minor7          [0 3 7 10]
   :dominant7       [0 4 7 10]
   :half-diminished [0 3 6 10]
   :diminished7     [0 3 6 9]
   :minor7b5        [0 3 6 10]
   :augmented       [0 4 8]
   :sus4            [0 5 7]
   :sus2            [0 2 7]})

(defn quality-intervals
  "Interval vector for a chord-quality keyword, or nil if I don't know it.
  (quality-intervals :major7) => [0 4 7 11]"
  [quality]
  (get qualities quality))

(defn chord-symbol
 "Builds a chord symbol map.
  (chord-symbol 60 :major7) => {:root 60 :quality :major7}"
  [root quality]
  {:root root :quality quality})

(defn chord-tones
  "Concrete pitches for a chord symbol, lowest first
  (chord-tones {:root 60 :quality :major7}) => [60 64 67 71]"
  [{:keys [root quality]}]
  (mapv #(+ root %) (quality-intervals quality)))

; scales


(def scales
  "Semitone offsets above the tonic for the usual scales and modes."
  {:major               [0 2 4 5 7 9 11]
   :minor               [0 2 3 5 7 8 10]
   :dorian              [0 2 3 5 7 9 10]
   :phrygian            [0 1 3 5 7 8 10]
   :lydian              [0 2 4 6 7 9 11]
   :mixolydian          [0 2 4 5 7 9 10]
   :locrian             [0 1 3 5 6 8 10]
   :harmonic-minor      [0 2 3 5 7 8 11]
   :melodic-minor       [0 2 3 5 7 9 11]
   :major-pentatonic    [0 2 4 7 9]
   :minor-pentatonic    [0 3 5 7 10]
   :chromatic           [0 1 2 3 4 5 6 7 8 9 10 11]})

(defn scale-tones
  "Concrete pitches for root and scale. SCALE names an entry in `scales`
  or a raw internal vector.
  (scale-tones 60 :major)      => [60 62 64 65 67 69 71]
  (scale-tones 60 [0 2 4 7 9]) => [60 62 64 67 69]"
  [root scale]
  (let [intervals (if (keyword? scale) (get scales scale) scale)]
    (mapv #(+ root %) intervals)))

(defn arp-stream
  "Arpeggiate a chord, one tone per beat, looping every (count tones) beats.
  The beats-per-note arg is ignored for now; the period is fixed at one beat.
  (arp-stream {:root 60 :quality :major7} 1) plays 60 64 67 71 on repeat"
  [chord _beats-per-note]
  (let [tones (chord-tones chord)]
    (if (seq tones)
      (s/periodic (count tones) (t/notes tones))
      (s/silence))))

(defn chord-stream
  "Sound every chord tone at once, sustained for PERIOD beats and looped.
  A block chord rather than an arpeggio."
  [chord period]
  (let [tones (chord-tones chord)]
    (if (seq tones)
      (s/stack
       (mapv (fn [p] (s/periodic period [(t/event (t/arc 0 period) p)]))
             tones))
      (s/silence))))
