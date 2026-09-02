(ns semigroove.audio
  (:require [overtone.core :refer [boot-external-server kill-server server-connected? ctl kill midi->hz volume]]
            [semigroove.synthdefs :refer [semigroove-note]]))

;; One voice per (sounding) pith
;; Just need overtone nodes not raw ids
(defonce ^:private voices (atom {}))

;; Bookkeeping (No Server Needed)

(defn steal-voice
  "Record node as the active voice for pitch. Returns [new-map prev-node] so the
   caller can kill whatever was sounding. Steal only, never layer; one node per
   pitch is the whole model."
  [voice-map pitch node]
  [(assoc voice-map pitch node) (get voice-map pitch)])

(defn release-voice
  "Forget pitch. Returns [new-map node] so the caller can gate the old node off."
  [voice-map pitch]
  [(dissoc voice-map pitch) (get voice-map pitch)])

(defn velocity->amp
  "MIDI velocity to amplitude. Just a clamp to [0, 1] for now."
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
  "Sound a pitch on a track, stealing any node already playing that pitch there.
   Voices are keyed [track pitch], so the same pitch can ring on two tracks at
   once but never twice on one."
  [track pitch velocity]
  (let [node (semigroove-note :freq (midi->hz pitch)
                              :amp  (velocity->amp velocity)
                              :gate 1)
        prev (get-in @voices [track pitch])]
    (when prev (kill prev))                       ;; steal only within the same track
    (swap! voices assoc-in [track pitch] node)
    nil))

(defn note-off
  "Gate off a single pitch on a track and forget its voice."
  [track pitch]
  (when-let [node (get-in @voices [track pitch])]
    (ctl node :gate 0)
    (swap! voices update track dissoc pitch))
  nil)

(defn release-track
  "Gate off every voice on one track and drop the track."
  [track]
  (doseq [[_ node] (get @voices track)] (ctl node :gate 0))
  (swap! voices dissoc track))

(defn release-all
  "Gate off every voice on every track. The panic button."
  []
  (doseq [[_ pitches] @voices, [_ node] pitches] (ctl node :gate 0))
  (reset! voices {}))
