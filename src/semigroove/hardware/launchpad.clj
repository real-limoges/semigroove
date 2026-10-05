(ns semigroove.hardware.launchpad
  (:require [overtone.midi :as midi]))

(def ^:private programmer-mode-sysex
  "The SysEx that flips a Launchpad Mini MK3 into programmer mode, where every
   pad reports a clean row/column note instead of the default session layout."
  ;; this is the code for launchpad mini mk3 (my launchpad). javax.sound wants
  ;; the F0/F7 framing included, or it throws before anything is sent
  [0xF0 0x00 0x20 0x29 0x02 0x0D 0x0E 0x01 0xF7])

(defn enter-programmer-mode!
  "Put the device into programmer mode so note->coord/coord->note line up."
  [device]
  (midi/midi-sysex device programmer-mode-sysex))

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
  "LED states as Launchpad palette indices (sent as note velocity)."
  {:off      0    ;; LED off
   :dim      5    ;; dim red: sequencer step off, bound scene
   :on       21   ;; bright green: sequencer step on, launched scene
   :playhead 3    ;; white: the step sounding now
   :field    37   ;; dim blue: instrument pad, idle
   :press    3    ;; white: instrument pad, held
   :mode     9    ;; amber: the active mode button
   :red      5
   :orange   9
   :yellow   13
   :green    21
   :cyan     37
   :blue     45
   :purple   53})

(defn set-led!
  "Light a pad one of the pad-colors states. Color is sent as the note velocity,
   which is how the Launchpad takes LED colors in programmer mode."
  [device note color-key]
  (midi/midi-note-on device note (pad-colors color-key)))

(defn set-button-led!
  "Light a function-row or side button. On the Mini MK3 in programmer mode these
   are control changes, not notes."
  [device cc color-key]
  (midi/midi-control device cc (pad-colors color-key)))
