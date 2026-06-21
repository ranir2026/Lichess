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