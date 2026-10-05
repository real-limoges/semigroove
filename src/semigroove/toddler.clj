(ns semigroove.toddler
  "One-command toddler instrument: boot the sound server, wait for a Launchpad,
   and run instrument mode locked, in key, and capped in loudness."
  (:require [overtone.core :as o]
            [semigroove.audio.server :as server]
            [semigroove.grid.binding :as binding]
            [semigroove.synthdefs :refer [master-limiter]]))

(def settings
  "What makes instrument mode a toddler instrument. Plain data, so it can be
   tweaked live with (swap! binding/grid-state merge ...)."
  {:mode        :instrument
   :locked      true
   :layout      {:base 48 :scale :major-pentatonic :row-step 1}  ;; C3 up to A5
   :palette     :rainbow
   :voice       {:gain 0.3 :wave 3 :attack 0.02 :decay 0.2       ;; wave 3 = triangle
                 :sustain 0.6 :release 0.8 :cutoff 1800}
   :max-voices  4})

(defn- wait-for-launchpad
  "Retry start-launchpad! every two seconds until a Launchpad shows up."
  []
  (loop []
    (or (binding/start-launchpad!)
        (do (Thread/sleep 2000) (recur)))))

(defn -main [& _]
  (server/boot!)
  (master-limiter [:tail (o/foundation-safe-post-default-group)] :ceiling 0.5)
  (swap! binding/grid-state merge settings)
  (wait-for-launchpad)
  (println "Toddler mode in da house. Close window to quit.")
  @(promise))                                  ;; keep the JVM alive
