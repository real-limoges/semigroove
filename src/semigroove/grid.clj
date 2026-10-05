(ns semigroove.grid
  (:require [semigroove.core.types  :as t]
            [semigroove.core.stream :as s]
            [semigroove.harmony     :as h]))

;; input

(defn classify-press
  "Turns raw overtone.midi events into {:edge :press|:release|:ignore}"
  [{:keys [command velocity]}]
  (cond
    (and (#{:note-on :control-change} command) (pos? velocity)) {:edge :press}
    (or (= command :note-off)
        (and (#{:note-on :control-change} command) (zero? velocity))) {:edge :release}
    :else {:edge :ignore}))

;; modes

(def modes [:sequencer :instrument :scene])

(defn set-mode [state mode]
  {:pre [(some #{mode} modes)]}
  (assoc state :mode mode))

(defn press-mode-button
  "State after pressing the button for MODE: switches mode unless locked."
  [state mode]
  (if (:locked state) state (set-mode state mode)))

(defn next-mode
  "Cycle to next mode because I'm lazy"
  [state]
  (let [i (.indexOf modes (:mode state))]
    (assoc state :mode (nth modes (mod (inc i) (count modes))))))

;; sequencer

(def empty-grid
  "A 16-step boolean grid, every step off. The sequencer's blank slate."
  (vec (repeat 16 false)))

(defn toggle-pad
  "Flip one step on or off."
  [grid step]
  (update grid step not))

(defn grid->stream
  "A 16-beat periodic stream that fires PITCH on every step that's on; off steps
   are just silence. An all-off grid returns silence rather than an empty loop."
  [grid pitch]
  (let [events (->> (map-indexed
                     (fn [i active?]
                       (when active?
                         (t/event (t/arc i (inc i)) pitch)))
                     grid)
                    (filter some?)
                    vec)]
    (if (seq events)
      (s/periodic 16 events)
      (s/silence))))

(defn step-at
  "The sequencer step sounding at BEAT in a loop of STEPS one-beat steps"
  [beat steps]
  (mod (long (Math/floor (double beat))) steps))

;; instrument

(defn pad->degree
  "Scale degree of a pad. Row 0 is the bottom row. Columns are steps up."
  ([row col] (pad->degree row col {}))
  ([row col {:keys [row-step] :or {row-step 5}}]
   (+ (* row-step row) col)))

(defn pad->pitch
  "Defaults to chromatic fourths from C2 (column + 1 semitone, row + 5)"
  ([row col] (pad->pitch row col {}))
  ([row col {:keys [base scale] :or {base 36 scale :chromatic} :as layout}]
   (h/scale-pitch base (h/scales scale) (pad->degree row col layout))))

(defn admit-note
  "Admit NOTE to HELD (sounding pitches, oldest first) under a CAP of voices.
   Returns [held' evicted], where evicted is the pitch to release or nil. A
   repeat press of a held pitch moves it to newest."
  [held note cap]
  (let [held' (conj (filterv #(not= note %) held) note)]
    (if (> (count held') cap)
      [(subvec held' 1) (first held')]
      [held' nil])))

(defn release-note
  "HELD without NOTE"
  [held note]
  (filterv #(not= note %) held))

(def rainbow
  "Idle pad colors in scale-degree order, so the root is always red."
  [:red :orange :yellow :green :cyan :blue :purple])

(defn degree-color
  "Rainbow color for a scale DEGREE under LAYOUT. Octaves share a color."
  [{:keys [scale] :or {scale :chromatic}} degree]
  (let [n (count (h/scales scale))]
    (nth rainbow (mod (Math/floorMod (long degree) (long n)) (count rainbow)))))

(defn field-color
  "Idle color of an instrument pad: rainbow by degree under the :rainbow
   palette, plain :field otherwise."
  [{:keys [palette layout]} row col]
  (if (= palette :rainbow)
    (degree-color layout (pad->degree row col layout))
    :field))

;; scenes

(defn rc->pad [row col] (+ (* row 8) col))
(defn pad->rc [pad] [(quot pad 8) (rem pad 8)])

(def empty-scenes
  {:clips {} :active nil})

(defn put-scene [bank pad stream] (assoc-in bank [:clips pad] stream))
(defn scene-at  [bank pad]         (get-in bank [:clips pad]))

(defn launch
  "Mark PAD as the active scene if it holds a clip. Pure; binding does the
   actual launching."
  [bank pad]
  (if (scene-at bank pad) (assoc bank :active pad) bank))

(defn stop-scenes
  [bank]
  (assoc bank :active nil))

(defn active? [bank pad] (= pad (:active bank)))

(defn slot-color
  [bank pad]
  (cond
    (active? bank pad)  :on
    (scene-at bank pad) :dim
    :else               :off))
