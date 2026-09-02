(ns semigroove.audio.scheduler-test
  (:require [clojure.test :refer [deftest is testing]]
            [semigroove.audio.scheduler :refer [step]]
            [semigroove.core.stream :as s]
            [semigroove.core.types :as t]))

(def base-state
  {:tempo         120
   :tracks        {}
   :muted         #{}
   :solo          #{}
   :beat          0
   :start-nanos   0
   :pending       []})

(deftest step-advances-cursor
  (let [[s' _] (step base-state 0)]
    (is (= 1/5 (:beat s')) "120 BPM * 100ms = 1/5 beat")))

(deftest step-cursor-stays-ratio
  (let [[s' _] (step base-state 0)]
    (is (ratio? (:beat s')) "cursor must remain a ratio")))

(deftest step-silence-yields-no-actions
  (let [[_ due] (step base-state 0)]
    (is (empty? due))))

(deftest step-periodic-yields-note-on-and-off
  (let [state    (assoc-in base-state
                           [:tracks :t] (s/periodic 1 (t/notes [60])))
        [s' due] (step state 0)
        all      (concat due (:pending s'))]
    (is (= 2 (count all)) "one note-on + one note-off per event")
    (is (some #(= :on  (:type %)) all))
    (is (some #(= :off (:type %)) all))))

(deftest step-drains-pending-within-lookahead
  (let [now     1000000000  ; 1 second in nanos
        pending [{:time-nanos 900000000 :type :off :pitch 60}   ; past
                 {:time-nanos 1050000000 :type :off :pitch 62}  ; within 100ms
                 {:time-nanos 2000000000 :type :off :pitch 64}] ; far future
        state   (assoc base-state :pending pending)
        [s' due] (step state now)]
    (is (= 2 (count due)))
    (is (= 1 (count (:pending s'))))))

(deftest step-beat-arithmetic-exact
  (testing "multiple steps stay exact"
    (let [steps 12
          [final _] (reduce (fn [[s _] _] (step s 0))
                            [base-state nil]
                            (range steps))]
      (is (= (* 12 1/5) (:beat final)))
      (is (ratio? (:beat final))))))

(deftest step-uses-event-velocity
  (let [stream (s/periodic 1 (t/notes [{:pitch 60 :vel 0.3}]))
        state  (assoc-in base-state [:tracks :t] stream)
        [_ due] (step state 0)
        note-on (first (filter #(= :on (:type %)) due))]
    (is (some? note-on) "expected a note-on action")
    (is (= 0.3 (:vel note-on)) "step must use :velocity ,not 0.5")))