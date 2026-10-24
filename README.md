# TurquoiseBOT
A classical chess engine written with Java. --RR

---

# Engine Progression

## IterativeDeepeningBot (4/16/2026)

### Features
- Iterative Deepening
- Alpha-Beta Pruning

---

## KillersAndLMRBot (4/18/2026)

### New Additions
- King Endgame Piece-Square Tables
- Tapered Evaluation for Piece-Square Tables
- Transposition Tables
- Killer Move Tracking
- Late Move Reductions (LMR)

### Match Suite (vs IterativeDeepeningBot)

| Result | Count |
|---|---|
| Wins | 256 |
| Losses | 18 |
| Draws | 52 |

### Statistics

- One-sample z-test for a proportion
  - H0: p = 0.5 // Ha: p > 0.5 // α = 0.001
  - z = 14.38 // p-value ≈ 0
- LoS ≈ 1.0
- Elo Change: **+322.7 ± 63.1**

---

## NMPBot (4/28/2026)

### New Additions
- Null Move Pruning
- Fixed En Passant Move Generation
- Fixed Mate Score Handling

### Match Suite (vs KillersAndLMRBot)

| Result | Count |
|---|---|
| Wins | 534 |
| Losses | 278 |
| Draws | 188 |

### Statistics

- One-sample z-test for a proportion
  - H0: p = 0.5 // Ha: p > 0.5 // α = 0.01
  - z = 8.01 // p-value = 5.9e-16
- LoS ≈ 1.0
- Elo Change: **+88.9 ± 25.6**

---

## FirstEngine (5/8/2026)

### New Additions
- History Heuristics
- Counter Move Heuristic
- Aspiration Windows
- Improved Move Ordering

### Match Suite (vs KillersAndLMRBot)

| Result | Count |
|---|---|
| Wins | 564 |
| Losses | 238 |
| Draws | 198 |

### Statistics

- One-sample z-test for a proportion
  - H0: p = 0.5 // Ha: p > 0.5 //α = 0.01
  - z = 10.31 // p-value = 3.2e-25
- LoS ≈ 1.0
- Elo Change: **+117.7 ± 24.9**

---

## FirstEngine vs NMPBot

| Result | Count |
|---|---|
| Wins | 440 |
| Losses | 350 |
| Draws | 210 |

### Statistics

- One-sample z-test for a proportion
  - H0: p = 0.5 // Ha: p > 0.5 // α = 0.01
  - z = 2.85 // p-value = 0.0022
- LoS ≈ 0.998
- Elo Change: **+31.3 ± 24.8**

## BIG UPDATE - 6/21/2026 - SWITCH TO BITBOARDS

## SecondEngine (6/21/2026)
### New additions
- Created a Bitboard representation of the game to be significantly faster
- Tweaked move ordering weightings
- Refactored the iterative deepening loop
- SecondEngine has the same search and eval features as FirstEngine, with only a different representation

### Metrics
#### Efficiency

| engine | avgDepth | avgNodes | avgTimeMs | avgNPS | depthPerMillionNodes |
|---|---|---|---|---|---|
| FirstEngine | 8.716723 | 33269.263662 | 64.142836 | 506413.256307 | 262.005269 |
| SecondEngine | 15.149731 | 60037.730879 | 55.831635 | 763071.677728 | 252.336828 |

#### Phase Analysis

| engine | phase | depthReached | elapsedMs | nodesPerSecond | nodesSearched |
|---|---|---|---|---|---|
| FirstEngine | Endgame | 10.720743 | 66.178086 | 5.118786e+05 | 35247.893885 |
| FirstEngine | Middlegame | 5.289069 | 68.810717 | 5.091503e+05 | 34233.412133 |
| FirstEngine | Opening | 4.128981 | 43.463148 | 4.701433e+05 | 20190.946625 |
| SecondEngine | Endgame | 19.159032 | 44.171355 | 6.396110e+05 | 49971.091724 |
| SecondEngine | Middlegame | 8.089761 | 79.000153 | 1.057087e+06 | 83852.863449 |
| SecondEngine | Opening | 6.326858 | 76.306729 | 8.840717e+05 | 70214.594332 |

### SecondEngine vs FirstEngine
| Result | Count |
|---|---|
| Wins | 391 |
| Losses | 201 |
| Draws | 408 |

### Statistics
  - One-sample z-test for a proportion
    - H0: p=0.5 // Ha: p>0.5 // α = 0.01
    - z = 7.80 // p-value = 3.1e-15
  - LoS ~= 1.0
  - ELO Change: **+88.8 ± 19.9**

  ## SecondEngine v2.1 (6/23/2026)

  ### New Additions
  - Mobility bonuses for Bishop, Rook, Knight, Queen
  - Tapered mobility bonuses

  ## SecondEngine v2.1 vs SecondEngine v2.0
| Result | Count |
|---|---|
| Wins | 276 |
| Losses | 88 |
| Draws | 636 |

  ### Statistics
  - Wins: 276 | Losses: 88 | Draws: 636
  - One-sample z-test for a proportion
    H0: p = 0.5 // Ha: p > 0.5 // α = 0.01
    z = 5.95 // p-value = 1.4e-9
  - LoS ~= 1.0
  - Elo Change: **+66.0 ± 22.0**

---

## TurquoiseBot v2.5 (7/28/2026)

### New Additions
- Isolated Pawn Penalty
- Passed Pawn Evaluation (replaces doubled-pawn penalty), with king-proximity/tropism scaling
- Principal Variation Search (PVS)

### Match Suite (vs MobilityBot v2.1)

| Result | Count |
|---|---|
| Wins | 312 |
| Losses | 195 |
| Draws | 493 |

### Statistics
- Total games: 1000 (500 pairs)
- Score: 55.9% (558.5/1000)
- SPRT: H0 elo<=0.0 vs H1 elo>=10.0 (α=0.05, β=0.05) -- LLR +6.61, verdict: H1 ACCEPTED (B is stronger)
- LOS ≈ 100.0%
- Elo Change: **+40.8 ± 14.5**

---

## TurquoiseBot v2.6 (7/29/2026)

### New Additions
- Texel-tuned evaluation constants

### Match Suite (vs PVSBot v2.5)

| Result | Count |
|---|---|
| Wins | 347 |
| Losses | 173 |
| Draws | 480 |

### Statistics
- Total games: 1000 (500 pairs)
- Score: 70.8%
- SPRT: H0 elo<=0.0 vs H1 elo>=10.0 (α=0.05, β=0.05) -- LLR +10.72
- LOS ≈ 100.0%
- Elo Change: **+61.1 ± 14.5**

---

## SEEBot (TurquoiseBot v2.7) (8/1/2026)

### New Additions
- Static-Exchange Evaluation

### Match Suite (vs TexelBot v2.6)

| Result | Count |
|---|---|
| Wins | 436 |
| Losses | 142 |
| Draws | 422 |

### Statistics
- Total games: 1000
- Score: 75.4%
- SPRT: H0 elo<=0.0 vs H1 elo>=10.0 (α=0.05, β=0.05)
- LOS ≈ 100.0%
- Elo Change: **++105.3 ± 15.4**

---

## KingSafetyBot (TurquoiseBot v2.8) (10/24/2026)

### New Additions
- King safety evaluation (search, move generation and all other evaluation terms are unchanged from SEEBot v2.7)
  - Pawn shield: holes and pushed shield pawns in front of a castled (wing) king
  - Open and half-open files next to the king
  - King-zone attackers: every knight/bishop/rook/queen attacking the king zone is counted once, weighted, and fed through a nonlinear (quadratic, capped) danger table. Needs at least 2 attackers, and counts much less without a queen
  - The whole penalty scales with the opponent's remaining attacking material, so it is 0 in pawn endgames
  - Pawn-storm term (Texel-tuned to 0, kept for future tuning)
- Only the 15 new king-safety constants were Texel-tuned (Zurichess quiet-labeled set, 1.43M positions, 80/20 train/validation split). Every other constant is SEEBot's.
- New tests: `PerftTest`, `EvalSymmetryTest`, `KingSafetyTest`, `SpeedBench`

### Match Suite (vs SEEBot v2.7)

| Result | Count |
|---|---|
| Wins | 259 |
| Losses | 216 |
| Draws | 525 |

### Statistics
- Total games: 1000 (500 pairs), MOVE_TIME_MS = 3500 (~79 ms/move actually used), on AC power
- Score: 52.15% (521.5/1000)
- SPRT: H0 elo<=0.0 vs H1 elo>=10.0 (α=0.05, β=0.05) -- LLR +2.12, verdict: inconclusive (no bound reached in 500 pairs)
- LOS ≈ 97.6%
- Elo Change: **+14.9 ± 13.5** (95% CI [+1.5, +28.4])

### Short Time Control Check (MOVE_TIME_MS = 860, ~12 ms/move actually used)

| Result | Count |
|---|---|
| Wins | 297 |
| Losses | 231 |
| Draws | 472 |

- Score: 53.3% (533/1000), LOS ≈ 99.8%
- SPRT: LLR +3.41, above the H1 bound (first crossed at pair 326). SuiteRunner prints "H0 ACCEPTED at pair 2" for this run. That is a bug in its SPRT, which locks in a verdict when the first two pairs score the same (near-zero variance). The final LLR is the number to trust.
- Elo Change: **+23.0 ± 14.3** (95% CI [+8.7, +37.3])

### Notes
- Where the gain comes from: KingSafetyBot delivers more middlegame mates (42 vs 18 at 3500, 73 vs 28 at 860) and more wins under 60 plies (47 vs 15, 61 vs 15). Endgame mates are about even (217 vs 198, 224 vs 203). Every decisive game was a checkmate. There were no illegal moves, crashes, time forfeits or move-limit games.
- Speed: -3.5% nodes/second on `SpeedBench` (20 positions, 1 s/move). In the suite games, -0.8% (3500) and -2.7% (860).
- v2.8 is the third king-safety candidate. The first two used a game-phase taper instead of the attacker-material taper and did not pass. They ran mostly on battery power, where this laptop searches ~2.5x slower, so they are not exactly comparable:

| Candidate | MOVE_TIME_MS | W / L / D | Score | Elo (95% CI) |
|---|---|---|---|---|
| C1: phase taper, KS-only tuning | 3500 | 244 / 257 / 499 | 49.35% | -4.5 [-18.2, +9.2] |
| C1 | 860 | 298 / 238 / 464 | 53.0% | +20.9 [+6.4, +35.4] |
| C2: C1 + full re-tune of all constants | 3500 | 274 / 252 / 474 | 51.1% | +7.6 [-6.3, +21.6] |
| C2 | 860 | 255 / 267 / 478 | 49.4% | -4.2 [-18.1, +9.7] |

- The score % listed above for v2.6 (70.8%) and v2.7 (75.4%) does not match (W + D/2) / N. Their W/L/D give 58.7% and 64.7%. 75.4% is W / (W + L).