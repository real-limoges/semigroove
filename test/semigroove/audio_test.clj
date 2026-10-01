(ns semigroove.audio-test
  (:require [clojure.test :refer [deftest is]]
            [overtone.core]
            [semigroove.audio :as a]
            [semigroove.synthdefs]))

(deftest velocity->amp-clamps
  (is (= 0.5 (a/velocity->amp 0.5)))
  (is (= 1.0 (a/velocity->amp 1.7)) "clamps above 1")
  (is (= 0.0 (a/velocity->amp -0.3)) "clamps below 0"))

(deftest controls->synth-args-fills-defaults
  (let [args (apply hash-map (a/controls->synth-args {:note 69 :cutoff 500}))]
    (is (== 440.0 (:freq args)))
    (is (== 500 (:cutoff args)))
    (is (== 0.8 (:amp args)))
    (is (== 0.0 (:pan args)))))

(deftest controls->synth-args-clamps-gain
  (is (== 1.0 (:amp (apply hash-map (a/controls->synth-args {:note 60 :gain 3}))))))

;; Synth calls are stubbed (a node is just a counter), so this checks the voice
;; bookkeeping without a running scsynth.
(deftest note-on-steals-within-a-track-only
  (let [voices @#'a/voices
        nodes  (atom 0)
        killed (atom [])]
    (reset! voices {})
    (with-redefs [semigroove.synthdefs/semigroove-note (fn [& _] (swap! nodes inc))
                  overtone.core/kill                   (fn [n] (swap! killed conj n))]
      (a/note-on :a {:note 60})
      (a/note-on :b {:note 60})
      (is (empty? @killed) "same note on another track does not steal")
      (a/note-on :a {:note 60 :gain 0.5})
      (is (= [1] @killed) "same note on the same track steals the old node")
      (is (= {:a {60 3} :b {60 2}} @voices)))
    (reset! voices {})))
