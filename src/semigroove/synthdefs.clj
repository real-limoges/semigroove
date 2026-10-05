(ns semigroove.synthdefs
  (:require [overtone.core :refer :all]))

(defsynth semigroove-note
  [freq    440
   amp     0.5
   gate    1
   attack  0.01
   decay   0.1
   sustain 0.7
   release 0.3
   wave    0      ; 0=sine 1=saw 2=square 3=triangle
   cutoff  2000
   pan     0]     ; -1.0 (L) … 1.0 (R)
  (let [env (env-gen (adsr attack decay sustain release) gate :action FREE)
        sig (select wave [(sin-osc freq)
                          (saw freq)
                          (pulse freq 0.5)
                          (lf-tri freq)])
        sig (lpf sig cutoff)]
    (out 0 (pan2 (* sig env amp) pan))))

;; Brick-wall limiter on the main stereo out. Placed at the tail of Overtone's
;; safe post-default group, it runs after every note and survives (stop).
(defsynth master-limiter [ceiling 0.5]
  (replace-out 0 (limiter (in 0 2) ceiling 0.01)))
