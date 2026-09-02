(ns semigroove.hardware.launchpad
  (:require [overtone.core :as o]))

(def ^:private programmer-mode-sysex
  "The SysEx that flips a Launchpad Mini MK3 into programmer mode, where every
   pad reports a clean row/column note instead of the default session layout."
  ;; this is the cde for launchpad mini mk3 (my launchpad)
  [0x00 0x20 0x29 0x02 0x0D 0x0E 0x01])

(defn enter-programmer-mode!
  "Put the device into programmer mode so note->coord/coord->note line up."
  [device]
  (o/midi-sysex device programmer-mode-sysex))

(defn note->coord
  "Decode a programmer-mode pad note into {:row :col}. The Launchpad numbers
   pads in base 10 (tens digit is the row, ones digit is the column), both
   1-based, so I shift each back to a 0-based grid."
  [note]
  {:row (dec (quot note 10))
   :col (dec (rem  note 10))})

(defn coord->note
  "The inverse of note->coord: a 0-based row/column back to a pad note."
  [row col]
  (+ (* (inc row) 10) (inc col)))

(def pad-colors
  "The three LED states I use, as Launchpad velocity values."
  {:off   0     ;; LED off
   :dim   5     ;; dim red
   :on    21})  ;; bright green

(defn set-led!
  "Light a pad one of the pad-colors states. Color is sent as the note velocity,
   which is how the Launchpad takes LED colors in programmer mode."
  [device note color-key]
  (o/midi-note-on device note (pad-colors color-key)))
