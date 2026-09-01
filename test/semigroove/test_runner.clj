(ns semigroove.test-runner
  "Exec-fn wrapper around cognitect.test-runner that forces JVM exit.

  Why this exists: any test namespace that transitively requires `overtone.core`
  (semigroove.audio / semigroove.live / semigroove.hardware.midi) starts non-daemon
  background threads. `clj -X:test` invokes `cognitect.test-runner.api/test`,
  which RETURNS a summary rather than calling System/exit, so the JVM then waits
  forever on those threads — the tests pass but the process hangs until killed.
  Routing the alias through this wrapper makes `-X:test` exit like `-M:test`.

  CI (`:test-ci`) drops Overtone entirely and routes through `run-ci`, which
  auto-discovers test namespaces and skips the Overtone-dependent denylist."
  (:require [cognitect.test-runner.api :as api]
            [clojure.tools.namespace.find :as find]
            [clojure.java.io :as io]))

(defn run
  "Run the cognitect test-runner with OPTS, then exit with a status code so a
  lingering Overtone thread can't keep the JVM alive. System/exit (not halt) so
  the scsynth-killing shutdown hook still runs when a server was booted."
  [opts]
  (let [{:keys [fail error] :or {fail 0 error 0}} (api/test opts)]
    (shutdown-agents)
    (System/exit (if (pos? (+ fail error)) 1 0))))

(def ^:private overtone-test-nses
  "Test namespaces that transitively require `overtone.core`, whose x86_64
  native libs crash at JVM class-init on arm64 (the reason `:test-ci` drops the
  Overtone dep). They cannot even be LOADED under `:test-ci`, so CI must skip
  them. When you add a new audio/midi/launchpad test, add its ns here too —
  if you forget, CI fails loudly at load rather than silently not running it."
  '#{semigroove.audio-test
     semigroove.hardware.launchpad-test
     semigroove.hardware.midi-test})

(defn run-ci
  "Discover every test namespace under `test/`, drop the Overtone-dependent ones
  (see `overtone-test-nses`), and run the rest. New pure-algebra test namespaces
  are picked up automatically — no `:dirs` allowlist to maintain by hand.

  Discovery is static (parses ns forms without loading), so the skipped
  namespaces are never required and never trigger the arm64 class-init crash."
  [_]
  (let [nses (->> (find/find-namespaces-in-dir (io/file "test"))
                  (remove overtone-test-nses)
                  vec)
        {:keys [fail error] :or {fail 0 error 0}} (api/test {:nses nses})]
    (shutdown-agents)
    (System/exit (if (pos? (+ fail error)) 1 0))))
