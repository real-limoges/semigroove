(ns cantor.generative.markov
  (:require [cantor.core.stream :as s]
            [cantor.harmony     :as h]))

(defn weighted-choice
  "Pick one next-state from PAIRS = [[weight state] ...] given ROLL in
  [0, total). Weights do not need to sum to 1 - the roll is normlaized
  by the caller. Pure and deterministic; the last pair is the catch-all.
  (weighted-choice [[0.6 :a] [0.4 :b]] 0.5) => :a"
  [pairs roll]
  (loop [r roll, ps pairs]
    (let [[[w s] & more] ps]
      (if (or (empty? more) (<= r w))
        s
        (recur (- r w) more)))))

(defn step
  "One markov step from STATE. States with no outbound transitions stay put.
  RNG is a java.utils.Random; the roll is normalized."
  [chain state ^java.util.Random rng]
  (let [outs (get chain state)]
    (if (seq outs)
      (let [total (reduce + (map first outs))
            roll (* (.nextDouble rng) total)]
        (weighted-choice outs roll))
      state)))

(defn walk
  "Lazy infinite sequence of states starting at START, driven by the RNG.
   Seed RNG with (java.util.Random. n) for a reproducible walk."
  [chain start ^java.util.Random rng]
  (cons start (lazy-seq (walk chain (step chain start rng) rng))))

(def i7  {:root 60 :quality :dominant7})
(def iv7 {:root 65 :quality :dominant7})
(def v7  {:root 67 :quality :dominant7})
(def ii7 {:root 62 :quality :minor7})

(def jazz-blues-chain
  "12-bar jazz blues skeleton over C7: I7 -> IV7 -> I7 -> V7 ii-V -> I7."
  {i7  [[0.6 iv7] [0.3 v7]  [0.1 ii7]]
   iv7 [[0.7 i7]  [0.2 iv7] [0.1 ii7]]
   v7  [[0.6 i7]  [0.3 ii7] [0.1 iv7]]
   ii7 [[0.7 v7]  [0.3 i7]]})

(defn markov-stream
  "Walk N chords from START and concatenate one chord-stream per state,
   PERIOD beats each. RNG is a seeded java.util.Random.
   The whole progression loops every (* n period) beats"
  [chain start n period rng]
  (let [states (vec (take n (walk chain start rng)))]
    (s/cat (mapv #(h/chord-stream % period) states) period)))
