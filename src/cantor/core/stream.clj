(ns cantor.core.stream
  (:refer-clojure :exclude [cat merge])
  (:require [cantor.core.types :as t]))

;; Helper

(defn query
  "Runs a stream over an arc. Sugar"
  [stream arc]
  (stream arc))

;; Construction

(defn silence []
  (fn [_arc] []))

(defn periodic
  "Loop EVENTS every PERIOD beats forever.
   Uses t/notes to build event vector from raw values"
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
  "Layer exactly two streams together"
  [s1 s2]
  (fn [arc]
    (sort-by #(-> % :part :start)
             (concat (s1 arc) (s2 arc)))))

(defn stack
  "Layer N streams."
  [streams]
  (reduce merge (silence) streams))

(defn cat
  "Concatenate streams"
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
