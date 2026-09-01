(ns semigroove.harmony-test
  (:require [clojure.test :refer [deftest is testing]]
            [semigroove.harmony      :as h]
            [semigroove.core.types   :as t]
            [semigroove.core.stream  :as s]))

; quality interval table

(def expected-intervals
  {:major7          [0 4 7 11]
   :minor7          [0 3 7 10]
   :dominant7       [0 4 7 10]
   :half-diminished [0 3 6 10]
   :diminished7     [0 3 6 9]
   :minor7b5        [0 3 6 10]
   :augmented       [0 4 8]
   :sus4            [0 5 7]
   :sus2            [0 2 7]})

(deftest quality-intervals-match
  (doseq [[q expc] expected-intervals]
    (is (= expc (h/quality-intervals q))
        (str "intervals for " q))))

(deftest unknown-quality-is-nil
  (is (nil? (h/quality-intervals :not-a-chord))))

;; chord tone transpotion

(deftest chord-tones-c-major7
  (is (= [60 64 67 71] (h/chord-tones {:root 60 :quality :major7}))))

(deftest chord-tones-d-minor7
  (is (= [62 65 69 72] (h/chord-tones {:root 62 :quality :minor7}))
      "a different root, just transposed"))

(deftest chord-symbol-builds-map
  (is (= {:root 60 :quality :major7} (h/chord-symbol 60 :major7))))

(deftest root-is-first-tone
  (doseq [q (keys h/qualities)]
    (is (= 60 (first (h/chord-tones {:root 60 :quality q})))
        (str "root present at the lowest tone for " q))))

(deftest tone-count-matches-intervals
  (doseq [q (keys h/qualities)]
    (is (= (count (h/quality-intervals q))
           (count (h/chord-tones {:root 60 :quality q}))))))

;; scale tones

(deftest scale-tones-c-major
  (is (= [60 62 64 65 67 69 71] (h/scale-tones 60 :major))))

(deftest scale-tones-named-lookup
  (is (= [60 62 63 65 67 69 70] (h/scale-tones 60 :dorian))))

(deftest scale-tones-raw-vector
  (is (= [60 62 64 67 69] (h/scale-tones 60 [0 2 4 7 9]))
      "a raw interval vector bypasses the named table"))

;; stream integration

(deftest arp-stream-fires-one-tone-per-beat
  (let [stream (h/arp-stream {:root 60 :quality :major7} 1)
        events (s/query stream (t/arc 0 4))]
    (is (= 4 (count events)))
    (is (= [60 64 67 71] (mapv :value (sort-by #(-> % :part :start) events)))
        "arpegiated on beats 0 1 2 3")))

(deftest chord-stream-fires-all-tones-together
  (let [stream (h/chord-stream {:root 60 :quality :major7} 4)
        events (s/query stream (t/arc 0 4))]
    (is (= 4 (count events)))
    (is (= #{60 64 67 71} (set (map :value events))))
    (is (every? zero? (map #(-> % :part :start) events))
        "every tone starts on beat 0 of the cycle")))
