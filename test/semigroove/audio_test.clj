(ns semigroove.audio-test
  (:require [clojure.test :refer [deftest is testing]]
            [semigroove.audio :as a]))

(deftest velocity->amp-clamps
  (is (= 0.5 (a/velocity->amp 0.5)))
  (is (= 1.0 (a/velocity->amp 1.7)) "clamps above 1")
  (is (= 0.0 (a/velocity->amp -0.3)) "clamps below 0"))