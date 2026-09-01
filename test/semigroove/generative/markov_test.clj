(ns semigroove.generative.markov-test
  (:require [clojure.test :refer [deftest is]]
            [semigroove.generative.markov :as m]
            [semigroove.core.types  :as t]
            [semigroove.core.stream :as s]))

;; pure weighted choice (no RNG)

(deftest weighted-choice-picks-the-band
  (let [outs (get m/jazz-blues-chain m/i7)]   ; [[0.6 iv7] [0.3 v7] [0.1 ii7]]
    (is (= m/iv7 (m/weighted-choice outs 0.0)))
    (is (= m/iv7 (m/weighted-choice outs 0.6)))
    (is (= m/v7  (m/weighted-choice outs 0.65)))
    (is (= m/ii7 (m/weighted-choice outs 0.95)))))

(deftest step-on-sink-state-stays-put
  (is (= :sink (m/step {:sink []} :sink (java.util.Random. 0)))
      "A state with no transitions returns itself"))

;; reproducible walk

(deftest walk-with-seed-42-is-reproducible
  (is (= [m/i7 m/v7 m/ii7 m/v7 m/i7 m/v7 m/iv7 m/i7 m/iv7 m/i7 m/v7 m/iv7]
         (take 12 (m/walk m/jazz-blues-chain m/i7 (java.util.Random. 42))))))

(deftest walk-stays-in-the-chain
  (let [states (set (keys m/jazz-blues-chain))]
    (is (every? states
                (take 100 (m/walk m/jazz-blues-chain m/i7 (java.util.Random. 7)))))))

;; stream integration

(deftest markov-stream-renders-the-start-chord-first
  (let [stream (m/markov-stream m/jazz-blues-chain m/i7 4 4 (java.util.Random. 1))
        events (s/query stream (t/arc 0 4))]
    (is (= 4 (count events)))
    (is (= #{60 64 67 70} (set (map :value events)))
        "C7 = 60 64 67 70, all sounding on beat 0")))
