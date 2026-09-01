(ns semigroove.generative.cellular-test
  (:require [clojure.test :refer [deftest is]]
            [semigroove.generative.cellular :as ca]
            [semigroove.core.types  :as t]
            [semigroove.core.stream :as s]))
;; Rule Table

(deftest rule90-is-xor-of-neighbors
  ;; rule 90: next = left XOR right (center ignored)
  (is (true?  (ca/apply-rule ca/rule90 true  false false)))
  (is (true?  (ca/apply-rule ca/rule90 false false true)))
  (is (false? (ca/apply-rule ca/rule90 true  false true)))
  (is (false? (ca/apply-rule ca/rule90 false false false)))
  (is (false? (ca/apply-rule ca/rule90 true  true  true))))

;; seeds

(deftest center-seed-has-one-live-cell
  (is (= [ false false false true false false false] (ca/center-seed 7)))
  (is (= [] (ca/center-seed 0))))

(deftest evolve-preserves-length-and-false-boundaries
  (let [row (ca/evolve ca/rule90 (ca/center-seed 7))]
    (is (= 7 (count row)))
    (is (= [false false true false true false false] row)
        "one Sierpinski step from the center")))

(deftest rule90-builds-sierpinski
  (let [gens (ca/generations ca/rule90 4 (ca/center-seed 7))]
    (is (= [[false false false true  false false false]
            [false false true  false true  false false]
            [false true  false false false true  false]
            [true  false true  false true  false true]]
           gens))))

;; stream integrations

(deftest ca-stream-fires-on-live-cells
  (let [stream (ca/ca-stream ca/rule90 3 7 [60 62 64 65 67 69 71])
        events (s/query stream (t/arc 0 21))]
    (is (= 5 (count events)))
    (is (= #{3 9 11 15 19} (set (map #(-> % :part :start) events)))
        "beats = row-idx*7 + column of each live cell")))

(deftest ca-rhythm-hits-middle-c
  (let [events (s/query (ca/ca-rhythm ca/rule90 7) (t/arc 0 7))]
    (is (= 2 (count events)))
    (is (every? #(= 60 (:value %)) events))))

(deftest empty-pitches-is-silence
  (is (empty? (s/query (ca/ca-stream ca/rule90 3 7 []) (t/arc 0 21)))))

(deftest ca-sequence-is-one-stream-per-generation
  ;; 4 gens of width 7: live-cell counts are 1, 2, 2, 4 (Sierpinski)
  (let [streams (ca/ca-sequence ca/rule90 4 7)]
    (is (= 4 (count streams)))
    (is (= [1 2 2 4]
           (mapv #(count (s/query % (t/arc 0 7))) streams)))
    (is (every? #(= 60 (:value %))
                (s/query (first streams) (t/arc 0 7)))
        "every live cell fires middle-C (60)")))

(deftest column-density-counts-live-cells
  (is (= [1 1 2 1 2 1 1]
         (ca/column-density (ca/generations ca/rule90 4 (ca/center-seed 7))))))
