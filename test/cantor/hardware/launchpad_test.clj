(ns cantor.hardware.launchpad-test
  (:require [clojure.test :refer :all]
            [cantor.hardware.launchpad :as lp]))

(deftest coord-round-trip
  (doseq [row (range 8) col (range 8)]
    (is (= {:row row :col col}
           (lp/note->coord (lp/coord->note row col)))
        "row/col round-trips")))

(deftest known-corners
  (is (= 11 (lp/coord->note 0 0)) "bottom-left is note 11")
  (is (= 88 (lp/coord->note 7 7)) "top-right is note 88")
  (is (= {:row 0 :col 0} (lp/note->coord 11)))
  (is (= {:row 7 :col 7} (lp/note->coord 88))))
