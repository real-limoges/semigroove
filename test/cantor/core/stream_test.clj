(ns cantor.core.stream-test
  (:require [clojure.test :refer [deftest is testing]]
            [cantor.core.stream :as s]
            [cantor.core.types :as t]))

(defn- starts [events]
  (mapv #(-> % :part :start) events))

(defn- values [events]
  (mapv :value events))

;; periodic

(deftest perioidic-one-cycle
  (let [stream (s/periodic 3 (t/notes [60 62 64]))
        events (s/query stream (t/arc 0 3))]
    (is (= 3 (count events)))
    (is (= [60 62 64] (values events)))
    (is (= [0 1 2] (starts events)))))

(deftest perioidic-loops
  (let [stream (s/periodic 2 (t/notes [60 62]))
        events (s/query stream (t/arc 0 4))]
    (is (= [60 62 60 62] (values events)))
    (is (= [0 1 2 3] (starts events)))))

(deftest perioidic-half-open
  (testing "events at arc end are excluded"
    (let [stream (s/periodic 1 [(t/event (t/arc 0 1) 60)])
          events (s/query stream (t/arc 0 3))]
      (is (= 3 (count events)))
      (is (= [0 1 2] (starts events))))))

(deftest triplets-exact
  (let [stream (s/periodic 1 [(t/event (t/arc 0 1/3) 60)
                              (t/event (t/arc 1/3 2/3) 62)
                              (t/event (t/arc 2/3 1) 64)])
        events (s/query stream (t/arc 0 1))]
    (is (= [0 1/3 2/3] (starts events)))
    (is (every? ratio? (filter ratio? (starts events))))))

;; silence

(deftest silence-returns-empty
  (is (empty? (s/query (s/silence) (t/arc 0 4)))))

;; stack

(deftest stack-merges
  (let [a (s/periodic 1 (t/notes [60]))
        b (s/periodic 1 (t/notes [67]))
        merged (s/stack [a b])
        events (s/query merged (t/arc 0 1))]
    (is (= #{60 67} (set (values events))))))

;; cat

(deftest cat-sequences
  (let [a (s/periodic 1 (t/notes [60]))
        b (s/periodic 1 (t/notes [67]))
        events (s/query (s/cat [a b] 1) (t/arc 0 2))]
    (is (= [60 67] (values events)))
    (is (= [0 1] (starts events)))))

(deftest cat-loops
  (let [a (s/periodic 1 (t/notes [60]))
        b (s/periodic 1 (t/notes [67]))
        events (s/query (s/cat [a b] 1) (t/arc 0 4))]
    (is (= [60 67 60 67] (values events)))
    (is (= [0 1 2 3] (starts events)))))

(deftest cat-multi-event-slot
  (let [a (s/periodic 2 (t/notes [60 62]))
        b (s/periodic 2 (t/notes [67 69]))
        events (s/query (s/cat [a b] 2) (t/arc 0 4))]
    (is (= [60 62 67 69] (values events)))
    (is (= [0 1 2 3] (starts events)))))

;; shift

(deftest shift-moves-events-forward
  (let [stream (s/periodic 2 (t/notes [60 62]))
        events (s/query (s/shift stream 2) (t/arc 2 4))]
    (is (= [60 62] (values events)))
    (is (= [2 3] (starts events)))))

;; slow

(deftest slow-stretches-time
  (let [stream (s/periodic 2 (t/notes [60 62]))
        events (s/query (s/slow stream 2) (t/arc 0 4))]
    (is (= [60 62] (values events)))
    (is (= [0 2] (starts events)))))

;; fast

(deftest fast-compresses-time
  (let [stream (s/periodic 2 (t/notes [60 62]))
        events (s/query (s/fast stream 2) (t/arc 0 1))]
    (is (= [60 62] (values events)))
    (is (= [0 1/2] (starts events)))))

(deftest fast-produces-ratios
  (let [stream (s/periodic 3 (t/notes [60 62 64]))
        events (s/query (s/fast stream 3) (t/arc 0 1))]
    (is (= [60 62 64] (values events)))
    (is (= [0 1/3 2/3] (starts events)))))
