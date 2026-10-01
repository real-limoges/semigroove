(ns semigroove.audio
  (:require [overtone.core :refer [boot-external-server kill-server server-connected? ctl kill midi->hz volume]]
            [semigroove.synthdefs :refer [semigroove-note]]))

;; One voice per (sounding) pitch
;; Just need overtone nodes not raw ids
(defonce ^:private voices (atom {}))

;; Bookkeeping (No Server Needed)

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

(declare controls->synth-args)

(defn note-on
  "Sound a control map on a track, stealing any node already playing its :note
   there. Voices are keyed [track note], so the same note can ring on two tracks
   at once but never twice on one."
  [track controls]
  (let [note (:note controls)
        node (apply semigroove-note (controls->synth-args controls))
        prev (get-in @voices [track note])]
    (when prev (kill prev))                       ;; steal only within the same track
    (swap! voices assoc-in [track note] node)
    nil))

(defn note-off
  "Gate off a single note on a track and forget its voice."
  [track note]
  (when-let [node (get-in @voices [track note])]
    (ctl node :gate 0)
    (swap! voices update track dissoc note))
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

(def ^:private default-controls
  {:gain 0.8 :pan 0.0 :wave 0 :cutoff 2000
   :attack 0.01 :decay 0.1 :sustain 0.7 :release 0.3})

(defn controls->synth-args
  "Turn a control map into semigroove-note kwargs, filling gaps from
   default-controls. The one place a control map is interpreted."
  [c]
  (let [{:keys [note gain pan wave cutoff attack decay sustain release]}
        (clojure.core/merge default-controls c)]
    ;; Clamp gain the way velocity->amp always did, so a stray :gain 3 can't blast.
    [:freq (midi->hz note) :amp (velocity->amp gain) :pan pan :wave wave :cutoff cutoff
     :attack attack :decay decay :sustain sustain :release release :gate 1]))
