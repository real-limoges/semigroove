# Semigroove

Interactive music environment. Pattern DSL + wall-clock scheduler + MIDI/Launchpad binding in **Clojure**; synthesis runs in an external `scsynth` (SuperCollider) via **Overtone**.

Successor to [funktor](../funktor) (Haskell). The design transfers; the implementation does not. See `docs/from-funktor.md` for what to reuse and what to leave behind.

## Stack

- **Clojure** (latest stable) — REPL is the live image; no hot-reload gymnastics.
- **Overtone** — scsynth client, `defsynth` macro, OSC, MIDI, scheduling primitives.
- **Cursive** (IntelliJ IDEA) — editor with REPL integration for interactive development.
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
user=> (require '[semigroove.live :refer [play stop set-tempo]]
               '[semigroove.core.stream :as s]
               '[semigroove.core.types :as t])

;; Play a repeating arpeggio
user=> (play (s/periodic 1 (t/notes [60 62 64])))

;; Hot-swap the stream — no stop/restart needed
user=> (play (s/periodic 1 (t/notes [60 63 67])))

;; Adjust tempo on the fly
user=> (set-tempo 140)

;; Stop
user=> (stop)
```

> **arm64 note:** Overtone 0.16.3331 ships x86_64-only native libs. The repo includes
> `resources/darwin-aarch64/libscsynth.dylib` (an empty stub) so JNA loads without error.
> `boot!` in `dev/user.clj` spawns the real `scsynth` binary directly and connects via
> `connect-external-server` — the internal Overtone booter does not work on arm64.

No build step, no `:reload`, no foreign-store. Edit a stream, re-`play` it, hear the change.

## Architecture

See `docs/architecture.md`. Layered design ported from Funktor with Clojure idioms substituted (atoms for TVars, Overtone for hosc+PortMidi, namespaces for module hierarchy).

## Repo layout

```
src/semigroove/
  core/                           Beat / Arc / Event / Stream — pure algebra
  audio/                          Overtone facade (note-on/off, voice steal)
  audio/scheduler.clj             Tick loop, beat cursor, lookahead window
  synthdefs.clj                   semigroove-note defsynth (ADSR, wave select, LPF)
  live.clj                        REPL API: play / stop / set-tempo
dev/
  user.clj                        REPL-only: (boot!) spawns scsynth + connects
docs/                             Architecture, roadmap, design notes
test/semigroove/                      clojure.test specs
```
