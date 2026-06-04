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
  [arc value]
  {:whole arc :part arc :value value})

(defn shift-event [offset {:keys [whole part value]}]
  {:whole (shift-arc offset whole)
   :part  (shift-arc offset part)
   :value  value})

;; Helper for vector of pitches -> vector of events

(defn notes
  "Turns a vector of VALUES into integer beats.
   (notes [60 62 64] [[0,1) [1,2) [2,3))"
  [values]
  (vec
   (map-indexed
    (fn [i v] (event (arc i (inc i)) v))
    values)))