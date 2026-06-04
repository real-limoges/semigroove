(ns cantor.audio-test
  (:require [clojure.test :refer [deftest is testing]]
            [cantor.audio :as a]))

(deftest steal-voice-on-empty
  (let [[m prev] (a/steal-voice {} 60 :node-a)]
    (is (= {60 :node-a} m))
    (is (nil? prev) "no previous voice to free")))

(deftest steal-voice-displaces
  (testing "second note-on for a held pitch surfaces the old node to kill"
    (let [[m prev] (a/steal-voice {60 :old} 60 :new)]
      (is (= {60 :new} m))
      (is (= :old prev)))))

(deftest steal-voice-independent-pitches
  (let [[m _] (a/steal-voice {60 :a} 64 :b)]
    (is (= {60 :a 64 :b} m) "different pitches coexist")))

(deftest release-voice-drops-and-returns
  (let [[m node] (a/release-voice {60 :n 64 :m} 60)]
    (is (= {64 :m} m))
    (is (= :n node) "the freed node is returned so the caller can gate it")))

(deftest release-voice-missing-pitch
  (let [[m node] (a/release-voice {} 60)]
    (is (= {} m))
    (is (nil? node) "releasing a silent pitch is a no-op")))

(deftest velocity->amp-clamps
  (is (= 0.5 (a/velocity->amp 0.5)))
  (is (= 1.0 (a/velocity->amp 1.7)) "clamps above 1")
  (is (= 0.0 (a/velocity->amp -0.3)) "clamps below 0"))