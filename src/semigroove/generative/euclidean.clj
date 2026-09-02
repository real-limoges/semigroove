(ns semigroove.generative.euclidean
  (:require [semigroove.core.types  :as t]
            [semigroove.core.stream :as s]))

(defn euclidean
  "Returns a vector of 0/1s with PULSES onsets distributed as evenly
   as possible over STEPS using the Bjorklund algorithm.
   (euclidean 3 8) => [1 0 0 1 0 0 1 0]"
  [pulses steps]
  (loop [ones  (vec (repeat pulses [1]))
         zeros (vec (repeat (- steps pulses) [0]))]
    
    (cond
      (empty? ones)         (vec (flatten zeros))
      (empty? zeros)        (vec (flatten ones))
      (<= (count zeros) 1)  (vec (flatten (concat ones zeros)))
      (>= (count zeros) (count ones))
      (let [paired (mapv #(into %1 %2) ones zeros)
            remain (subvec zeros (count ones))]
        (recur paired remain))
      :else
        (let [paired (mapv #(into %1 %2) (subvec ones 0 (count zeros)) zeros)
              remain (subvec ones (count zeros))]
          (recur paired remain)))))

(defn euclid-stream
  "A periodic stream with PITCH firing on the Euclidean onsets.
   (euclid-stream 60 3 8) plays a tresillo pattern on C4."
  [pitch pulses steps]
  (let [pattern (euclidean pulses steps)
        events  (->> (map-indexed
                      (fn [i v]
                        (when (= 1 v)
                          (t/event (t/arc i (inc i)) pitch)))
                      pattern)
                     (filter some?)
                     vec)]
    (if (seq events)
      (s/periodic steps events)
      (s/silence))))


(defn rotate
  "Rotate V left by N, wrapping; spins a Euclidean pattern onto a different
   downbeat."
  [n v]
  (vec (take (count v) (drop n (cycle v)))))
