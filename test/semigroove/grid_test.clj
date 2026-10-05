(ns semigroove.grid-test
  (:require [clojure.test :refer :all]
            [semigroove.grid                  :as g]
            [semigroove.core.types            :as t]
            [semigroove.core.stream           :as s]
            [semigroove.harmony               :as h]
            [semigroove.generative.euclidean  :as e]
            [semigroove.generative.markov     :as m]
            [semigroove.generative.cellular   :as ca]))

(deftest grid->stream-fires-active-steps
  (let [grid    (-> g/empty-grid
                    (g/toggle-pad 0)
                    (g/toggle-pad 3)
                    (g/toggle-pad 7))
        stream  (g/grid->stream grid 60)
        events  (s/query stream (t/arc 0 16))]
    (is (= 3 (count events)))
    (is (= #{0 3 7}
           (set (map #(-> % :part :start) events))))))

(deftest all-off-returns-silence
  (let [stream (g/grid->stream g/empty-grid 60)]
    (is (empty? (s/query stream (t/arc 0 16))))))

;; dispatcher and instrument mode

(deftest classify-press-edges
  (is (= :press   (:edge (g/classify-press {:command :note-on        :velocity 127}))))
  (is (= :release (:edge (g/classify-press {:command :note-off       :velocity 0}))))
  (is (= :press   (:edge (g/classify-press {:command :control-change :velocity 127}))))
  (is (= :release (:edge (g/classify-press {:command :control-change :velocity 0}))))
  (is (= :ignore  (:edge (g/classify-press {:command :pitch-bend     :velocity 3})))))

(deftest pad->pitch-isomorphic
  (testing "column adds a semitone, row adds a fourth"
    (is (= 36 (g/pad->pitch 0 0)))
    (is (= 37 (g/pad->pitch 0 1)))
    (is (= 41 (g/pad->pitch 1 0)))
    (is (= 53 (g/pad->pitch 3 2))))
  (testing "the same chord shape transposes anywhere"
    (let [shape (fn [r c] [(g/pad->pitch r c) (g/pad->pitch r (+ c 2)) (g/pad->pitch (inc r) c)])]
      (is (= (map #(+ 5 %) (shape 0 0)) (shape 1 0))))))

(deftest pad->pitch-in-key
  (let [layout  {:base 48 :scale :major-pentatonic :row-step 2}
        pitches (for [r (range 8) c (range 8)] (g/pad->pitch r c layout))]
    (is (every? #{0 2 4 7 9} (map #(mod % 12) pitches)))))

(deftest mode-transitions
  (is (= :instrument (:mode (g/set-mode {:mode :sequencer} :instrument))))
  (is (thrown? AssertionError (g/set-mode {:mode :sequencer} :bogus)))
  (is (= :instrument (:mode (g/next-mode {:mode :sequencer}))))
  (is (= :sequencer  (:mode (g/next-mode {:mode :scene})))))

(deftest step-at-wraps
  (is (= 0 (g/step-at 0 16)))
  (is (= 3 (g/step-at 7/2 16)))
  (is (= 0 (g/step-at 16 16))))

;; the scene bank

(deftest scene-bank-put-and-lookup
  (let [s1   (constantly :clip1)
        s2   (constantly :clip2)
        bank (-> g/empty-scenes (g/put-scene 0 s1) (g/put-scene 5 s2))]
    (is (= s1 (g/scene-at bank 0)))
    (is (= s2 (g/scene-at bank 5)))
    (is (nil? (g/scene-at bank 9)))))

(deftest launch-only-fills-active-for-bound-pads
  (let [bank (g/put-scene g/empty-scenes 3 (constantly :clip))]
    (is (= 3 (:active (g/launch bank 3))))
    (testing "launching an empty pad is a no-op"
      (is (nil? (:active (g/launch bank 7))))
      (is (= 3 (:active (-> bank (g/launch 3) (g/launch 7))))))
    (testing "launching another bound pad moves the active pad"
      (let [bank2 (g/put-scene bank 4 (constantly :other))]
        (is (= 4 (:active (-> bank2 (g/launch 3) (g/launch 4)))))))
    (testing "stop clears the active pad and keeps the clips"
      (let [stopped (g/stop-scenes (g/launch bank 3))]
        (is (nil? (:active stopped)))
        (is (some? (g/scene-at stopped 3)))))))

(deftest slot-color-mapping
  (let [bank (-> g/empty-scenes (g/put-scene 1 (constantly :x)) (g/launch 1))]
    (is (= :on  (g/slot-color bank 1)))
    (is (= :off (g/slot-color bank 2))))
  (is (= :dim (g/slot-color (g/put-scene g/empty-scenes 4 (constantly :y)) 4))))

(deftest pad-index-roundtrip
  (doseq [pad (range 64)]
    (is (= pad (apply g/rc->pad (g/pad->rc pad)))))
  (testing "pad 0 is bottom-left, row-major"
    (is (= [0 0] (g/pad->rc 0)))
    (is (= [1 2] (g/pad->rc 10)))
    (is (= 63 (g/rc->pad 7 7)))))

(deftest example-bank-clips-are-playable
  ;; a bank built from the real generators
  (let [clips  {0 (e/euclid-stream 60 3 8)
                1 (h/arp-stream {:root 60 :quality :major7} 1)
                2 (h/chord-stream {:root 65 :quality :minor7} 4)
                3 (m/markov-stream m/jazz-blues-chain m/i7 8 4 (java.util.Random. 42))
                4 (ca/ca-stream ca/rule90 8 8 [60 62 64 65 67 69 71 72])}
        bank   (reduce-kv g/put-scene g/empty-scenes clips)]
    (doseq [pad (keys clips)]
      (let [launched (g/launch bank pad)
            clip     (g/scene-at launched (:active launched))]
        (is (= pad (:active launched)))
        (is (seq (s/query clip (t/arc 0 8))) (str "pad " pad " sounds"))))))

;; toddler mode

(def ^:private toddler-layout {:base 48 :scale :major-pentatonic :row-step 1})

(deftest toddler-layout-in-key-and-in-range
  (let [pitches (for [r (range 8) c (range 8)] (g/pad->pitch r c toddler-layout))]
    (is (every? #{0 2 4 7 9} (map #(mod % 12) pitches)))
    (is (= 48 (apply min pitches)))
    (is (= 81 (apply max pitches)))))

(deftest rainbow-colors
  (let [st {:palette :rainbow :layout toddler-layout}]
    (is (= :red    (g/field-color st 0 0)) "the root is red")
    (is (= :orange (g/field-color st 0 1)))
    (is (= :red    (g/field-color st 0 5)) "an octave up keeps its color")
    (is (= :orange (g/field-color st 1 0)) "row-step 1: one row up is one degree up"))
  (is (= :field (g/field-color {} 0 0)) "no palette means the plain field"))

(deftest polyphony-cap
  (is (= [[60 62] nil]   (g/admit-note [60] 62 3)))
  (is (= [[62 64 67] 60] (g/admit-note [60 62 64] 67 3)) "the oldest note is evicted")
  (is (= [[62 60] nil]   (g/admit-note [60 62] 60 3)) "a repeat press becomes newest")
  (is (= [62]            (g/release-note [60 62] 60))))

(deftest lock-ignores-mode-buttons
  (is (= :instrument (:mode (g/press-mode-button {:mode :instrument :locked true} :sequencer))))
  (is (= :sequencer  (:mode (g/press-mode-button {:mode :instrument} :sequencer)))))
