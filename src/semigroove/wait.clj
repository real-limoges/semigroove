(ns semigroove.wait
  "Polling with a deadline. Every wait in semigroove names its way out, so a
   missing device or server ends in a named outcome rather than a hang.")

(defn until
  "Call READY? every INTERVAL-MS until it returns true or TIMEOUT-MS has passed.
   Returns :ready or :timed-out."
  [ready? timeout-ms interval-ms]
  (let [deadline (+ (System/nanoTime) (* timeout-ms 1000000))]
    (loop []
      (cond
        (ready?)                          :ready
        (>= (System/nanoTime) deadline)   :timed-out
        :else                             (do (Thread/sleep (long interval-ms))
                                              (recur))))))
