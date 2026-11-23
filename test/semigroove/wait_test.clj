(ns semigroove.wait-test
  (:require [clojure.test :refer [deftest is]]
            [semigroove.wait :as wait]))

(deftest ready-at-once
  (is (= :ready (wait/until (constantly true) 1000 10))))

(deftest ready-after-a-few-polls
  (let [polls (atom 0)]
    (is (= :ready (wait/until #(>= (swap! polls inc) 3) 1000 1)))
    (is (= 3 @polls))))

(deftest never-ready-times-out
  (is (= :timed-out (wait/until (constantly false) 30 5))))
