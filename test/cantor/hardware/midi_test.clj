(ns cantor.hardware.midi-test
  (:require [clojure.test :refer [deftest is testing]]
            [cantor.hardware.midi :refer [midi->action]]))

(deftest note-on-converts
  (let [a (midi->action {:note 60 :velocity 100 :command :note-on :channel 0 })]
    (is (= :on (:type a)))
    (is (= 60 (:pitch a)))
    (is (< 0.78 (:vel a) 0.79) "velocity 100/127 = 0.787")))

(deftest note-off-converts
  (let [a (midi->action {:note 60 :velocity 0 :command :note-off :channel 0})]
    (is (= :off (:type a)))
    (is (= 60 (:pitch a)))))

(deftest note-on-velocity-zero-is-note-off
  (testing "common MIDI shorthand: note-on vel=0 means note-off"
    (let [a (midi->action {:note 60 :velocity 0 :command :note-on :channel 0})]
      (is (= :off (:type a))))))

(deftest other-commands-return-nil
  (is (nil? (midi->action {:command :control-change :note 64 :velocity 127 :channel 0}))))