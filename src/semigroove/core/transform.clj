(ns semigroove.core.transform
  "Pattern transforms: stream -> stream combinators in the Tidal mold.
   Pure; every one returns a new stream function and never touches audio."
  (:require [semigroove.core.stream :as s]
            [semigroove.core.types :as t]))

(def ^:dynamic *cycle*
  "Beats per cycle, the unit the cyclic transforms reverse, rotate and divide
   over. Tidal fixes it at 1; semigroove is beat-based, so it is an explicit
   knob. Default 4, one bar of 4/4. The one-cycle-less arities read it when the
   transform is built, not when it is queried."
  4)

(defmacro with-cycle
  "Build the transforms in BODY with *cycle* bound to BEATS."
  [beats & body]
  `(binding [*cycle* ~beats] ~@body))

(defn- floor-div [a b] (long (Math/floor (/ a b))))
(defn- ceil-div  [a b] (long (Math/ceil  (/ a b))))

(defn- per-cycle
  "For each CYC-cycle overlapping the query arc, call (f cycle-index cycle-base)
   to produce events (already in absolute beats), then keep those whose :part
   start lands in the arc, the same windowing rule periodic and cat use."
  [cyc {:keys [start end]} f]
  (let [c0 (floor-div start cyc)
        c1 (ceil-div  end   cyc)]
    (for [i (range c0 c1)
          e (f i (* i cyc))
          :let [st (-> e :part :start)]
          :when (and (>= st start) (< st end))]
      e)))

;; Structure: these move events in time and carry the value opaquely.

(defn rev
  "Reverse each cycle in time. An event whose :whole crosses a cycle edge is
   dropped; nothing in semigroove produces one yet."
  ([s] (rev *cycle* s))
  ([cyc s]
   (fn [arc]
     (per-cycle cyc arc
                (fn [_i base]
                  ;; Mirror [a, b) inside [base, base+cyc) to [m-b, m-a).
                  (let [m       (+ (* 2 base) cyc)
                        reflect (fn [a] (t/arc (- m (:end a)) (- m (:start a))))]
                    (->> (s (t/arc base (+ base cyc)))
                         (map (fn [e] (-> e
                                          (update :whole reflect)
                                          (update :part reflect)))))))))))

(defn iter
  "Pull the content (i mod n)/n of a cycle earlier on cycle i, so over n
   cycles the phrase walks through n rotations of itself."
  ([n s] (iter *cycle* n s))
  ([cyc n s]
   (fn [arc]
     (per-cycle cyc arc
                (fn [i base]
                  (let [amount (* (mod i n) (/ cyc n))]
                    ((s/shift s (- amount)) (t/arc base (+ base cyc)))))))))

(defn rot
  "Rotate which value sits in each slot by K, leaving every onset in place."
  ([k s] (rot *cycle* k s))
  ([cyc k s]
   (fn [arc]
     (per-cycle cyc arc
                (fn [_i base]
                  (let [evs (vec (sort-by #(-> % :part :start)
                                          (s (t/arc base (+ base cyc)))))
                        n (count evs)]
                    (if (zero? n)
                      evs
                      (let [vals (mapv :value evs)]
                        (map-indexed
                         (fn [idx e] (assoc e :value (nth vals (mod (+ idx k) n))))
                         evs)))))))))

;; Conditional: pick s or (f s) per cycle.

(defn every
  "Apply f on every n-th cycle, starting with cycle 0."
  ([n f s] (every *cycle* n f s))
  ([cyc n f s]
   (let [fs (f s)]
     (fn [arc]
       (per-cycle cyc arc
                  (fn [i base]
                    (let [chosen (if (zero? (mod i n)) fs s)]
                      (chosen (t/arc base (+ base cyc))))))))))

(defn whenmod
  "Apply f on cycles where (cycle mod a) >= b."
  ([a b f s] (whenmod *cycle* a b f s))
  ([cyc a b f s]
   (let [fs (f s)]
     (fn [arc]
       (per-cycle cyc arc
                  (fn [i base]
                    (let [chosen (if (>= (mod i a) b) fs s)]
                      (chosen (t/arc base (+ base cyc))))))))))

(defn palindrome
  "Play s forward on even cycles and reversed on odd ones."
  ([s] (palindrome *cycle* s))
  ([cyc s]
   (fn [arc]
     (per-cycle cyc arc
                (fn [i base]
                  (let [chosen (if (even? i) s (rev cyc s))]
                    (chosen (t/arc base (+ base cyc)))))))))

;; Randomness: seeded by event onset, so every query of the same event agrees.

(defn- rand01
  "A stable pseudo-random in [0, 1) for an event onset. Depends only on SEED and
   BEAT, never on when the stream is queried, so a note can't flicker."
  [seed beat]
  (let [h (hash [seed beat])]
    (/ (bit-and h 0x7fffffff) (double 0x80000000))))

(defn degrade-by
  "Drop each event with probability p."
  ([p s] (degrade-by p 0 s))
  ([p seed s]
   (fn [arc] (filter #(>= (rand01 seed (-> % :part :start)) p) (s arc)))))

(defn undegrade-by
  "Keep exactly the events degrade-by with the same p and seed would drop."
  ([p s] (undegrade-by p 0 s))
  ([p seed s]
   (fn [arc] (filter #(< (rand01 seed (-> % :part :start)) p) (s arc)))))

(defn degrade "Drop about half the events." [s] (degrade-by 0.5 s))

(defn sometimes-by
  "Apply f to a p-fraction of the events, leave the rest. The split is the
   degrade/undegrade complement, so no event is dropped or doubled."
  ([p f s] (sometimes-by p 0 f s))
  ([p seed f s]
   (s/merge (degrade-by p seed s)
            (f (undegrade-by p seed s)))))

(defn sometimes "Apply f to about half the events." [f s] (sometimes-by 0.5  f s))
(defn often     "Apply f to about 3/4 of the events." [f s] (sometimes-by 0.75 f s))
(defn rarely    "Apply f to about 1/4 of the events." [f s] (sometimes-by 0.25 f s))

;; Values: each takes its stream last and, given only its parameter, returns a
;; stream -> stream function, so (off 1/2 (add 7) s) reads the way Tidal does.

(defn vmap
  "Map f over each event's :value."
  ([f] (partial vmap f))
  ([f s] (fn [arc] (map #(update % :value f) (s arc)))))

(defn- set-control [k v]
  (fn [val] (assoc (t/controls val) k v)))

(defn gain   "Set :gain."              ([g] (partial gain g))   ([g s] (vmap (set-control :gain   g) s)))
(defn pan    "Set :pan, -1 to 1."      ([p] (partial pan p))    ([p s] (vmap (set-control :pan    p) s)))
(defn cutoff "Set the filter :cutoff." ([c] (partial cutoff c)) ([c s] (vmap (set-control :cutoff c) s)))
(defn sound  "Set the :wave select."   ([w] (partial sound w))  ([w s] (vmap (set-control :wave   w) s)))

(defn add
  "Transpose :note by n semitones."
  ([n] (partial add n))
  ([n s] (vmap (fn [val] (update (t/controls val) :note + n)) s)))

;; Layering.

(defn superimpose
  "Layer (f s) on top of s."
  [f s]
  (s/merge s (f s)))

(defn off
  "Layer a copy shifted BEATS later and transformed by f."
  [beats f s]
  (s/merge s (f (s/shift s beats))))

(defn jux
  "Stereo: s hard left, (f s) hard right."
  [f s]
  (s/stack [(pan -1 s) (pan 1 (f s))]))
