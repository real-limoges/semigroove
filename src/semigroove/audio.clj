(ns semigroove.audio
  (:require [overtone.core :refer [boot-external-server kill-server server-connected? ctl kill midi->hz volume]]
            [semigroove.synthdefs :refer [semigroove-note]]))


;; One voice per (sounding) pith
;; Just need overtone nodes not raw ids
(defonce ^:private voices (atom {}))


;; Bookkeeping (No Server Needed)

(defn steal-voice
  "Records teh node as the active voice for pitch
  Ret: [new-map prev-node] where prev-node is being booted. Nil allowed
  Steal only, no layering"
  [voice-map pitch node]
  [(assoc voice-map pitch node) (get voice-map pitch)])

(defn release-voice
  "Forgets the pitc. Returns [new-map node] so the caller can gate it off"
  [voice-map pitch]
  [(dissoc voice-map pitch) (get voice-map pitch)])

(defn velocity->amp
  "MIDI Velocity to Amplitude. Clamps (currently)"
  [v]
  (-> v (max 0.0) (min 1.0)))

;; Lifecycle

(defonce ^:private hook-registered (atom false))

(defn open!
  "Start the audio engine. Boots scsynth with audio input disabled (avoids
  sample-rate mismatches across devices) and registers the JVM shutdown hook."
  []
  (when-not (server-connected?)
    (boot-external-server (+ (rand-int 50000) 2000) {:max-input-bus 0})
    ; Calling semigroove-note triggers load-synthdef which uses with-server-sync —
    ; flushes all pending /s_new for mixer nodes before volume is set.
    (kill (semigroove-note :amp 0))
    (volume 1))
  (when (compare-and-set! hook-registered false true)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable (fn [] (kill-server)))))
  :ready)


;; Notes

(defn note-on
  "Start pitch at velocity. If pitch is already on, old voice is stolen (no release)"
  [pitch velocity]
  (let [node (semigroove-note :freq (midi->hz pitch)
                          :amp (velocity->amp velocity)
                          :gate 1)
        [m' prev] (steal-voice @voices pitch node)]
    (when prev (kill prev))
    (reset! voices m')
    nil))

(defn note-off
  "Release pitch, gate envelope to 0, doneAction FREE releases the node"
  [pitch]
  (let [[m' node] (release-voice @voices pitch)]
    (when node (ctl node :gate 0))
    (reset! voices m')
    nil))

(defn release-all
  "Gate every voice off (finish envelopes). This is the 'nice' version"
  []
  (doseq [[_ node] @voices] (ctl node :gate 0))
  (reset! voices {}))
