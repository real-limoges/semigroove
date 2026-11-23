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

(def long-press-nanos
  "How long a function-row button must be held to count as a reset."
  1000000000)

(def toddler-scales
  "The scales a locked grid cycles through on a function-row tap."
  [:major-pentatonic :minor-pentatonic :blues :major])

(defn cycle-scale
  "STATE with its layout moved to the next toddler scale, wrapping at the end.
   A scale outside the list starts the cycle over at the first."
  [state]
  (let [i (.indexOf toddler-scales (get-in state [:layout :scale]))]
    (assoc-in state [:layout :scale]
              (nth toddler-scales (mod (inc i) (count toddler-scales))))))

(defn mode-button-press
  "State after a function-row button goes down at NOW-NANOS. Remembers when, so
   the release can tell a tap from a hold, and switches to MODE when the grid is
   unlocked. MODE is nil for the buttons that name no mode; those only time."
  [state mode now-nanos]
  (cond-> (assoc state :mode-press-time now-nanos)
    (and mode (not (:locked state))) (set-mode mode)))

(defn mode-button-release
  "What releasing a function-row button at NOW-NANOS does, as [state' outcome].
   A hold of long-press-nanos or more is :reset (the binding then releases every
   voice); a tap on a locked grid is :scale-changed; otherwise :nothing, which
   includes a release with no press on record."
  [state now-nanos]
  (let [pressed (:mode-press-time state)
        state'  (dissoc state :mode-press-time)]
    (cond
      (nil? pressed)                               [state' :nothing]
      (>= (- now-nanos pressed) long-press-nanos)  [(assoc state' :held []) :reset]
      (:locked state)                              [(cycle-scale state') :scale-changed]
      :else                                        [state' :nothing])))

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

(defn scale-intervals
  "The intervals of the scale named SCALE in harmony/scales. Throws on a name it
   doesn't know, since every pad and color would otherwise divide by zero."
  [scale]
  (or (h/scales scale)
      (throw (ex-info (str "unknown scale " scale) {:scale scale}))))

(defn pad->pitch
  "Defaults to chromatic fourths from C2 (column + 1 semitone, row + 5)"
  ([row col] (pad->pitch row col {}))
  ([row col {:keys [base scale] :or {base 36 scale :chromatic} :as layout}]
   (h/scale-pitch base (scale-intervals scale) (pad->degree row col layout))))

(defn- silenced
  "The pitches of the REMOVED entries that no entry of HELD still sounds."
  [removed held]
  (let [still (set (map :pitch held))]
    (vec (distinct (remove still (map :pitch removed))))))

(defn press-pad
  "Admit PAD sounding PITCH to HELD (entries {:pad :pitch}, oldest first) under
   a CAP of voices. A repeat press of a held pad moves it to newest. Returns
   {:held held' :silence pitches}: the pitches of evicted entries that no held
   pad still sounds, for the binding to gate off. Keying by pad means a release
   always finds the pitch its press sounded, whatever the layout has become."
  [held pad pitch cap]
  (let [mine?   #(= pad (:pad %))
        joined  (conj (filterv (complement mine?) held) {:pad pad :pitch pitch})
        over    (max 0 (- (count joined) cap))
        held'   (subvec joined over)
        evicted (into (filterv mine? held) (subvec joined 0 over))]
    {:held held' :silence (silenced evicted held')}))

(defn release-pad
  "Release PAD from HELD. Returns {:held held' :silence pitches}: the pitch the
   pad was sounding, unless another held pad sounds it too. A pad that is not
   held (it was evicted) silences nothing."
  [held pad]
  (let [mine? #(= pad (:pad %))
        gone  (filterv mine? held)
        held' (filterv (complement mine?) held)]
    {:held held' :silence (silenced gone held')}))

(def rainbow
  "Idle pad colors in scale-degree order, so the root is always red."
  [:red :orange :yellow :green :cyan :blue :purple])

(defn degree-color
  "Rainbow color for a scale DEGREE under LAYOUT. Octaves share a color."
  [{:keys [scale] :or {scale :chromatic}} degree]
  (let [n (count (scale-intervals scale))]
    (nth rainbow (mod (Math/floorMod (long degree) (long n)) (count rainbow)))))

(defn field-color
  "Idle color of an instrument pad: rainbow by degree under the :rainbow
   palette, plain :field otherwise."
  [{:keys [palette layout]} row col]
  (if (= palette :rainbow)
    (degree-color layout (pad->degree row col layout))
    :field))

;; volume

(def gain-steps
  "The volume column's ladder, quietest first, before any cap."
  [0.1 0.3 0.5 0.8])

(defn gain-ladder
  "The ladder under MAX-GAIN: every step below it, then MAX-GAIN as the top."
  [max-gain]
  (conj (filterv #(< % max-gain) gain-steps) max-gain))

(defn next-gain
  "The ladder step above GAIN, wrapping to the quietest once GAIN is at or past
   the top, which is MAX-GAIN."
  [gain max-gain]
  (let [ladder (gain-ladder max-gain)]
    (or (first (filter #(> % gain) ladder))
        (first ladder))))

(defn press-volume
  "STATE after a volume-column press: the voice's :gain moves one step up its
   ladder under :max-gain. A voice with no :gain starts from silence."
  [{:keys [max-gain] :as state}]
  (update-in state [:voice :gain] #(next-gain (or % 0) max-gain)))

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
