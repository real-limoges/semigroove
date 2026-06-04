(ns cantor.core.types-test
  (:require [clojure.test :refer [deftest is testing]]
            [cantor.core.types :as t]))

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
