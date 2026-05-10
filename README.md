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