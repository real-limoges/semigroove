# Semigroove

Interactive music environment. Pattern DSL + wall-clock scheduler + MIDI/Launchpad binding in **Clojure**; synthesis runs in an external `scsynth` (SuperCollider) via **Overtone**.

Successor to funktor (Haskell). The design transfers; the implementation does not.

## Stack

- **Clojure** (latest stable) — REPL is the live image; no hot-reload gymnastics.
- **Overtone** — scsynth client, `defsynth` macro, OSC, MIDI, scheduling primitives.
- **Emacs + CIDER**: `.dir-locals.el` sets the `:dev` alias, so `cider-jack-in` gets `boot!` and the arm64 JVM options.
- **scsynth** — same SuperCollider server you used with funktor.

## Boot

```bash
# Start the REPL with dev helpers
clj -A:dev
```

```clojure
;; Boot scsynth and connect Overtone (spawns scsynth at port 57110)
user=> (boot!)

;; Require the live API
user=> (require '[semigroove.live :refer [play stop set-tempo add-track]]
               '[semigroove.core.stream :as s]
               '[semigroove.core.types :as t])

;; Play a repeating arpeggio (play resets the session: beat 0, one :main track)
user=> (play (s/periodic 3 (t/notes [60 62 64])))

;; Swap the stream without restarting the transport
user=> (add-track :main (s/periodic 3 (t/notes [60 63 67])))

;; Layer a second track
user=> (add-track :bass (s/periodic 4 (t/notes [36])))

;; Adjust tempo on the fly
user=> (set-tempo 140)

;; Stop
user=> (stop)
```

> **arm64 note:** Overtone 0.16.3331 ships x86_64-only native libs. The repo includes
> `resources/darwin-aarch64/libscsynth.dylib` (an empty stub) so JNA loads without error.
> `boot!` in `dev/user.clj` spawns the real `scsynth` binary directly and connects via
> `connect-external-server` — the internal Overtone booter does not work on arm64.

No build step, no `:reload`, no foreign-store. Edit a stream, `add-track` it, hear the change.

For the toddler Launchpad, double-click `bin/toddler.command` or run `clj -M:toddler`.

## Architecture

See `docs/reference/internal/architecture.md`. Layered design ported from Funktor with Clojure idioms substituted (atoms for TVars, Overtone for hosc+PortMidi, namespaces for module hierarchy).

## Repo layout

```
src/semigroove/
  core/                           Beat / Arc / Event / Stream, transforms: pure algebra
  audio.clj                       Overtone facade (note-on/off, voice steal)
  audio/scheduler.clj             Pure scheduler step, beat cursor, lookahead window
  audio/server.clj                Spawn scsynth and connect (arm64)
  synthdefs.clj                   semigroove-note, master-limiter
  live.clj                        REPL API: play / stop / set-tempo / tracks / mixer
  harmony.clj, harmony/           Chords, scales, voicing, analysis
  generative/                     Euclidean, Markov, cellular automata
  grid.clj, grid/binding.clj      Launchpad modes: pure state, then the wiring
  hardware/                       MIDI input, Launchpad LEDs and SysEx
  toddler.clj                     One-command toddler instrument
  wait.clj                        Polling with a deadline
dev/
  user.clj                        REPL-only: (boot!) spawns scsynth + connects
docs/                             Architecture, roadmap, design notes
test/semigroove/                      clojure.test specs
```
