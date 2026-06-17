(ns cantor.generative.euclidean-test
  (:require [clojure.test :refer [deftest is testing]]
            [cantor.generative.euclidean :refer [euclidean euclid-stream]]
            [cantor.core.types :as t]
            [cantor.core.stream :as s]))

;; known musical patterns

(deftest tresillo
  (is (= [1 0 0 1 0 0 1 0] (euclidean 3 8))
      "Afro-Cuban tresillo"))

(deftest cinquillo
  (is (= [1 0 1 1 0 1 1 0] (euclidean 5 8))
      "Afro-Cuban cinquillo (one rotation)"))

(deftest single-onset
  (is (= [1 0 0 0] (euclidean 1 4))))

(deftest all-onsets
  (is (= [1 1 1 1] (euclidean 4 4))))


; structural stuff

(deftest pulse-count-preserved
  (doseq [k (range 0 9)
          n (range k 9)]
    (is (= k (apply + (euclidean k n)))
        (str "pulse count must be equal to k for (euclidean " k " " n ")"))))


(deftest length-equals-steps
  (doseq [k (range 0 9)
          n (range k 9)]
    (is (= n (count (euclidean k n)))
        (str "output length must equal n for (euclidean " k " " n ")"))))

; boundaries

(deftest zero-pluses
  (is (= [0 0 0 0] (euclidean 0 4))
      "(euclidean 0 n) is all zeros, not an exception"))

(deftest max-pulses
  (is (= [1 1 1 1 1] (euclidean 5 5))
      "(euclidean n n) is all ones"))


; stream integration

(deftest euclid-stream-fires-on-onset
  (let [stream (euclid-stream 60 3 8)
        events (s/query stream (t/arc 0 8))]
    (is (= 3 (count events)))
    (is (= #{0 3 6} (set (map #(-> % :part :start) events)))
        "tesillo fires on beats 0, 3, 6")))

(deftest euclid-stream-loops
  (let [stream (euclid-stream 60 3 8)
        events (s/query stream (t/arc 0 16))]
    (is (= 6 (count events))
        "two periods of 3-onset pattern = 6 events")))

(deftest zero-pulse-stream-is-silence
  (is (empty? (s/query (euclid-stream 60 0 8) (t/arc 0 8)))))
