(ns cantor.core.types)


;; Arc: Half open interval [start, end)

(defn arc 
  "A half open interval [start, end)"
  [start end]
  {:start start :end end})

(defn arc-length [{:keys [start end]}]
  (- end start))

(defn shift-arc [offset {:keys [start end]}]
  {:start (+ start offset) :end (+ end offset)})

(defn scale-arc [k {:keys [start end]}]
  {:start (* start k) :end (* end k)})

;; Event: A value attached to an arc

(defn event
  "An event whose :whole and :part are equal."
  ([arc value]          {:whole arc :part arc :value value :velocity 1.0})
  ([arc value velocity] {:whole arc :part arc :value value :velocity (or velocity 1.0)}))


(defn shift-event [offset e]
  (-> e
      (update :whole #(shift-arc offset %))
      (update :part  #(shift-arc offset %))))

;; Helper for vector of pitches -> vector of events

(defn notes
  "Turns a vector of VALUES into integer beats.
   (notes [60 62 64] [[0,1) [1,2) [2,3))"
  [values]
  (vec
    (map-indexed
      (fn [i v]
        (if (map? v)
          (event (arc i (inc i)) (:pitch v) (or (:vel v) (:velocity v)))
          (event (arc i (inc i)) v)))
      values)))