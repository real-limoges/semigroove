(ns semigroove.audio.scheduler-test
  (:require [clojure.test :refer [deftest is testing]]
            [semigroove.audio.scheduler :refer [step]]
            [semigroove.core.stream :as s]
            [semigroove.core.types :as t]
            [semigroove.audio.scheduler :as sched]))

(def base-state
  {:tempo         120
   :tracks        {}
   :muted         #{}
   :solo          #{}
   :beat          0
   :start-nanos   0
   :pending       []})

(deftest step-advances-cursor
  (let [[s' _] (step base-state 0)]
    (is (= 1/5 (:beat s')) "120 BPM * 100ms = 1/5 beat")))

(deftest step-cursor-stays-ratio
  (let [[s' _] (step base-state 0)]
    (is (ratio? (:beat s')) "cursor must remain a ratio")))

(deftest step-silence-yields-no-actions
  (let [[_ due] (step base-state 0)]
    (is (empty? due))))

(deftest step-periodic-yields-note-on-and-off
  (let [state    (assoc-in base-state
                           [:tracks :t] (s/periodic 1 (t/notes [60])))
        [s' due] (step state 0)
        all      (concat due (:pending s'))]
    (is (= 2 (count all)) "one note-on + one note-off per event")
    (is (some #(= :on  (:type %)) all))
    (is (some #(= :off (:type %)) all))))

(deftest step-drains-pending-within-lookahead
  (let [now     1000000000  ; 1 second in nanos
        pending [{:time-nanos 900000000 :type :off :note 60}   ; past
                 {:time-nanos 1050000000 :type :off :note 62}  ; within 100ms
                 {:time-nanos 2000000000 :type :off :note 64}] ; far future
        state   (assoc base-state :pending pending)
        [s' due] (step state now)]
    (is (= 2 (count due)))
    (is (= 1 (count (:pending s'))))))

(deftest step-beat-arithmetic-exact
  (testing "the cursor follows the clock and stays exact"
    (let [[final _] (reduce (fn [[s _] i] (step s (* i 10000000)))
                            [base-state nil]
                            (range 12))]
      ;; last tick at 110 ms, plus 100 ms lookahead = 210 ms = 21/50 beat at 120 bpm
      (is (= 21/50 (:beat final)))
      (is (ratio? (:beat final))))))

(deftest same-instant-does-not-advance
  (let [[s1 _] (step base-state 0)
        [s2 _] (step s1 0)]
    (is (= 1/5 (:beat s1) (:beat s2)))))

(deftest step-carries-the-control-map
  (let [stream (s/periodic 1 (t/notes [{:note 60 :gain 0.3}]))
        state  (assoc-in base-state [:tracks :t] stream)
        [_ due] (step state 0)
        note-on (first (filter #(= :on (:type %)) due))]
    (is (some? note-on) "expected a note-on action")
    (is (= 0.3 (-> note-on :controls :gain)) "a map's own :gain wins over :velocity")))

(deftest bare-pitch-becomes-full-gain-controls
  (let [state     (assoc-in base-state [:tracks :t] (s/periodic 1 (t/notes [60])))
        [s' due]  (step state 0)
        [on off]  (sort-by :time-nanos (concat due (:pending s')))]
    (is (= {:note 60 :gain 1.0} (:controls on)) "bare ints keep their old loudness")
    (is (= 60 (:note off)))))

(defn- two-track-state []
  (-> (assoc @sched/scheduler-state :beat 0 :start-nanos 0 :pending [] :muted #{} :solo #{} :tracks {})
      (sched/install-track :drums (s/periodic 1 (t/notes [36 38 36 38])))
      (sched/install-track :bass (s/periodic 1 (t/notes [28 31])))))

(deftest step-emits-from-all-tracks-tagged
  (let [[_ due] (sched/step (two-track-state) (* 100 1000000000))]
    (is (some #(= :drums (:track %)) due))
    (is (some #(= :bass (:track %)) due))
    (is (every? #{:drums :bass} (map :track due)))))

(deftest mute-silences-one-track-only
  (let [st (-> (two-track-state) (sched/mute-track :bass))
        [_ due] (sched/step st (* 1000 1000000000))]
    (is (some #(= :drums (:track %)) due))
    (is (not-any? #(= :bass (:track %)) due))))

(deftest solo-wins-over-mute
  (let [st (-> (two-track-state) (sched/solo-track :bass))]
    (is (= #{:bass} (set (keys (sched/audible-tracks st)))))))

(deftest remove-track-purges-its-pending
  (let [st  (assoc (two-track-state) :pending [{:track :bass :time-nanos 5}
                                               {:track :drums :time-nanos 6}])
        st' (sched/remove-track st :bass)]
    (is (= [:drums] (map :track (:pending st'))))
    (is (not (contains? (:tracks st') :bass)))))

;; Live input (e.g. MIDI) arrives pre-built in :pending on a track that owns no
;; stream, so it bypasses audible-tracks. step must still gate it by the mixer.

(deftest mute-drops-live-note-ons-but-keeps-note-offs
  (let [now   1000000000
        state (assoc base-state
                     :muted #{:midi}
                     :pending [{:time-nanos 500000000 :type :on  :track :midi :controls {:note 60 :gain 0.8}}
                               {:time-nanos 500000000 :type :off :track :midi :note 60}])
        [_ due] (step state now)]
    (is (not-any? #(= :on (:type %)) due) "muted track's note-ons are dropped")
    (is (some #(and (= :off (:type %)) (= :midi (:track %))) due)
        "note-offs still fire so a held voice never hangs")))

(deftest solo-elsewhere-silences-live-input
  (let [now   1000000000
        state (assoc base-state
                     :solo #{:bass}
                     :pending [{:time-nanos 500000000 :type :on :track :midi :controls {:note 60 :gain 0.8}}])
        [_ due] (step state now)]
    (is (empty? due) "with a solo on another track, un-soloed live input is silent")))

(deftest unmuted-unsoloed-live-input-sounds
  (let [now   1000000000
        state (assoc base-state
                     :pending [{:time-nanos 500000000 :type :on :track :midi :controls {:note 60 :gain 0.8}}])
        [_ due] (step state now)]
    (is (= 1 (count due)) "a live note on an unmuted track with no solo sounds")))

(deftest install-does-not-touch-the-clock
  (let [st (assoc (two-track-state) :beat 17 :start-nanos 999)
        st' (sched/install-track st :pad (s/silence))]
    (is (= 17 (:beat st')))
    (is (= 999 (:start-nanos st')))))

;; Window fix (audit B3): drive step with a fake clock, 10 ms per tick.

(def ^:private tick-nanos (* 10 1000000))

(defn- run-ticks
  "Step STATE through N ticks starting at tick FROM. Returns [state fired]."
  [state from n]
  (reduce (fn [[st fired] i]
            (let [[st' due] (sched/step st (* i tick-nanos))]
              [st' (into fired due)]))
          [state []]
          (range from (+ from n))))

(def ^:private four-notes
  (assoc base-state :tracks {:main (s/periodic 4 (t/notes [60 62 64 65]))}))

(deftest nanos->beat-inverts-the-clock
  (is (= 1 (sched/nanos->beat 0 120 500000000)) "half a second at 120 bpm is one beat"))

(deftest cursor-tracks-wall-clock
  (let [[st _] (run-ticks four-notes 0 6000)                    ;; 60 s
        wall   (sched/nanos->beat 0 120 (* 6000 tick-nanos))]
    (is (>= (:beat st) wall))
    (is (<= (- (:beat st) wall)
            (sched/nanos->beat 0 120 (+ sched/lookahead-nanos tick-nanos))))
    (is (< (count (:pending st)) 10) "pending stays bounded")
    (is (or (ratio? (:beat st)) (integer? (:beat st))) "beats stay exact")))

(deftest every-note-fires-once-in-order
  (let [[_ fired] (run-ticks four-notes 0 800)                  ;; 8 s = 16 beats
        ons       (filter #(= :on (:type %)) fired)]
    (is (apply distinct? (map :time-nanos ons)) "no note is sent twice")
    ;; The last tick (7.99 s) rightly sends the beat-16 note early, so stop at 8 s.
    (is (= (take 16 (cycle [60 62 64 65]))
           (map #(-> % :controls :note) (filter #(< (:time-nanos %) 8000000000) ons))))))

(deftest added-track-sounds-within-a-lookahead
  (let [[st _]    (run-ticks four-notes 0 6000)
        st        (sched/install-track st :bass (s/periodic 1 (t/notes [36])))
        [_ fired] (run-ticks st 6000 60)]                        ;; the next 600 ms
    (is (some #(= :bass (:track %)) fired))))

(deftest retempo-keeps-the-present
  (let [now  (* 7 1000000000)
        beat (sched/nanos->beat 0 120 now)
        st   (sched/retempo four-notes 90 now)]
    (is (= 90 (:tempo st)))
    (is (< (Math/abs (- (double (sched/nanos->beat (:start-nanos st) 90 now))
                        (double beat)))
           1e-6))))
