(ns semigroove.grid
  (:require [semigroove.core.types  :as t]
            [semigroove.core.stream :as s]))

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
