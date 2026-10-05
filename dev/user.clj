(ns user
  (:require [overtone.core :refer :all]
            [semigroove.audio.server :as server]))

(defn boot! [] (server/boot!))
