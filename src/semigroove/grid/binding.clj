(ns semigroove.grid.binding
  (:require [overtone.core                 :as o]
            [semigroove.grid               :as g]
            [semigroove.hardware.launchpad :as lp]
            [semigroove.live               :as live]))

(defonce grid-state
  (atom {:grid  g/empty-grid
         :pitch 60}))

(defn- pad-press-handler
  "React to a pad press: toggle its step, repaint its LED, and push the new grid
   to the live sequencer. Only the top two 8-pad rows count as steps; the rest of
   the Launchpad (top row, right column, lower rows) is ignored."
  [event device]
  (let [note (:note event)
        {:keys [row col]} (lp/note->coord note)]
    ;; ignore the top row, right column (auxiliary buttons)
    (when (and (<= 0 row 7) (<= 0 col 7))
      (let [;; map 8x2 layout to 16 step index
            step (if (< row 2)
                   (+ (* row 8) col)
                   nil)]
        (when step
          (swap! grid-state update :grid g/toggle-pad step)
          (let [{:keys [grid pitch]} @grid-state
                active? (get grid step)]
            (lp/set-led! device note (if active? :on :dim))
            (live/play (g/grid->stream grid pitch))))))))

(defn- open-launchpad
  "Open the Launchpad MIDI in/out pair by name, or return nil when the device
   is absent. o/midi-out and o/midi-in throw IllegalArgumentException when no
   sink/source matches, so we swallow that and let the caller no-op."
  []
  (try
    {:out (o/midi-out "Launchpad")   ; matches name from o/midi-sinks
     :in  (o/midi-in "Launchpad")}
    (catch IllegalArgumentException _
      nil)))

(defn start-launchpad!
  "Wire up the Launchpad as a step sequencer for PITCH (middle C by default):
   enter programmer mode, light the 16 step pads dim, and route pad presses
   through pad-press-handler. Returns the MIDI-out device, or nil (with a
   warning) when no Launchpad is connected."
  ([] (start-launchpad! 60))
  ([pitch]
   (if-let [{:keys [out in]} (open-launchpad)]
     (do
       (swap! grid-state assoc :pitch pitch)
       (lp/enter-programmer-mode! out)
       ;; illuminate all 16 steps as dim (off state)
       (doseq [row (range 2) col (range 8)]
         (lp/set-led! out (lp/coord->note row col) :dim))
       (o/midi-handle-events in #(pad-press-handler % out))
       out)
     (do
       (println "start-launchpad!: no Launchpad found in o/midi-sinks; skipping.")
       nil))))
