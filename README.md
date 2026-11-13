# TurquoiseBOT

[![CI](https://github.com/ranir2026/Lichess/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/ranir2026/Lichess/actions/workflows/ci.yml)

A chess engine written from scratch in Java: bitboard move generation, an alpha-beta search with the usual modern pruning and ordering techniques, a Texel-tuned evaluation, and a self-play testing harness that decides every change with confidence intervals and a sequential probability ratio test (SPRT).

When the bot is online, it can be challenged in blitz and rapid time controls on Lichess at: https://lichess.org/@/TurquoiseBOT.

**Author:** Rahul Rani   
**Language:** Java (compiles with `--release 8`)   
**Search:** single-threaded  
**Strength:** about +920 Elo over the first version in self-play (a rough chained estimate, see [Results](#results))

![Cumulative Elo vs IterativeDeepeningBot](docs/elo_progression.svg)


## Contents
1. [Engine at a glance](#engine-at-a-glance)
2. [Versions and naming](#versions-and-naming)
3. [Getting started](#getting-started)
4. [Continuous integration](#continuous-integration)
5. [Testing methodology](#testing-methodology)
6. [Results](#results)
7. [Version details](#version-details)
8. [Testing history and selection effects (v2.8)](#testing-history-and-selection-effects-v28)
9. [Testing history and selection effects (v2.9)](#testing-history-and-selection-effects-v29)
10. [Testing history and selection effects (v3.0)](#testing-history-and-selection-effects-v30)
11. [Testing history and selection effects (v3.1)](#testing-history-and-selection-effects-v31)
12. [Known limitations](#known-limitations)
13. [Glossary and references](#glossary-and-references)
14. [License](#license)

---

## Engine at a glance

| Area | What is implemented |
|---|---|
| Board and move generation | Bitboard board representation, precomputed attack tables, legal move generation, incremental Zobrist hashing, threefold-repetition and fifty-move detection |
| Search | Iterative deepening, alpha-beta, principal variation search (PVS), check extension, quiescence search (captures, and every evasion when in check), transposition table, null-move pruning, reverse futility pruning, late move reductions (LMR), aspiration windows, static-exchange evaluation (SEE) |
| Move ordering | Hash move, killer moves, history heuristic, counter-move heuristic, SEE |
| Evaluation | Tapered (midgame/endgame) piece-square tables including a separate king endgame table, tapered mobility, isolated pawns, passed pawns with king-proximity scaling, king safety (pawn shield, open files, king-zone attackers, attacker-material scaling). Constants are Texel-tuned |
| Testing and tooling | `PerftTest`, `EvalSymmetryTest`, `KingSafetyTest`, `RfpEquivalenceTest`, `QsEvasionTest`, `CheckExtensionTest`, `TimeCapTest`, `BenchTest`, `Bench` (fixed-depth node-count signature), `SpeedBench`, `SuiteRunner` (self-play A/B harness), `TexelTuner` (parameter tuner), a Swing GUI (`App`) |

## Versions and naming

The project is one development line (TurquoiseBot). Each benchmarked milestone is frozen as its own class and then named after its headline feature, so it can be used as a fixed opponent. Versions from 2.0 onward carry a version number (`getVersion()` in code). `TurquoiseBot.java` is always the most advanced engine: currently identical in behaviour to TimeCapBot (v3.2), and the file where the next version is developed. Besides it, the source tree keeps the latest frozen version (TimeCapBot) plus CheckExtensionBot, which `TimeCapTest` compares against, the baselines the equivalence tests compare against (QuiescenceEvasionBot for `CheckExtensionTest`, ReverseFutilityBot for `QsEvasionTest`, KingSafetyBot for `RfpEquivalenceTest`) and SEEBot (the reference `KingSafetyTest` checks KingSafetyBot against) as classes. Every earlier milestone is a git tag, so a fixed opponent can be rebuilt with `git checkout <tag>`.

| Version | Class | Date | Headline change |
|---|---|---|---|
| (none) | IterativeDeepeningBot | 2026-04-16 | Iterative deepening + alpha-beta |
| (none) | KillersAndLMRBot | 2026-04-18 | Transposition table, killers, LMR, tapered PST |
| (none) | NMPBot | 2026-04-28 | Null-move pruning, en passant and mate-score fixes |
| (none) | FirstEngine | 2026-05-08 | History/counter-move heuristics, aspiration windows |
| 2.0 | SecondEngine | 2026-06-21 | Bitboard rewrite |
| 2.1 | MobilityBot | 2026-06-23 | Tapered mobility bonuses |
| 2.5 | PVSBot | 2026-07-28 | PVS, isolated and passed pawns |
| 2.6 | TexelBot | 2026-07-29 | Texel-tuned evaluation constants |
| 2.7 | SEEBot | 2026-08-01 | Static-exchange evaluation |
| 2.8 | KingSafetyBot | 2026-10-24 | King-safety evaluation |
| 2.9 | ReverseFutilityBot | 2026-11-08 | Reverse futility pruning |
| 3.0 | QuiescenceEvasionBot | 2026-11-09 | Quiescence search of every evasion when in check |
| 3.1 | CheckExtensionBot | 2026-11-09 | Check extension: a move that gives check is searched one ply deeper |
| 3.2 | TimeCapBot | 2026-11-13 | Time management: the planned think time is capped at the time left |

The array-based engines before 2.0 were removed from the source tree and are available in git history.

**Git tags:** `v2.1-mobilitybot`, `v2.5-pvsbot`, `v2.6-texelbot`, `v2.7-seebot`, `v2.8-kingsafetybot`, `v2.9-reversefutilitybot`, `v3.0-quiescenceevasionbot`, `v3.1-checkextensionbot`, `v3.2-timecapbot`. The v2.6 and v2.7 tags share a commit, in which `TexelBot.java` and `SEEBot.java` sit side by side. The tags also preserve the move generator exactly as it was when the results below were recorded (see [Known limitations](#known-limitations)).

## Getting started

Requires a JDK (8 or newer). From the repository root:

```bash
# compile (the attack tables are loaded from the classpath, so copy the .bin next to the classes)
javac --release 8 -d bin src/*.java
cp src/precomputed_attacks.bin bin/        # Windows: copy src\precomputed_attacks.bin bin\

# correctness tests (each exits non-zero on failure; PerftTest checks 35 reference node counts)
java -cp bin PerftTest                      # perft counts + hash/make-unmake integrity sweep
java -cp bin EvalSymmetryTest               # eval(position) == eval(colour-flipped position)
java -cp bin KingSafetyTest                 # king-safety unit tests
java -cp bin RfpEquivalenceTest             # with RFP, QS evasions and the check extension off, TurquoiseBot searches exactly like KingSafetyBot
java -cp bin QsEvasionTest                  # with QS evasions and the check extension off, TurquoiseBot searches exactly like ReverseFutilityBot
java -cp bin CheckExtensionTest             # with the check extension off, TurquoiseBot searches exactly like QuiescenceEvasionBot
java -cp bin TimeCapTest                    # the planned think time never exceeds the time left; with the cap off it matches CheckExtensionBot
java -cp bin BenchTest                      # frozen engines' bench signatures must not change; exits non-zero if one does
java -cp bin Bench                          # fixed-depth node-count signature per engine; deterministic, so an unchanged signature means an unchanged search

# speed benchmark: nodes/sec at a fixed think time on 20 fixed positions
java -cp bin SpeedBench 1000 3 SEEBot KingSafetyBot

# self-play A/B match: engine A defaults to SEEBot (-Dsuite.engineA=<Class>), engine B to KingSafetyBot (-Dsuite.engineB=<Class>)
java -cp bin -Dsuite.pairs=500 -Dsuite.moveTimeMs=3500 SuiteRunner
# writes suite_results.csv (one row per game) and suite_log.csv (one row per move) to the working directory
# -Dsuite.bookSeed=N builds a different opening book (used for tuning, so the standard book is kept for confirmation runs)
# -Dsuite.failOnError=true exits non-zero if any game ends in an illegal move, an error or a timeout (used by CI)
# -Drfp.margin=N / -Drfp.maxDepth=N set TurquoiseBot's reverse futility pruning (-Drfp.margin=100000 switches it off)
# -Dqs.evasions=false switches off TurquoiseBot's quiescence check evasions (stand pat and captures only, as in v2.9)
# -Dcheck.extension=false switches off TurquoiseBot's check extension (as in v3.0)
# -Dtime.cap=false switches off TurquoiseBot's time cap (as in v3.1)
# -Dtime.overheadMs=N sets the reserve the time cap keeps back (default 50 ms)

# Texel tuner -- needs a dataset of "FEN result" lines (default positions.txt, not included); options are documented in TexelTuner.java
java -cp bin TexelTuner --data positions.txt --ks-only --params-out tuned.txt

# GUI (run from the repository root: it loads lib/pieces.png)
java -cp bin App
```

**Playing on Lichess.** The engine speaks UCI through a front end that runs under [lichess-bot](https://github.com/lichess-bot-devs/lichess-bot). The UCI front end and the lichess-bot configuration, which hold an API token, are local only and not part of this repository.

## Continuous integration

A GitHub Actions workflow (`.github/workflows/ci.yml`) runs on every push and pull request, with three jobs:
- **Correctness:** compiles with `--release 8` and runs the seven correctness tests.
- **Bench signatures:** `BenchTest` must pass. The frozen engines share `Board`, `MoveGenerator`, `Zobrist` and the transposition-table entry with the dev engine, so a change to shared code that alters a baseline's signature fails CI. The dev engine's signature is shown in the run summary.
- **Smoke match:** 4 pairs, TimeCapBot vs TurquoiseBot at 860 (about 12 ms per move), failing on any illegal move, error or timeout.

CI checks correctness and determinism, not strength. Shared runners vary in speed, so strength results come only from the SuiteRunner runs in [Results](#results).

## Testing methodology

Every strength claim below comes from `SuiteRunner`, a self-play harness in which engine B (the candidate) plays engine A (the baseline) in-process.

**Games and openings**
- 200 random quiet openings, 8 plies each (no captures or promotions), deduplicated by position hash, generated from a fixed seed (`20260726`) so every run uses the same book. `-Dsuite.bookSeed=N` builds a different book. From v2.9 on, parameters are tuned on a separate book, so the standard book is used only for confirmation runs.
- Each opening is played as a color-reversed pair -- one game with A as White, one with A as Black. A match of 500 pairs is 1000 games.
- 500 pairs cycle through 200 openings, so 100 openings are used 3 times and 100 are used twice. Repeated openings are not fully independent samples (see limitations).
- Games run one at a time. Fresh engine instances (so empty transposition tables) are created for every game.

**Time control**
- Fixed time per move. `MOVE_TIME_MS` is passed to the engine as the time left, and the engine thinks for about `MOVE_TIME_MS / 40 - 10` ms. The default `MOVE_TIME_MS = 3500` is about 80 ms per move (measured ~79 ms). `MOVE_TIME_MS = 860` is about 12 ms per move.
- Game ends: checkmate, stalemate, threefold repetition, fifty-move rule. A 400 half-move limit and a 4-minute wall-clock limit both score as draws. An illegal move loses on the spot. There is no adjudication.

**Statistics**
- Score = (W + D/2) / N. Elo = 400 · log10(score / (1 − score)).
- 95% confidence interval on Elo: from the variance of the per-pair scores (0 to 2) for v2.5 onward. Earlier results have no pair structure (see below), so they use the per-game trinomial variance.
- LOS (likelihood of superiority) = Φ((W − L) / √(W + L)), which ignores draws.
- SPRT: pentanomial GSPRT on pair scores, H0: Elo ≤ 0 vs H1: Elo ≥ 10, α = β = 0.05, so the LLR bounds are ±2.944. The harness plays the full sample (`SPRT_STOP_EARLY = false`) so the Elo estimate is precise. In that mode the final LLR is the number to quote, and a first-crossing verdict is optimistic because the bounds are checked after every pair.

**Hardware.** Intel Core Ultra 7 256V laptop.

**Two harness generations.** The opening book and colour-reversed pairs were added to `SuiteRunner` on 2026-07-27. Before that, every game started from the standard starting position and results were a plain win/loss tally. Results for every version up to and including v2.1 come from that older harness. They are labelled "legacy" below. Games from a single start position are close to duplicates of each other, so the legacy intervals are likely too narrow and the legacy Elo numbers are best read as direction and rough size, not precise values. The time control used in the legacy runs was not recorded.

## Results

All entries compare a version (B) against the previous baseline (A). "Pairs" means the opening-book harness; "legacy" means the older single-start-position harness.

| B vs A | Harness | W / L / D | Games | Score | Elo (95% CI) | LOS | SPRT |
|---|---|---|---|---|---|---|---|
| KillersAndLMRBot vs IterativeDeepeningBot | legacy | 256 / 18 / 52 | 326 | 86.5% (282/326) | +322.7 ± 45.4 | 100.0% | n/a |
| NMPBot vs KillersAndLMRBot | legacy | 534 / 278 / 188 | 1000 | 62.8% | +91.0 ± 19.9 | 100.0% | n/a |
| FirstEngine vs KillersAndLMRBot | legacy | 564 / 238 / 198 | 1000 | 66.3% | +117.6 ± 20.1 | 100.0% | n/a |
| FirstEngine vs NMPBot | legacy | 440 / 350 / 210 | 1000 | 54.5% | +31.4 ± 19.2 | 99.9% | n/a |
| SecondEngine (v2.0) vs FirstEngine | legacy | 391 / 201 / 408 | 1000 | 59.5% | +66.8 ± 16.7 | 100.0% | n/a |
| MobilityBot (v2.1) vs SecondEngine (v2.0) | legacy | 276 / 88 / 636 | 1000 | 59.4% | +66.1 ± 12.8 | 100.0% | n/a |
| PVSBot (v2.5) vs MobilityBot (v2.1) | pairs | 312 / 195 / 493 | 1000 | 55.9% (558.5/1000) | +40.8 ± 14.5 | 100.0% | LLR +6.61, H1 accepted |
| TexelBot (v2.6) vs PVSBot (v2.5) | pairs | 347 / 173 / 480 | 1000 | 58.7% (587/1000) | +61.1 ± 14.5 | 100.0% | LLR +10.72, H1 accepted |
| SEEBot (v2.7) vs TexelBot (v2.6) | pairs | 436 / 142 / 422 | 1000 | 64.7% (647/1000) | +105.3 ± 15.4 | 100.0% | LLR not recorded |
| KingSafetyBot (v2.8) vs SEEBot (v2.7), 80 ms/move | pairs | 259 / 216 / 525 | 1000 | 52.15% (521.5/1000) | +14.9 ± 13.5 | 97.6% | LLR +2.12, inconclusive |
| KingSafetyBot (v2.8) vs SEEBot (v2.7), 12 ms/move | pairs | 297 / 231 / 472 | 1000 | 53.3% (533/1000) | +23.0 ± 14.3 | 99.8% | LLR +3.41, H1 bound crossed |
| ReverseFutilityBot (v2.9) vs KingSafetyBot (v2.8), 80 ms/move | pairs | 356 / 143 / 501 | 1000 | 60.65% (606.5/1000) | +75.2 ± 14.1 | 100.0% | LLR +14.77, H1 bound crossed |
| ReverseFutilityBot (v2.9) vs KingSafetyBot (v2.8), 12 ms/move | pairs | 341 / 180 / 479 | 1000 | 58.05% (580.5/1000) | +56.4 ± 13.4 | 100.0% | LLR +11.53, H1 bound crossed |
| QuiescenceEvasionBot (v3.0) vs ReverseFutilityBot (v2.9), 80 ms/move | pairs | 287 / 202 / 511 | 1000 | 54.25% (542.5/1000) | +29.6 ± 13.6 | 100.0% | LLR +5.22, H1 bound crossed |
| QuiescenceEvasionBot (v3.0) vs ReverseFutilityBot (v2.9), 12 ms/move | pairs | 273 / 228 / 499 | 1000 | 52.25% (522.5/1000) | +15.6 ± 13.9 | 97.8% | LLR +2.14, inconclusive |
| CheckExtensionBot (v3.1) vs QuiescenceEvasionBot (v3.0), 80 ms/move | pairs | 246 / 203 / 551 | 1000 | 52.15% (521.5/1000) | +14.9 ± 13.4 | 97.9% | LLR +2.15, inconclusive |
| CheckExtensionBot (v3.1) vs QuiescenceEvasionBot (v3.0), 12 ms/move | pairs | 260 / 214 / 526 | 1000 | 52.3% (523/1000) | +16.0 ± 13.7 | 98.3% | LLR +2.27, inconclusive |

The v2.9 rows are the two confirmation runs. The 80 ms/move run includes a 6-minute standby partway through, which the owner accepted. Leaving the affected games out changes the result by at most 0.2 Elo (see [Testing history (v2.9)](#testing-history-and-selection-effects-v29)).

The v3.0 rows are its two confirmation runs. In the 12 ms/move v3.0 run both engines averaged 14.1 ms per move, against 12.6 ms in the v2.9 run at the same setting (see [Testing history (v3.0)](#testing-history-and-selection-effects-v30)).

The v3.1 rows are its two confirmation runs. Both engines averaged 13.5 ms per move in the 12 ms/move run and 78.8 ms in the 80 ms/move run (see [Testing history (v3.1)](#testing-history-and-selection-effects-v31)).

v3.2 has no row. It changes only time management, and the search is unchanged (the same bench signature as v3.1). SuiteRunner can't measure it: it passes a constant time left with no increment, so the cap never binds in a suite game. Its absolute rating is to be measured separately.

**Cumulative gain.** Chaining the consecutive gains (Killers+LMR, NMP, FirstEngine over NMP, 2.0, 2.1, 2.5, 2.6, 2.7, and 2.8, 2.9, 3.0 and 3.1 at 80 ms/move) gives about **+920 Elo ± 68** over IterativeDeepeningBot. The interval adds the individual intervals in quadrature. It assumes Elo is additive across matches and mixes the two harness generations, so it is a rough picture and not a rating. As a consistency check, FirstEngine measured directly against KillersAndLMRBot is +117.6, while chaining NMP (+91.0) and FirstEngine over NMP (+31.4) gives +122.4. The two agree well inside the intervals. `docs/make_elo_chart.py` regenerates the chart.

## Version details

### IterativeDeepeningBot (2026-04-16)
- Iterative deepening
- Alpha-beta pruning

### KillersAndLMRBot (2026-04-18)
- King endgame piece-square tables
- Tapered evaluation for piece-square tables
- Transposition tables
- Killer-move tracking
- Late move reductions (LMR)

### NMPBot (2026-04-28)
- Null-move pruning
- Fixed en passant move generation
- Fixed mate-score handling

### FirstEngine (2026-05-08)
- History heuristic
- Counter-move heuristic
- Aspiration windows
- Improved move ordering

### SecondEngine, v2.0 (2026-06-21): switch to bitboards
- Bitboard representation of the game, to be faster
- Tweaked move-ordering weightings
- Refactored the iterative-deepening loop
- Same search and evaluation features as FirstEngine. Only the representation changed.

Earlier versions of this README listed depth, node-count and speed tables for this change. Those numbers contained a bug and have been withdrawn. The head-to-head result in the table above is not affected.

### MobilityBot, v2.1 (2026-06-23)
- Mobility bonuses for bishop, rook, knight and queen
- Tapered mobility bonuses

### PVSBot, v2.5 (2026-07-28)
- Isolated-pawn penalty
- Passed-pawn evaluation (replaces the doubled-pawn penalty), with king-proximity scaling
- Principal variation search (PVS)

### TexelBot, v2.6 (2026-07-29)
- Texel-tuned evaluation constants

### SEEBot, v2.7 (2026-08-01)
- Static-exchange evaluation

### KingSafetyBot, v2.8 (2026-10-24)
- King-safety evaluation. Search, move generation and all other evaluation terms are unchanged from SEEBot v2.7.
  - Pawn shield: holes and pushed shield pawns in front of a castled (wing) king
  - Open and half-open files next to the king
  - King-zone attackers: every knight, bishop, rook or queen attacking the king zone is counted once, weighted, and fed through a nonlinear (quadratic, capped) danger table. It needs at least 2 attackers and counts much less without a queen.
  - The whole penalty scales with the opponent's remaining attacking material, so it is 0 in pawn endgames.
  - Pawn-storm term (Texel-tuned to 0, kept for future tuning)
- Only the 15 new king-safety constants were Texel-tuned: Zurichess quiet-labeled set, 1.43M positions, 80/20 train/validation split. Every other constant is SEEBot's.
- New tests: `PerftTest`, `EvalSymmetryTest`, `KingSafetyTest`, `SpeedBench`. `KingSafetyTest` also checks that, with every king-safety constant set to 0, the bot evaluates and searches identically to SEEBot.

**Where the gain comes from.** KingSafetyBot delivers more middlegame mates (42 vs 18 at 80 ms/move, 73 vs 28 at 12 ms/move) and more wins under 60 plies (47 vs 15, 61 vs 15). Endgame mates are about even (217 vs 198, 224 vs 203). Every decisive game was a checkmate. There were no illegal moves, crashes, time forfeits or move-limit games.

**Speed cost.** -3.5% nodes/second on `SpeedBench` (20 positions, 1 s/move). In the suite games, -0.8% (80 ms/move) and -2.7% (12 ms/move).

### ReverseFutilityBot, v2.9 (2026-11-08)
- Reverse futility pruning (also called static null-move pruning). At a node below the root that is searched with a null window, is not in check and has at most 6 plies left: if the static evaluation minus 90 per remaining ply is still at least beta, the node returns beta without searching any moves. The search is fail-hard, so this returns beta like every other cutoff.
- It is skipped when beta is a mate score. Mate scores are ±(100000 + depth), and the ±50 aspiration window can push a bound slightly below 100000, so any |beta| ≥ 50000 counts as a mate score.
- The static evaluation is now computed once per node (not in check) and shared with null-move pruning, which behaves exactly as before. Evaluation, move ordering and every other search feature are unchanged from KingSafetyBot v2.8.
- The margin (90 per ply) was picked from 70, 90 and 110 on a separate opening book. See [Testing history (v2.9)](#testing-history-and-selection-effects-v29).
- New test: `RfpEquivalenceTest`. With RFP switched off, TurquoiseBot gives the same root score, node count and best move as KingSafetyBot at depths 1 to 7 on the 20 benchmark positions. With RFP on, the depth-7 searches of those positions shrink from 1,046,258 to 712,648 nodes (-32%).

**Where the gain comes from.** At equal time RFP searches deeper: on average 10.16 vs 9.20 plies per move at 80 ms/move, and 7.70 vs 7.04 at 12 ms/move.

**Speed cost.** -2.5% nodes/second on `SpeedBench` (20 positions, 100 ms/move, 3 repetitions), with the average depth reached rising from 10.80 to 11.42. In the suite games, -0.2% (80 ms/move) and -1.4% (12 ms/move).

### QuiescenceEvasionBot, v3.0 (2026-11-09)
- Quiescence search when the side to move is in check. Before, quiescence always stood pat on the static evaluation and searched only captures, so a position in check at the end of the main search was scored as if the check could be ignored, and a checkmate there was never seen. Now a quiescence node in check does not stand pat: it searches every legal evasion, quiet moves included, and returns a mate score if there is none. Outside check, quiescence is unchanged (stand pat, then captures that don't lose material by SEE).
- A mate found in quiescence scores -100000, which ranks it as the most distant mate (the main search scores mates -100000 - depth, with depth at least 1).
- Nothing else changed. Evaluation, move ordering and every other search feature are as in ReverseFutilityBot v2.9 (RFP margin 90, maximum depth 6).
- New test: `QsEvasionTest`. With `-Dqs.evasions=false`, TurquoiseBot gives the same root score, node count and best move as ReverseFutilityBot at depths 1 to 7 on the 20 benchmark positions. With it on, 19 of the 20 trees change and the depth-7 searches grow from 712,648 to 884,887 nodes (+24%). Two positions check the new behaviour in a single quiescence call. After fool's mate, the old search stood pat at -64 and the new one returns mate (-100000). With White's king and queen forked by a checking knight, the old search stood pat at +648 and the new one returns -431, because it sees the queen loss.

**Where the gain comes from.** Not from depth: at equal time both versions reach about the same depth (10.17 vs 10.21 plies per move at 80 ms/move, 7.69 vs 7.75 at 12 ms/move). The only change is how positions in check at the end of the main search are scored.

**Speed cost.** No slowdown measured. Nodes/second rose by 1.3% on `SpeedBench` (20 positions, 100 ms/move, 3 repetitions), with the average depth reached falling from 11.55 to 11.30. In the suite games, nodes/second rose by 3.0% (80 ms/move) and 1.3% (12 ms/move).

### CheckExtensionBot, v3.1 (2026-11-09)
- Check extension. In the main search, a move that gives check is searched one ply deeper than other moves: the position after it keeps the parent's remaining depth instead of losing a ply. This applies to the first move's full-window search, to the null-window search of every later move and to their re-searches. Moves that give check were already exempt from late move reductions, so they are never reduced.
- Every checking move is extended, whatever it costs in material. Null-move pruning, its verification search and quiescence search are unchanged.
- No extension is made 64 or more plies from the root, so a long run of checks cannot overrun the search's per-ply arrays.
- Nothing else changed. Evaluation, move ordering and every other search feature are as in QuiescenceEvasionBot v3.0.
- New test: `CheckExtensionTest`. With `-Dcheck.extension=false`, TurquoiseBot gives the same root score, node count and best move as QuiescenceEvasionBot at depths 1 to 7 on the 20 benchmark positions. With it on, 19 of the 20 trees change and the depth-7 searches grow from 884,887 to 1,023,727 nodes (+16%). In one of three king-and-two-rooks mating positions the first mate is found at depth 8 instead of 11; in the other two it is found at depth 9 either way. A queen-versus-queen ending, where both sides have long series of checks, searches to depth 10 without error.
- `RfpEquivalenceTest` and `QsEvasionTest` now switch the check extension off, so each still tests one feature. `RfpEquivalenceTest` also keeps QS evasions off in its second part (since v3.0 it had switched them back on), so its "RFP is live" check again compares RFP on against RFP off with nothing else changed.

**Where the gain comes from.** Not from searching deeper overall. At equal time CheckExtensionBot completes fewer iterations than QuiescenceEvasionBot (9.24 vs 10.08 plies per move at 80 ms/move, 6.93 vs 7.54 at 12 ms/move), because lines with checks are searched beyond that nominal depth. The extension trades uniform depth for extra depth along checking lines.

**Speed cost.** No slowdown measured. Nodes/second rose by 2.4% on `SpeedBench` (20 positions, 100 ms/move, 3 repetitions), with the average depth reached falling from 11.13 to 10.93. In the suite games, nodes/second rose by 6.6% (80 ms/move) and 7.5% (12 ms/move).

### TimeCapBot, v3.2 (2026-11-13)
- What was wrong. The planned think time (timeLeft / 40 + increment, or 1000 + increment in the first 8 plies) was never capped at the time left. With 1.5 s left and a 2 s increment it planned 2.04 s, so it could flag. This affected the Lichess bot only, never SuiteRunner, which passes a constant time left with no increment.
- The fix. The planned think time is capped at max(1, timeLeft - 50 ms). The 50 ms reserve can be changed with `-Dtime.overheadMs` in TurquoiseBot, and `-Dtime.cap=false` switches the cap off.
- Nothing else changed. The search is identical: the bench signature is 2246792, as in v3.1.
- New test: `TimeCapTest`, in three parts. (1) On the start position and after 8 plies, over 136 cases (17 clock values, 4 increments, 2 positions), the cap changes 64 results. Every changed case is one where v3.1 planned more than timeLeft - 50. With the cap on no result exceeds max(1, timeLeft - 50), and with it off every result equals v3.1. (2) With the constant clocks SuiteRunner uses, the time formula equals v3.1. (3) At 1500 ms left with a 2000 ms increment, the cap plans 1450 ms where v3.1 plans 2037 ms. The measured search took 1441 ms, against 2028 ms for CheckExtensionBot in the same test run.

## Testing history and selection effects (v2.8)

v2.8 is the third king-safety candidate, so the headline result has to be read with that in mind. The first two used a game-phase taper instead of the attacker-material taper and did not pass. They ran mostly on battery power, where this laptop searches about 2.5x slower, so they are not exactly comparable to the v2.8 runs.

| Candidate | Time/move | W / L / D | Score | Elo (95% CI) |
|---|---|---|---|---|
| C1: phase taper, king-safety-only tuning | 80 ms | 244 / 257 / 499 | 49.35% | -4.5 [-18.2, +9.2] |
| C1 | 12 ms | 298 / 238 / 464 | 53.0% | +20.9 [+6.4, +35.4] |
| C2: C1 + full re-tune of all constants | 80 ms | 274 / 252 / 474 | 51.1% | +7.6 [-6.3, +21.6] |
| C2 | 12 ms | 255 / 267 / 478 | 49.4% | -4.2 [-18.1, +9.7] |
| **v2.8**: attacker-material taper | 80 ms | 259 / 216 / 525 | 52.15% | +14.9 [+1.5, +28.4] |
| **v2.8** | 12 ms | 297 / 231 / 472 | 53.3% | +23.0 [+8.7, +37.3] |

How to read this:
- Six runs (3 candidates x 2 time controls) were looked at before settling on v2.8. A one-sided p-value is about 0.024 for v2.8 at 80 ms/move (LOS 97.6%) and about 0.002 at 12 ms/move (LOS 99.8%). A rough Bonferroni correction across all six looks multiplies those by 6, giving about 0.15 and about 0.012. So the 80 ms/move result does not survive correction on its own, and the 12 ms/move result does.
- v2.8 is not reported as a clear success. The SPRT at 80 ms/move is inconclusive (LLR +2.12 against a bound of +2.944), and the case rests on both time controls being positive (+14.9 and +23.0).
- The `SuiteRunner` bug that printed "H0 ACCEPTED at pair 2" for the 12 ms/move run (it locked in a verdict when the first two pairs scored the same, because the variance was near zero) was fixed on 2026-11-07: no LLR is computed before 20 pairs. The final LLR (+3.41) for that run was never affected. The fix does not change any other number in this document.

## Testing history and selection effects (v2.9)

The reverse futility pruning margin was tuned on a separate opening book (seed 1 instead of 20260726), so the standard book was used only for the confirmation runs. Two rules were fixed before any run: pick the margin with the best Elo, unless all three 95% intervals overlap, in which case use 90; and call v2.9 confirmed only if both confirmation runs have a 95% interval lower bound above 0 Elo. B is TurquoiseBot with RFP, A is KingSafetyBot v2.8. All runs were on AC power apart from the disturbance described below.

| Run | Book seed | Time/move | Pairs | W / L / D | Score | Elo (95% CI) | LLR |
|---|---|---|---|---|---|---|---|
| Tuning, margin 70 | 1 | 12 ms | 200 | 142 / 82 / 176 | 57.5% | +52.5 [+30.6, +74.8] | +3.88 |
| Tuning, margin 90 | 1 | 12 ms | 200 | 119 / 78 / 203 | 55.1% | +35.7 [+14.0, +57.8] | +2.51 |
| Tuning, margin 110 | 1 | 12 ms | 200 | 113 / 87 / 200 | 53.25% | +22.6 [+1.4, +44.0] | +1.50 |
| **v2.9** confirmation, margin 90 | 20260726 | 12 ms | 500 | 341 / 180 / 479 | 58.05% | +56.4 [+43.1, +69.9] | +11.53 |
| Confirmation, first attempt (invalid: stopped) | 20260726 | 80 ms | 8 | 6 / 4 / 6 | n/a | n/a | n/a |
| **v2.9** confirmation, margin 90 | 20260726 | 80 ms | 500 | 356 / 143 / 501 | 60.65% | +75.2 [+61.2, +89.3] | +14.77 |

How to read this:
- The three tuning intervals all overlap (they share +30.6 to +44.0), so by the rule the margin is 90, even though 70 had the best point estimate. The estimates fall as the margin grows (+52.5, +35.7, +22.6), which hints that a smaller margin could be better. That was not tested on the standard book and is left for a later version.
- Because the margin was picked on a different book, the confirmation runs carry no selection effect from the margin search. Only one candidate went to confirmation. Both runs clear the decision rule easily: lower bounds +43.1 and +61.2, final LLR +11.53 and +14.77 against a bound of +2.944.
- Margin 90 scored +35.7 on the tuning book and +56.4 on the standard book at the same time control. The intervals overlap, so the gap is within sampling and book-to-book noise.
- The first 80 ms/move attempt was stopped after 8 pairs (17 games) by the tool running the match, because the computer was low on memory. It was not stopped because of its score. It was re-run once, as the protocol allows.
- **Disturbance in the 80 ms/move run.** About 19 minutes in, the laptop was unplugged for about 7 minutes to move it, which the owner accepted before it happened. It ran on battery for about 45 s and spent about 6 minutes in Modern Standby. One move (KingSafetyBot's, in game index 159 of `suite_results.csv`) was frozen mid-search for 366 s and stopped at depth 8. That game ended in a draw by repetition. After the laptop woke, background load lowered search speed to 1.46-1.67M nodes/second for games 160-189, against 1.95-2.18M for the rest of the run. Both engines were slowed equally. Leaving the affected games out changes nothing that matters: without pairs 79-82 (the standby and battery games) the result is +75.0 [+61.1, +89.3], LLR +14.62; without pairs 79-94 (every slowed game) it is +75.1 [+60.9, +89.6], LLR +14.11. Both subsets were fixed from the power log and the node rates before their scores were computed.

## Testing history and selection effects (v3.0)

The change has no parameter, so there was no tuning run and only one candidate. The decision rule was fixed before either run: v3.0 is confirmed only if both confirmation runs have a 95% interval lower bound above 0 Elo. B is TurquoiseBot with quiescence check evasions, A is ReverseFutilityBot v2.9, on the standard book. Both runs were on AC power throughout, with no sleep, standby or other Windows power events, and no timeouts, move-limit games, crashes or errors.

| Run | Book seed | Time/move | Pairs | W / L / D | Score | Elo (95% CI) | LLR |
|---|---|---|---|---|---|---|---|
| **v3.0** confirmation | 20260726 | 12 ms (measured 14.1 ms) | 500 | 273 / 228 / 499 | 52.25% | +15.6 [+1.8, +29.5] | +2.14 |
| **v3.0** confirmation | 20260726 | 80 ms | 500 | 287 / 202 / 511 | 54.25% | +29.6 [+16.1, +43.2] | +5.22 |

How to read this:
- Both runs clear the rule, so v3.0 is confirmed at both time controls: neither 95% interval includes 0 (lower bounds +1.8 and +16.1). The SPRT answers a different question, whether the gain is at least 10 Elo. It crossed that bound at 80 ms/move (final LLR +5.22) and did not reach either bound at 12 ms/move (final LLR +2.14 against +2.944).
- The 12 ms/move run was played first, and the 80 ms/move run was started after its result was known. Both runs had to pass and both are reported, so the order does not change the decision.
- In the 12 ms/move run both engines averaged 14.1 ms per move at `moveTimeMs=860`, the same setting as every 12 ms/move row, against 12.6 ms in the v2.9 confirmation run. The cause was not established. Both engines had the same time within the run, so the comparison is fair, but this run played at about 12% more time per move than the v2.9 one. The 80 ms/move run averaged 79.1 ms per move, as expected.

## Testing history and selection effects (v3.1)

The change has no parameter, so there was no tuning run and only one candidate. The decision rule was fixed before either run: v3.1 is confirmed only if both confirmation runs have a 95% interval lower bound above 0 Elo. B is TurquoiseBot with the check extension, A is QuiescenceEvasionBot v3.0, on the standard book. Both runs were on AC power throughout, with no sleep, standby or other Windows power events during either run, and no timeouts, move-limit games, crashes or errors. The laptop was in Modern Standby for about one second between the two runs, after the 12 ms/move run's last game and before the 80 ms/move run started.

| Run | Book seed | Time/move | Pairs | W / L / D | Score | Elo (95% CI) | LLR |
|---|---|---|---|---|---|---|---|
| **v3.1** confirmation | 20260726 | 12 ms (measured 13.5 ms) | 500 | 260 / 214 / 526 | 52.3% | +16.0 [+2.4, +29.7] | +2.27 |
| **v3.1** confirmation | 20260726 | 80 ms | 500 | 246 / 203 / 551 | 52.15% | +14.9 [+1.6, +28.3] | +2.15 |

How to read this:
- Both runs clear the rule, so v3.1 is confirmed at both time controls: neither 95% interval includes 0 (lower bounds +2.4 and +1.6). The SPRT, which asks whether the gain is at least 10 Elo, reached neither bound in either run (final LLR +2.27 and +2.15 against +2.944).
- Both runs were agreed before either started and ran back to back as one job, 12 ms/move first. The 80 ms/move run started automatically, so whether it ran did not depend on the first result. Both are reported.
- In the 12 ms/move run both engines averaged 13.5 ms per move at `moveTimeMs=860`, between the v2.9 run (12.6 ms) and the v3.0 run (14.1 ms) at the same setting. Both engines had the same time within the run. The 80 ms/move run averaged 78.8 ms per move.

## Known limitations

- **All Elo is relative and measured in self-play.** The engine has not been rated against external engines, so there is no absolute strength figure here. Self-play gains tend to overstate gains against other opponents.
- **Legacy results (up to v2.1) used a weaker harness** (single start position, no pairing, time control not recorded). See [Testing methodology](#testing-methodology).
- **Repeated openings.** 500 pairs use 200 openings, so games are not fully independent and the true uncertainty is somewhat larger than the reported intervals.
- **Move times are very short** (about 80 ms and 12 ms per move). Results may not carry over to longer time controls.
- **The move generator was fixed after the v2.8 results were recorded.** On 2026-11-07 a bug in en passant check evasion was fixed: when a pawn that had just advanced two squares gave check, the en passant capture of that pawn was wrongly rejected as illegal. `PerftTest` now matches all 35 reference node counts (and depth 6 for the two positions that have one), where it previously missed 81 of 674,624 nodes in one position. All results up to v2.8 were recorded with the earlier generator, which the version tags preserve, and were not re-run. The v2.9, v3.0 and v3.1 results were recorded with the fixed generator. The affected positions are rare in play.
- **Some run conditions were not recorded** for older runs (hardware, power state, and the SPRT LLR for v2.7).
- **UCI front end and Lichess setup are local only** and not included in the repository.

## Glossary and references

- **Elo difference.** Strength difference implied by a match score. 100 Elo is about a 64% score.
- **LOS.** Likelihood of superiority: the probability that B is truly stronger than A, computed from the decisive games.
- **SPRT / LLR.** Sequential probability ratio test and its log-likelihood ratio. Here it tests H0 "B gains 0 Elo or less" against H1 "B gains 10 Elo or more". An LLR above +2.944 favours H1, below -2.944 favours H0, and in between is inconclusive.
- **Pair.** Two games from the same opening with colours swapped.
- **Texel tuning.** Fitting evaluation constants by minimising the error between a sigmoid of the evaluation and game results over a set of quiet positions. See [Texel's Tuning Method](https://www.chessprogramming.org/Texel%27s_Tuning_Method). The dataset used is the Zurichess quiet-labeled position set.
- **Perft.** Counting all leaf nodes of the legal-move tree to a fixed depth and comparing with published counts. See [Perft Results](https://www.chessprogramming.org/Perft_Results).
- **SEE.** Static-exchange evaluation of a capture sequence on one square. See [Static Exchange Evaluation](https://www.chessprogramming.org/Static_Exchange_Evaluation).
- **LMR / PVS / NMP.** Late move reductions, principal variation search, null-move pruning. See the [Chess Programming Wiki](https://www.chessprogramming.org/).

## License

MIT. See [LICENSE](LICENSE).
