(ns semigroove.core.types)


;; Arc: Half open interval [start, end)

(defn arc 
  "A half open interval [start, end)"
  [start end]
  {:start start :end end})

(defn arc-length
  "How many beats an arc spans."
  [{:keys [start end]}]
  (- end start))

(defn shift-arc
  "Slide an arc forward by offset beats, keeping its length."
  [offset {:keys [start end]}]
  {:start (+ start offset) :end (+ end offset)})

(defn scale-arc
  "Stretch an arc about the origin by k. Both ends scale, so an arc anchored at
   0 stays anchored."
  [k {:keys [start end]}]
  {:start (* start k) :end (* end k)})

;; Event: A value attached to an arc

(defn event
  "An event whose :whole and :part are equal."
  ([arc value]          {:whole arc :part arc :value value :velocity 1.0})
  ([arc value velocity] {:whole arc :part arc :value value :velocity (or velocity 1.0)}))


(defn shift-event
  "Slide an event forward by offset beats, moving :whole and :part together."
  [offset e]
  (-> e
      (update :whole #(shift-arc offset %))
      (update :part  #(shift-arc offset %))))

;; Helper for vector of pitches -> vector of events

(defn notes
  "Lay a vector of values out one per beat: value i lands on arc [i, i+1). A bare
   number is a pitch; a map carries its own :pitch and :vel (or :velocity)."
  [values]
  (vec
    (map-indexed
      (fn [i v]
        (if (map? v)
          (event (arc i (inc i)) (:pitch v) (or (:vel v) (:velocity v)))
          (event (arc i (inc i)) v)))
      values)))