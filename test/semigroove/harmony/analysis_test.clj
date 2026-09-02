(ns semigroove.harmony.analysis-test
  (:require [clojure.test :refer [deftest is]]
            [semigroove.harmony.analysis :as a]
            [semigroove.harmony :as h]))

;; Interval classification

(deftest classify-interval-table
  (is (= :major7      (a/classify-intervals 4 7 11)))
  (is (= :minor7      (a/classify-intervals 3 7 10)))
  (is (= :dominant7   (a/classify-intervals 4 7 10)))
  (is (= :minor7b5    (a/classify-intervals 3 6 10)))
  (is (= :diminished7 (a/classify-intervals 3 6 9)))
  (is (= :augmented   (a/classify-intervals 4 8 11)))
  (is (= :sus4        (a/classify-intervals 5 7 10)))
  (is (= :sus2        (a/classify-intervals 2 7 10)))
  (is (= :unknown     (a/classify-intervals 1 1 1)) "unknown triple falls back to :unknown"))

;; Pitch classes and suggestion for a scale

(deftest chord-pcs-are-root-relative
  (is (= [0 4 7 11] (a/chord-pcs {:root 60 :quality :major7})))
  (is (= [0 4 7 11] (a/chord-pcs {:root 67 :quality :major7}))
      "pitch classes are transposition-invariant"))

(deftest scales-for-minor7-include-dorian-and-aeolian
  (let [names (set (map :name (a/scales-for-chord {:root 60 :quality :minor7})))]
    (is (contains? names "Dorian"))
    (is (contains? names "Aeolian"))))

(deftest scales-for-major7-include-ionian-and-lydian
  (let [names (set (map :name (a/scales-for-chord {:root 60 :quality :major7})))]
    (is (contains? names "Ionian"))
    (is (contains? names "Lydian"))))

;; Harmonization (it that even a word?)

(deftest harmonize-c-major-is-the-diatonic-row
  (is (= [{:root 60 :quality :major7}
          {:root 62 :quality :minor7}
          {:root 64 :quality :minor7}
          {:root 65 :quality :major7}
          {:root 67 :quality :dominant7}
          {:root 69 :quality :minor7}
          {:root 71 :quality :minor7b5}]
         (a/chords-from-scale (:major h/scales) 60))))

(deftest harmonize-needs-a-heptatonic-scale
  (is (= [] (a/chords-from-scale (:major-pentatonic h/scales) 60))
      "fewer than seven notes can't stack diatonic thirds"))
