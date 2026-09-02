(ns semigroove.harmony.voicing-test
  (:require [clojure.test :refer [deftest is]]
            [semigroove.core.stream :as s]
            [semigroove.core.types :as t]
            [semigroove.harmony :as h]
            [semigroove.harmony.voicing :as v]))

;; Inversions and drops

(deftest inversions-rotate-bottom-up
  (is (= [[60 64 67 71] [64 67 71 72] [67 71 72 76] [71 72 76 79]]
         (v/inversions [60 64 67 71]))))

(deftest inversions-count-matches-arity
  (is (= 4 (count (v/inversions [60 64 67 71]))))
  (is (= 3 (count (v/inversions [60 64 67])))))

(deftest close-is-identity
  (is (= [60 64 67 71] (v/apply-drop :close [60 64 67 71]))))

(deftest drop2-lowers-second-from-top
  (is (= [55 60 64 71] (v/apply-drop :drop2 [60 64 67 71]))
      "67 (second from top) drops an octave to 55, re-sorted"))

(deftest drop3-lowers-third-from-top
  (is (= [52 60 67 71] (v/apply-drop :drop3 [60 64 67 71]))
      "64 (third from top) drops an octave to 52, re-sorted"))

;; Range, cost, voice leading

(deftest in-range-checks-every-voice
  (is (v/in-range? {:low 48 :high 84} [60 64 67 71]))
  (is (not (v/in-range? {:low 48 :high 70} [60 64 67 71]))))

(deftest cost-is-sum-of-absolute-motion
  (is (= 0 (v/voice-leading-cost [60 64 67] [60 64 67])))
  (is (= 3 (v/voice-leading-cost [60 64 67] [60 64 70])))
  (is (= Long/MAX_VALUE (v/voice-leading-cost [60 64] [60 64 67]))
      "size mismatch returns the sentinel"))

(deftest voice-lead-stays-in-range-and-minimizes-motion
  (let [range  {:low 48 :high 84}
        chords [{:root 62 :quality :minor7}     ; Dm7
                {:root 67 :quality :dominant7}   ; G7
                {:root 60 :quality :major7}]     ; Cmaj7
        voiced (v/voice-lead range chords)
        naive  (mapv h/chord-tones chords)
        total  (fn [vs] (reduce + (map v/voice-leading-cost vs (rest vs))))]
    (is (= 3 (count voiced)))
    (is (every? #(v/in-range? range %) voiced))
    (is (<= (total voiced) (total naive))
        "voice-led total motion is no worse than naive close position")))

;; Stream integration

(deftest voicing-stream-blocks-all-tones
  (let [stream (v/voicing->stream [60 64 67 71] 4)
        events (s/query stream (t/arc 0 4))]
    (is (= 4 (count events)))
    (is (= #{60 64 67 71} (set (map :value events))))
    (is (every? zero? (map #(-> % :part :start) events))
        "every voice starts on beat 0 of the cycle")))

(deftest empty-progression-is-empty
  (is (= [] (v/voice-lead v/default-range []))))
