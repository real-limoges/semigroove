# Knowledge transfer from Funktor

Funktor (Haskell) mostly worked. The pattern algebra, the scheduler design, and the scsynth boundary were good ideas and transfer directly. The Haskell-specific plumbing was tax — don't port it.

Read this once, then use the funktor repo as **reference only**. Do not translate file-by-file: that's how you end up writing Haskell-in-Clojure and reproducing the exact friction this rewrite exists to escape.

## Reuse verbatim (design)

- **`Beat = Rational`** — exact triplets, no float drift. Clojure has native `Ratio`; even better than Haskell.
- **`Stream = Arc -> [Event]`** — query function model. Not a lazy seq. The scheduler asks for events in a window; the stream returns them.
- **scsynth as synthesis boundary** — Cantor does scheduling, scsynth does sound. Overtone manages the connection so you don't even write the OSC layer.
- **Smart constructors `periodic` / `cat` / `stack` / `slow` / `fast`** — port the algebra. It's the genuinely hard, genuinely good part of Funktor.
- **Pitch → active-node tracking** for `note-off` lookup. Possibly delegate to Overtone's node objects instead of maintaining the map yourself; decide during implementation.
- **Out-of-band MIDI routing** — input thread drains MIDI onto a queue; router moves it into scheduler state. Don't let MIDI block the audio tick.
- **Monotonic clock** for event timing.
- **Pad ↔ grid model** for Launchpad. `cantor.grid` should stay pure data (pad position, color, action); `cantor.hardware.launchpad` does the SysEx.
- **Mode dispatcher (`:sequencer` / `:instrument` / `:scene`)** for grid bindings.
- **SynthDef in `synthdefs/funktor.scd`** — copy it over as starting point, then *rewrite as Overtone `defsynth`* so it lives alongside the rest of the Clojure source and gets recompiled on eval.

## Reuse with adaptation (idiom)

| Funktor (Haskell)              | Cantor (Clojure)                          |
|--------------------------------|-------------------------------------------|
| `TVar SchedulerState`          | `(atom {...})` — single-writer, simpler   |
| `STM TQueue`                   | `core.async/chan` or `LinkedBlockingQueue`|
| `forkIO`                       | `(future ...)` or `(Thread. ...)`         |
| `hosc` OSC client              | Overtone's built-in OSC                   |
| `PortMidi` (vendored fork!)    | `overtone.midi` — wraps javax.sound.midi  |
| `data Stream a = Stream (Arc -> [Event a])` | `(fn [arc] ...)` — just a function |
| `Note { pitch, velocity }` records | maps `{:pitch p :velocity v}`         |
| `Funktor.Audio.Timbre` record  | An Overtone synth value + param map       |
| `tasty` test framework         | `clojure.test` (built-in)                 |
| `fourmolu` + `hlint` + `-Werror` | `clj-kondo` (optional, run when you care) |

## Delete from your mental model

These existed only to work around Haskell/GHC constraints. They have no Cantor equivalent.

- **`foreign-store` slot 0 for global session state.** Clojure vars survive REPL evaluations natively. Just `(def live-state (atom nil))` and re-evaluate at will.
- **`fsnotify` file watcher (`Funktor.Live.Reload`).** No reload step exists in Clojure. Edit, eval form, done.
- **`Funktor.Audio.Timbre` as a distinct type.** Overtone synths are values; a timbre is just `[synth-var param-map]`.
- **Cabal flag gating for MIDI (`midi` flag).** No equivalent. MIDI is always available via `overtone.midi`.
- **`-Wall` + `-Werror` + `hlint` end-of-session ritual.** Optional in Clojure. Run `clj-kondo` if you want; don't gate commits on it for a solo project.
- **Per-module coverage thresholds (`scripts/check-coverage.sh`).** Skip unless coverage becomes a real concern.
- **Stub-vs-implement discipline (`feedback_stub_not_implement`).** Less relevant — the REPL gives you instant feedback so half-built things break loudly the moment you eval them.
- **`docs/state-and-roadmap.md`-style tier tracking.** You can rebuild faster than you can re-plan. Start with `play (periodic 1 60)` working end-to-end and grow from there.

## What hurt in Funktor — and why it won't hurt here

1. **GHCi `:reload` losing scheduler state** → solved by Clojure's REPL model. The JVM stays up; vars get redefined in place.
2. **STM ceremony for single-writer state** → atoms are simpler and sufficient.
3. **PortMidi vendored fork + cabal flag** → Overtone handles MIDI out of the box.
4. **`hosc` + manual OSC encoding** → Overtone hides the wire format.
5. **SynthDef in a separate `.scd` file that must be loaded manually in the SC IDE before audio works** → Overtone `defsynth` compiles & ships at eval-time. No separate boot step.
6. **Type-driven refactoring slowing iteration** → dynamic types let you reshape data structures mid-session.

## Build order (suggested)

Each step ends with something audible.

1. `cantor.core.types` + `cantor.core.stream` — Beat, Arc, Event, `periodic`. Test with `clojure.test`.
2. **The algebra** — `cat` / `stack` / `slow` / `fast` and the arc transforms behind them (fractional cycles, querying a stretched/shifted window). Pure, no sound, fully unit-tested. This is the genuinely hard part and the place where being pure-and-testable pays off most; nail it before scsynth is in the picture so timing bugs can't hide behind audio.
3. Overtone boot + a simple `definst` — confirm scsynth round-trip.
4. `cantor.audio` thin facade: `(play-stream stream)` → schedule events → spawn synths. Sends timestamped bundles ahead by latency `L`, not just-in-time.
5. Hot-swap: re-evaluate a stream and re-`play`/commit to `swap!` the new value into `scheduler-state` — the running scheduler reads the stream from the atom each tick, so playback changes without stopping. (It does *not* deref a stream var.)
6. `cantor.hardware.midi` — MIDI keyboard plays the current timbre.
7. `cantor.hardware.launchpad` — Mk3 boots into programmer mode, pads light up.
8. `cantor.grid.binding` — Sequencer mode: pads toggle steps in a grid; commits `swap!` the new stream into `scheduler-state`.
9. `cantor.live` — REPL helpers (`play`, `stop`, `set-tempo`, ...).

Each of these was a Funktor "tier" too. Use the funktor repo's git log if you want to see how the original split landed, but plan to skip several intermediate steps that existed only to manage Haskell complexity.
