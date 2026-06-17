(ns cantor.grid
  (:require [cantor.core.types  :as t]
            [cantor.core.stream :as s]))

(def empty-grid
  "16 step boolean grid. initialized off"
  (vec (repeat 16 false)))

(defn toggle-pad [grid step]
  (update grid step not))

(defn grid->stream
  "returns a 16-beat periodic stream. note-on each step
   pitch is the midi pitch to fire; steps with false are silence"
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
