(ns semigroove.grid.binding
  (:require [overtone.midi                 :as midi]
            [semigroove.audio              :as a]
            [semigroove.audio.scheduler    :as sched]
            [semigroove.core.stream        :as s]
            [semigroove.grid               :as g]
            [semigroove.hardware.launchpad :as lp]
            [semigroove.live               :as live]))

(defonce grid-state
  (atom {:mode          :sequencer
         :grid          g/empty-grid
         :pitch         60
         :layout        {}
         :palette       :field
         :voice         {:gain 0.8}
         :max-gain      0.8
         :max-voices    16
         :held          []
         :locked        false
         :scenes        g/empty-scenes}))

(defonce device-atom (atom nil))

(def ^:private mode-buttons
  "Function-row button number -> mode. Row 8 in note->coord terms."
  {91 :sequencer 92 :instrument 93 :scene})

(defn- step->note
  "Launchpad note for sequencer STEP. The 16 steps fill the bottom two rows."
  [step]
  (lp/coord->note (quot step 8) (rem step 8)))

;; rendering

(defn- render-mode!
  "Repaint the 8x8 and the mode buttons to match grid-state."
  [device]
  (let [{:keys [mode grid scenes locked] :as state} @grid-state]
    (doseq [row (range 8) col (range 8)]
      (lp/set-led! device (lp/coord->note row col) :off))
    (case mode
      :sequencer  (doseq [step (range 16)]
                    (lp/set-led! device (step->note step) (if (get grid step) :on :dim)))
      :instrument (doseq [row (range 8) col (range 8)]
                    (lp/set-led! device (lp/coord->note row col) (g/field-color state row col)))
      :scene      (doseq [row (range 8) col (range 8)]
                    (lp/set-led! device (lp/coord->note row col)
                                 (g/slot-color scenes (g/rc->pad row col)))))
    (doseq [[button m] mode-buttons]
      (lp/set-button-led! device button (if (= m mode) :mode :off)))
    ;; Safe mode indicator: steady green on bottom-right pad when locked
    (when locked
      (lp/set-led! device (lp/coord->note 7 7) :green))))

(defn- repaint-if-showing!
  "Repaint when a Launchpad is attached and MODE is the one on screen."
  [mode]
  (when (and @device-atom (= mode (:mode @grid-state)))
    (render-mode! @device-atom)))

;; per-mode handlers

(defn- handle-sequencer [device note row col edge]
  ;; the bottom two rows are the 16 steps; toggle on press only
  (when (and (= edge :press) (< row 2))
    (let [step (+ (* row 8) col)]
      (swap! grid-state update :grid g/toggle-pad step)
      (let [{:keys [grid pitch]} @grid-state]
        (lp/set-led! device note (if (get grid step) :on :dim))
        (live/add-track :seq (g/grid->stream grid pitch))))))  ;; swap, don't restart

(defn- handle-instrument
  "Sound or release one pad on the :grid track. Held notes are keyed by pad, so
   a release gates the pitch its press sounded even if the scale changed in
   between. A press on a muted (or soloed-out) :grid track is still held, so its
   release stays clean, but makes no sound."
  [device note row col edge]
  (let [{:keys [layout voice max-voices held] :as state} @grid-state]
    (case edge
      :press   (let [pitch                          (g/pad->pitch row col layout)
                     {held' :held silence :silence} (g/press-pad held note pitch max-voices)]
                 (swap! grid-state assoc :held held')
                 (doseq [p silence] (a/note-off :grid p))
                 (when (sched/audible? @sched/scheduler-state :grid)
                   (a/note-on :grid (merge voice {:note pitch})))
                 (lp/set-led! device note :on))  ;; Brighter color when playing
      :release (let [{held' :held silence :silence} (g/release-pad held note)]
                 (swap! grid-state assoc :held held')
                 (doseq [p silence] (a/note-off :grid p))
                 (lp/set-led! device note (g/field-color state row col))))))

(defn- handle-scene [device note row col edge]
  ;; press launches the pad's clip onto :scene; it loops until the next launch
  (when (= edge :press)
    (let [pad (g/rc->pad row col)]
      (when-let [clip (g/scene-at (:scenes @grid-state) pad)]
        (live/add-track :scene clip)                    ;; transport keeps running
        (swap! grid-state update :scenes g/launch pad)
        (render-mode! device)))))

;; the dispatcher

(defn- handle-mode-press
  "A function-row button went down: start timing it, and switch mode when the
   grid is unlocked and the button names one."
  [device note]
  (swap! grid-state g/mode-button-press (mode-buttons note) (System/nanoTime))
  (render-mode! device))

(defn- handle-mode-release
  "A function-row button came up. A hold of a second or more releases every
   voice; a tap on a locked grid moves to the next toddler scale."
  [device]
  (let [now     (System/nanoTime)
        [old _] (swap-vals! grid-state #(first (g/mode-button-release % now)))
        outcome (second (g/mode-button-release old now))]
    (case outcome
      :reset         (do (a/release-all) (render-mode! device))
      :scale-changed (render-mode! device)
      :nothing       nil)))

(defn- handle-volume-control
  "A volume-column press: one step up the gain ladder, wrapping at :max-gain."
  [device]
  (swap! grid-state g/press-volume)
  (render-mode! device))

(defn- pad-handler
  "Main handler for all pad events"
  [event device]
  (let [note              (:note event)
        {:keys [row col]} (lp/note->coord note)
        {:keys [edge]}    (g/classify-press event)]
    (cond
      (= edge :ignore) nil

      ;; Function row button
      (= row 8)
      (case edge
        :press   (handle-mode-press device note)
        :release (handle-mode-release device))

      ;; Volume control (right column)
      (and (= col 8) (= edge :press))
      (handle-volume-control device)

      ;; The 8x8 grid: route to active mode
      (and (<= 0 row 7) (<= 0 col 7))
      (case (:mode @grid-state)
        :sequencer  (handle-sequencer  device note row col edge)
        :instrument (handle-instrument device note row col edge)
        :scene      (handle-scene      device note row col edge))

      :else nil)))

(defn- safe-pad-handler
  "pad-handler for the MIDI receiver thread, which has no try of its own: an
   exception there never reaches the REPL and can strand a held note, so on
   any error release every voice."
  [event device]
  (try (pad-handler event device)
       (catch Exception e
         (a/release-all)
         (swap! grid-state assoc :held [])
         (println "pad-handler error:" (.getMessage e)))))

;; startup

(defn- open-launchpad
  "Open the Launchpad MIDI in/out pair by name, or return nil when the device
   is absent. midi/midi-out and midi/midi-in throw IllegalArgumentException when no
   sink/source matches, so we swallow that and let the caller no-op."
  []
  (try
    ;; the Mini MK3 has a DAW port and a MIDI port, and a bare "Launchpad"
    ;; matches DAW first; programmer mode lives on the MIDI port. The
    ;; CoreMIDI4J copy of the port is the one that can send SysEx
    {:out (midi/midi-out "CoreMIDI4J - Launchpad Mini MK3 LPMiniMK3 MIDI")
     :in  (midi/midi-in "CoreMIDI4J - Launchpad Mini MK3 LPMiniMK3 MIDI")}
    (catch IllegalArgumentException _
      nil)))

(defn start-launchpad!
  "Wire up the Launchpad: enter programmer mode, paint the current mode, and
   route every pad and button through pad-handler. PITCH is the sequencer's
   note. Returns the MIDI-out device, or nil (with a warning) when no Launchpad
   is connected."
  ([] (start-launchpad! 60))
  ([pitch]
   (if-let [{:keys [out in]} (open-launchpad)]
     (do
       (a/open!)
       ;; add-track needs a running tick loop to join
       (when-not (:running @sched/scheduler-state)
         (live/play (s/silence)))
       (swap! grid-state assoc :pitch pitch)
       (reset! device-atom out)
       (lp/enter-programmer-mode! out)
       (render-mode! out)
       (midi/midi-handle-events in #(safe-pad-handler % out))
       out)
     (do
       (println "start-launchpad!: no Launchpad found in midi/midi-sinks; skipping.")
       nil))))

;; the mode lock, from the REPL

(defn lock!   [] (swap! grid-state assoc :locked true)  :locked)
(defn unlock! [] (swap! grid-state assoc :locked false) :unlocked)

;; scenes, from the REPL

(defn set-scene!
  "Bind one stream to a pad index (0..63). Repaints if scene mode is showing."
  [pad stream]
  (swap! grid-state update :scenes g/put-scene pad stream)
  (repaint-if-showing! :scene)
  pad)

(defn load-scenes!
  "Bulk-bind from a {pad stream} map."
  [m]
  (doseq [[pad stream] m] (set-scene! pad stream))
  (keys m))

(defn stop-scene!
  "Silence the scene track and unlight the active pad."
  []
  (live/drop-track :scene)
  (swap! grid-state update :scenes g/stop-scenes)
  (repaint-if-showing! :scene)
  :stopped)
