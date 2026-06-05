# Cantor

Interactive music environment. Pattern DSL + wall-clock scheduler + MIDI/Launchpad binding in **Clojure**; synthesis runs in an external `scsynth` (SuperCollider) via **Overtone**.

Successor to [funktor](../funktor) (Haskell). The design transfers; the implementation does not. See `docs/from-funktor.md` for what to reuse and what to leave behind.

## Stack

- **Clojure** (latest stable) — REPL is the live image; no hot-reload gymnastics.
- **Overtone** — scsynth client, `defsynth` macro, OSC, MIDI, scheduling primitives.
- **Cursive** (IntelliJ IDEA) — editor with REPL integration for interactive development.
- **scsynth** — same SuperCollider server you used with funktor.

## Boot

```bash
# 1. Start scsynth in a separate terminal
/Applications/SuperCollider.app/Contents/Resources/scsynth -u 57110
```

```clojure
# 2. In the REPL
clj
user=> (use 'overtone.core)
user=> (connect-external-server 57110)

# 3. Define an instrument
user=> (definst tone [freq 440 amp 0.5]
         (* amp (env-gen (perc 0.01 0.3) :action FREE)
            (sin-osc freq)))

# 4. Play
user=> (tone 440)
```

> **arm64 note:** Overtone 0.10.6 ships x86_64-only native libs. The repo includes
> `resources/darwin-aarch64/libscsynth.dylib` (an empty stub) so JNA loads without error.
> `connect-external-server` is the correct entry point on arm64 — the internal booter
> needs symbols from the real dylib that SC 3.11+ no longer ships.

That's it. No build step, no `:reload`, no foreign-store. Edit the file, eval the form, hear the change.

## Architecture

See `docs/architecture.md`. Layered design ported from Funktor with Clojure idioms substituted (atoms for TVars, Overtone for hosc+PortMidi, namespaces for module hierarchy).

## Repo layout

```
src/cantor/                       Clojure source
  core/                           Beat / Arc / Event / Stream primitives
  audio/                          Overtone wrapper + scheduler
  hardware/                       MIDI + Launchpad Mk3
  grid/                           Pad ↔ grid model + mode dispatcher
  live.clj                        REPL entry: play / stop / set-tempo / ...
synthdefs/                        Reference .scd files (Overtone defsynths live in src/)
docs/                             Architecture, lessons, design notes
test/cantor/                      clojure.test specs
```
