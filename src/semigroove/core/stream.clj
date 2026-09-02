(ns semigroove.core.stream
  (:refer-clojure :exclude [cat merge])
  (:require [semigroove.core.types :as t]))

;; Helper

(defn query
  "Materialize the events a stream produces inside an arc. A stream is just a
   function of an arc, so this is only sugar for calling it."
  [stream arc]
  (stream arc))

;; Construction

(defn silence
  "The empty stream: nothing, over any arc."
  []
  (fn [_arc] []))

(defn periodic
  "Loop a fixed event vector every period beats, forever. A period of zero or
   less has nothing to repeat, so it collapses to silence."
  [period events]
  (if-not (pos? period)
    (silence)
    (fn [{:keys [start end]}]
      (let [start-cycle (long (Math/floor (/ start period)))
            end-cycle   (long (Math/ceil  (/ end   period)))]
        (for [n (range start-cycle end-cycle)
              e  events
              :let [shifted (t/shift-event (* n period) e)
                    s-abs   (-> shifted :part :start)]
              :when (and (>= s-abs start) (< s-abs end))]
          shifted)))))


;; Composition

(defn shift
  "Translate every event forward by OFFSET beats"
  [stream offset]
  (fn [arc]
    (->> (stream (t/shift-arc (- offset) arc))
         (map   #(t/shift-event offset %)))))

(defn merge
  "Layer exactly two streams, their events interleaved in time order."
  [s1 s2]
  (fn [arc]
    (sort-by #(-> % :part :start)
             (concat (s1 arc) (s2 arc)))))

(defn stack
  "Layer any number of streams, folding them together over silence."
  [streams]
  (reduce merge (silence) streams))

(defn cat
  "Play streams one after another, each getting a period-beat slot, cycling back
   to the first once the list runs out."
  [streams period]
  (if (or (empty? streams) (not (pos? period)))
    (silence)
    (let [n (count streams)
          local (t/arc 0 period)]
      (fn [{:keys [start end]}]
        (let [start-cycle (long (Math/floor (/ start period)))
              end-cycle   (long (Math/ceil (/ end period)))]
          (for [i (range start-cycle end-cycle)
                :let [slot (nth streams (mod i n))
                      cycle-start (* i period)
                      shifted (map #(t/shift-event cycle-start %) (slot local))]
                e     shifted
                :when (let [s (-> e :part :start)]
                        (and (>= s start) (< s end)))]
            e))))))


;; Time Scaling

(defn slow
  "Stretch a stream by a factor of k. `(slow s 2)` plays at half speed"
  [stream k]
  (if-not (pos? k)
    (silence)
    (fn [arc]
      (let [inv-k (/ 1 k)]
        (->> (stream (t/scale-arc inv-k arc))
             (map (fn [e]
                    (-> e
                        (update :whole #(t/scale-arc k %))
                        (update :part  #(t/scale-arc k %))))))))))


(defn fast
  "Compress a stream by a factor of k. `(fast s 2)` plays at double speed"
  [stream k]
  (if (zero? k)
    (silence)
    (slow stream (/ 1 k))))
