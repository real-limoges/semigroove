(ns cantor.hardware.launchpad
  (:require [overtone.core :as o]))

(def ^:private programmer-mode-sysex
  ;; this is the cde for launchpad mini mk3 (my launchpad)
  [0x00 0x20 0x29 0x02 0x0D 0x0E 0x01])

(defn enter-programmer-mode! [device]
  (o/midi-sysex device programmer-mode-sysex))

(defn note->coord [note]
  {:row (dec (quot note 10))
   :col (dec (rem  note 10))})

(defn coord->note [row col]
  (+ (* (inc row) 10) (inc col)))

(def pad-colors
  {:off   0     ;; LED off
   :dim   5     ;; dim red
   :on    21})  ;; bright green

(defn set-led! [device note color-key]
  (o/midi-note-on device note (pad-colors color-key)))
