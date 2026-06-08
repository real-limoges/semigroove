(ns cantor.grid-test
  (:require [clojure.test :refer :all]
            [cantor.grid        :as g]
            [cantor.core.types  :as t]
            [cantor.core.stream :as s]))

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
