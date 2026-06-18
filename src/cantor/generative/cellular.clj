(ns cantor.generative.cellular
  (:require [cantor.core.types  :as t]
            [cantor.core.stream :as s]))

(def rule30  30)   ; craziness
(def rule90  90)   ; Sierpinski (XOR)
(def rule110 110)  ; turing-complete

(defn apply-rule
  "Evaluate a Wolfram rule on a 3-cell neighborhood.
   The bit pattern of RULE is indexed by (l c r) packed as (41+2c+r)
   with 1 as the high bit.
  (apply-rule 90 true false false => true)"
  [rule l c r]
  (let [idx (+ (if l 4 0) (if c 2 0) (if r 1 0))]
    (bit-test rule idx)))

(defn evolve
  "One generation of the CA over ROW with false boundaries."
  [rule row]
  (let [n (count row)]
    (mapv (fn [i]
            (let [l (if (zero? i)       false (nth row (dec i)))
                  c (nth row i)
                  r (if (= i (dec n))   false (nth row (inc i)))]
              (apply-rule rule l c r)))
          (range n))))

(defn generations
  "N successive generations starting from SEED (inclusive of the seed)."
  [rule n seed]
  (vec (take n (iterate (partial evolve rule) seed))))

(defn center-seed
  "A row of N cells with only the middle cell alive. Basically a filter
  (center-seed 7 => [false false false true false false false])"
  [n]
  (if (<= n 0)
    []
    (mapv #(= % (quot n 2)) (range n))))

(defn row->stream
  "Loop ROW as a unit-step stream: each live cell fires PITCH at its index,
   dead cells are rests. Perioid = (count row) beats"
  [pitch row]
  (let [events (->> (map-indexed
                     (fn [i alive]
                       (when alive (t/event (t/arc i (inc i)) pitch)))
                     row)
                    (filter some?)
                    vec)]
    (if (seq events)
      (s/periodic (count row) events)
      (s/silence))))

(defn ca-stream
  "Evolve RULE for ROWS generations from a center seed of COLS cells, then
   concatenate the rows in time. Each live cell fires the pitch at its column
   index, cycling through PITCHES. Perioid = (* rows columns) beats"
  [rule rows cols pitches]
  (if (empty? pitches)
    (s/silence)
    (let [pitch-at (fn [j] (nth pitches (mod j (count pitches))))
          gens (generations rule rows (center-seed cols))
          events (vec
                  (for [[row-idx row] (map-indexed vector gens)
                        [j alive]     (map-indexed vector row)
                        :when alive
                        :let [beat (+ (* row-idx cols) j)]]
                    (t/event (t/arc beat (inc beat)) (pitch-at j))))]
      (if (seq events)
        (s/periodic (* rows cols) events)
        (s/silence)))))

(defn ca-rhythm
  "One evolved row used as a rhythm: middle-C (60) on every live cell."
  [rule cols]
  (row->stream 60 (evolve rule (center-seed cols))))

(defn ca-sequence
  "One stream per generation - useful as scene content or whatnot."
  [rule rows cols]
  (mapv #(row->stream 60 %) (generations rule rows (center-seed cols))))

(defn column-density
  "Live-cell count per column across a list of ROWS (equal length)."
  [rows]
  (if (empty? rows)
    []
    (mapv (fn [j] (count (filter #(nth % j) rows)))
          (range (count (first rows))))))
