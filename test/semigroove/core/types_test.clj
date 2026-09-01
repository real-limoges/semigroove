(ns semigroove.core.types-test
  (:require [clojure.test :refer [deftest is testing]]
            [semigroove.core.types :as t]))

(deftest arc-construction
  (testing "arcs are construccted properly")
  (let [a (t/arc 0 4)]
    (is (= 0 (:start a)))
    (is (= 4 (:end a)))
    (is (= 4 (t/arc-length a)))))

(deftest shift-and-scale-arc
  (testing "scales and shifts arc properly")
  (is (= {:start 2 :end 6} (t/shift-arc 2 (t/arc 0 4))))
  (is (= {:start 0 :end 8} (t/scale-arc 2 (t/arc 0 4)))))

(deftest notes-builds-events
  (testing "notes build events as expected")
  (let [evs (t/notes [60 62 64])]
    (is (= 3 (count evs)))
    (is (= [60 62 64](mapv :value evs)))
    (is (= [0 1 2] (mapv #(-> % :whole :start) evs)))))

(deftest beats-stay-ratio
  (testing "ratio arithmetic doesn't demote to double")
  (let [a (t/scale-arc 1/3 (t/arc 0 1))]
    (is (= 1/3 (:end a)))
    (is (ratio? (:end a)))))

(deftest event-carries-velocity
  (testing "explicit velocity round-trips"
    (let [e (t/event (t/arc 0 1) 60 0.7)]
      (is (= 0.7 (:velocity e)))))
  (testing "default velocity is 1.0"
    (let [e (t/event (t/arc 0 1) 60)]
      (is (= 1.0 (:velocity e))))))

(deftest notes-map-form
  (testing "pitch+vel map propagates velocity"
    (let [evs (t/notes [{:pitch 60 :vel 0.3} {:pitch 64 :vel 0.8}])]
      (is (= 0.3 (:velocity (first evs))))
      (is (= 0.8 (:velocity (second evs)))))))