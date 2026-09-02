(ns semigroove.harmony.analysis
  (:require [semigroove.harmony :as h]))

(def jazz-scales
  "The scales I'll match a chord against, each {:name :intervals} with the
   intervals as semitone offsets above the tonic (0 to 11). Not exhaustive on
   purpose; this is the set I actually reach for."
  [{:name "Ionian"              :intervals [0 2 4 5 7 9 11]}
   {:name "Dorian"              :intervals [0 2 3 5 7 9 10]}
   {:name "Phrygian"            :intervals [0 1 3 5 7 8 10]}
   {:name "Lydian"              :intervals [0 2 4 6 7 9 11]}
   {:name "Mixolydian"          :intervals [0 2 4 5 7 9 10]}
   {:name "Aeolian"             :intervals [0 2 3 5 7 8 10]}
   {:name "Locrian"             :intervals [0 1 3 5 6 8 10]}
   {:name "MelodicMinor"        :intervals [0 2 3 5 7 9 11]}
   {:name "LydianDominant"      :intervals [0 2 4 6 7 9 10]}
   {:name "Altered"             :intervals [0 1 3 4 6 8 10]}
   {:name "HalfWholeDiminished" :intervals [0 1 3 4 6 7 9 10]}
   {:name "WholeTone"           :intervals [0 2 4 6 8 10]}
   {:name "BebopDominant"       :intervals [0 2 4 5 7 9 10 11]}
   {:name "BebopMajor"          :intervals [0 2 4 5 7 8 9 11]}])

(defn chord-pcs
  "Pitch classes of a chord, transposed so the root sits at 0 and sorted."
  [{:keys [root] :as chord}]
  (sort (map #(mod (- % root) 12) (h/chord-tones chord))))

(defn scale-pcs
  "Scale intervals folded into pitch classes (0 to 11), sorted."
  [intervals]
  (sort (map #(mod % 12) intervals)))

(defn scales-for-chord
  "Every scale that contains all the chord's tones. Strict: one stray note and
   the scale is out, which is why the loose variant exists."
  [chord]
  (let [chord-set (chord-pcs chord)]
    (filterv (fn [{:keys [intervals]}]
               (let [scale-set (set (scale-pcs intervals))]
                 (every? scale-set chord-set)))
             jazz-scales)))

(defn scales-for-chord-loose
  "Like scales-for-chord, but only insists on the color tones: I drop the root
   and keep the next three. Catches scales the strict match throws away over a
   note nobody's leaning on."
  [chord]
  (let [trimmed (take 3 (drop 1 (chord-pcs chord)))]
    (filterv (fn [{:keys [intervals]}]
               (let [scale-set (set (scale-pcs intervals))]
                 (every? scale-set trimmed)))
             jazz-scales)))

(defn classify-intervals
  "Name a chord quality from its third, fifth, and seventh (semitones above the
   root). Falls through to :unknown rather than guessing."
  [third fifth seventh]
  (cond
    (= [third fifth seventh] [4 7 11]) :major7
    (= [third fifth seventh] [3 7 10]) :minor7
    (= [third fifth seventh] [4 7 10]) :dominant7
    (= [third fifth seventh] [3 6 10]) :minor7b5
    (= [third fifth seventh] [3 6 9])  :diminished7
    (= [third fifth seventh] [4 8 11]) :augmented
    (= [third fifth] [5 7])            :sus4
    (= [third fifth] [2 7])            :sus2
    (= [third fifth] [3 6])            :half-diminished
    :else                              :unknown))

(defn chords-from-scale
  "The diatonic seventh chords built by stacking thirds on each scale degree.
   Bails on anything under seven notes; the stacking assumes a heptatonic scale,
   and I'd rather return nothing than a wrong chord."
  [intervals root]
  (let [n (count intervals)]
    (if (< n 7)
      []
      (vec
       (for [i (range n)
             :let [deg     (intervals i)
                   third   (mod (- (intervals (mod (+ i 2) n)) deg) 12)
                   fifth   (mod (- (intervals (mod (+ i 4) n)) deg) 12)
                   seventh (mod (- (intervals (mod (+ i 6) n)) deg) 12)]]
         {:root (+ root deg)
          :quality (classify-intervals third fifth seventh)})))))
