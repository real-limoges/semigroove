(ns semigroove.core.transform-test
  (:require [clojure.test :refer [deftest is testing]]
            [semigroove.core.transform :as x]
            [semigroove.core.stream :as s]
            [semigroove.core.types  :as t]))

(defn- vals-at [stream a b]
  (->> (s/query stream (t/arc a b))
       (sort-by #(-> % :part :start))
       (map (juxt #(-> % :part :start) :value))))

(def two-note (s/periodic 2 (t/notes [60 62])))   ;; 60@0, 62@1, looping every 2 beats

(deftest rev-reflects-within-the-cycle
  (is (= [[0 62] [1 60]] (vals-at (x/rev 2 two-note) 0 2))))   ;; values untouched (still bare ints)

(deftest rot-rotates-values-not-onsets
  (is (= [[0 62] [1 60]] (vals-at (x/rot 2 1 two-note) 0 2))))

(deftest setters-coerce-bare-ints
  (is (= [[0 {:note 60 :cutoff 800}] [1 {:note 62 :cutoff 800}]]
         (vals-at (x/cutoff 800 two-note) 0 2))))

(deftest add-updates-note-through-a-map
  (is (= [[0 {:note 72}] [1 {:note 74}]] (vals-at (x/add 12 two-note) 0 2)))
  (testing "add composes with other controls"
    (is (= [[0 {:note 67 :gain 0.3}]] (vals-at (x/add 7 (x/gain 0.3 (s/periodic 1 (t/notes [60])))) 0 1)))))

(deftest degrade-is-reproducible-and-bounded
  (let [d (x/degrade-by 0.5 7 two-note)]
    (is (= (vals-at d 0 8) (vals-at d 0 8)))                            ;; same answer every query
    (is (empty? (s/query (x/degrade-by 1.0 7 two-note) (t/arc 0 8))))   ;; p=1 drops all
    (is (= 4 (count (s/query (x/degrade-by 0.0 7 two-note) (t/arc 0 4))))))) ;; p=0 keeps all (2 cyc)

(deftest sometimes-partitions-no-loss
  (let [s* (x/sometimes-by 0.5 7 (x/add 12) two-note)]
    (is (= 4 (count (s/query s* (t/arc 0 4)))))))   ;; 2 events/cycle * 2 cycles, none lost

(deftest every-applies-f-on-cycle-0-only
  (is (= [[0 {:note 160}] [1 {:note 162}]] (vals-at (x/every 2 2 #(x/add 100 %) two-note) 0 2)))   ;; cyc 0 +100
  (is (= [[2 60] [3 62]]                    (vals-at (x/every 2 2 #(x/add 100 %) two-note) 2 4)))) ;; cyc 1 untouched

(deftest off-layers-a-shifted-copy
  (let [o (x/off 1 (x/add 7) two-note)]
    (is (= 4 (count (s/query o (t/arc 0 2)))))                  ;; original 2 + echo 2
    (is (some #(= [1 {:note 67}] %) (vals-at o 0 3)))))         ;; 60@0 -> shifted to 1, +7

(deftest jux-pans-the-two-copies
  (let [vs (set (map second (vals-at (x/jux (x/add 12) (s/periodic 1 (t/notes [60]))) 0 1)))]
    (is (contains? vs {:note 60 :pan -1}))        ;; dry, hard left
    (is (contains? vs {:note 72 :pan 1}))))       ;; +12, hard right

(def ^:private four-note (s/periodic 4 (t/notes [60 62 64 65])))   ;; one bar at the default cycle
(def ^:private one-note  (s/periodic 1 (t/notes [60])))

(deftest rev-over-an-unaligned-arc
  (is (= [[1 60] [2 62]] (vals-at (x/rev 2 two-note) 1 3))))

(deftest default-cycle-and-with-cycle
  (is (= [[0 65] [1 64] [2 62] [3 60]] (vals-at (x/rev four-note) 0 4)))           ;; *cycle* 4
  (is (= [[0 62] [1 60] [2 65] [3 64]] (vals-at (x/with-cycle 2 (x/rev four-note)) 0 4))))

(deftest iter-walks-the-phrase
  (is (= [[0 60] [1 62] [2 62] [3 60]] (vals-at (x/iter 2 2 two-note) 0 4))))       ;; cycle 1 starts one beat in

(deftest whenmod-applies-on-late-cycles
  (is (= [[4 60] [5 62] [6 {:note 160}] [7 {:note 162}]]
         (vals-at (x/whenmod 2 4 3 (x/add 100) two-note) 4 8))))                  ;; only cycle 3

(deftest palindrome-alternates
  (is (= [[0 60] [1 62] [2 62] [3 60]] (vals-at (x/palindrome 2 two-note) 0 4))))

(deftest superimpose-layers-the-transform
  (is (= #{60 {:note 72}} (set (map second (vals-at (x/superimpose (x/add 12) one-note) 0 1))))))

(deftest setters-stack-on-one-map
  (is (= [[0 {:note 60 :wave 2 :pan -1 :gain 0.5}]]
         (vals-at (x/gain 0.5 (x/pan -1 (x/sound 2 one-note))) 0 1)))
  (is (= (vals-at (x/cutoff 900 one-note) 0 2) (vals-at ((x/cutoff 900) one-note) 0 2))
      "the one-argument arity is the same transform, curried"))

(deftest sometimes-by-extremes
  (is (= (vals-at (x/add 12 two-note) 0 4) (vals-at (x/sometimes-by 1.0 7 (x/add 12) two-note) 0 4)))
  (is (= (vals-at two-note 0 4)            (vals-at (x/sometimes-by 0.0 7 (x/add 12) two-note) 0 4))))

(deftest beats-stay-ratio
  (let [starts (map first (vals-at (x/iter 1 3 one-note) 0 3))]   ;; shifts by thirds of a beat
    (is (some ratio? starts))
    (is (every? #(or (ratio? %) (integer? %)) starts))))

(deftest nested-transforms-agree-when-built-together
  ;; every and the rev it applies both capture *cycle* 2 at construction
  (let [nested   (x/with-cycle 2 (x/every 2 x/rev four-note))
        explicit (x/every 2 2 #(x/rev 2 %) four-note)]
    (is (= [[0 62] [1 60] [2 64] [3 65] [4 62] [5 60]] (vals-at nested 0 6)))
    (is (= (vals-at explicit 0 8) (vals-at nested 0 8)))))
