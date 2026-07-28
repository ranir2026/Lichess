import java.util.*;
import java.util.concurrent.*;
import java.io.*;

/**
 * Self-play suite for A/B-testing engine changes.
 *
 * This harness answers ONE question: "is engine B actually stronger than engine A?"
 * To answer it reliably we need (a) games that are independent samples and (b) a real
 * statistical test. The previous version failed both -- every game started from the
 * identical position (so games only differed by timing jitter, making them near-duplicates)
 * and the win/loss result was only printed, never saved or tested for significance.
 *
 * What this version does:
 *   1. OPENING DIVERSITY. We build a book of short, quiet, RANDOM openings (no captures or
 *      promotions, so material stays equal and positions stay roughly balanced). Each opening
 *      is played as a COLOR-REVERSED PAIR: game 1 has A as white, game 2 has A as black, both
 *      from the same opening. Pairing cancels any first-move / opening imbalance.
 *   2. OUTCOME CAPTURE. Every game's result (win/loss/draw + reason) is written to
 *      suite_results.csv, and a summary is printed at the end.
 *   3. REAL STATISTICS. We report the Elo difference with a 95% confidence interval, the LOS
 *      (likelihood B is superior), and run a pentanomial SPRT that can stop early with a
 *      definitive verdict once it has enough evidence.
 *
 * The per-move efficiency log (suite_log.csv) is kept exactly as before so Suite.py etc. still work.
 */
public class SuiteRunner {

    // ------------------------------------------------------------------ config
    private static final int    NUM_PAIRS    = 500;   // total games = NUM_PAIRS * 2 (each opening played both colors)
    private static final int    MOVE_TIME_MS = 3500;  // ms per move per engine
    private static final int    MOVE_LIMIT   = 400;   // half-move ceiling -> draw
    private static final String LOG_PATH     = "suite_log.csv";      // per-move efficiency stats (unchanged schema)
    private static final String RESULTS_PATH = "suite_results.csv";  // per-game outcomes (new)

    // opening book: quiet random openings keep material equal so games stay balanced but diverse
    private static final int    OPENING_PLIES = 8;          // depth of the random opening (must be even -> white to move after)
    private static final int    NUM_OPENINGS  = 200;        // distinct openings to cycle through
    private static final long   BOOK_SEED     = 20260726L;  // fixed seed -> reproducible book across runs

    // SPRT: sequential test that stops as soon as it can accept H0 or H1.
    // H0 "B is not an improvement" (elo <= ELO0)  vs  H1 "B is a real gain" (elo >= ELO1).
    private static final boolean SPRT_ENABLED = true;
    // If true, the run stops the moment SPRT reaches a verdict -- fast, but the final Elo
    // estimate is only as precise as whatever sample size the bound happened to cross at.
    // If false, SPRT LLR/verdict are still computed and reported every pair, but the run
    // always plays out all NUM_PAIRS -- use this when you want a precise Elo MAGNITUDE
    // (tighter confidence interval) rather than just a fast yes/no on direction.
    private static final boolean SPRT_STOP_EARLY = false;
    private static final double  SPRT_ELO0    = 0.0;   // null hypothesis bound
    private static final double  SPRT_ELO1    = 10.0;  // alternative hypothesis bound
    private static final double  SPRT_ALPHA   = 0.05;  // false-positive rate
    private static final double  SPRT_BETA    = 0.05;  // false-negative rate

    public static String ENGINE_A_NAME = "A";
    public static String ENGINE_B_NAME = "B";

    private static Engine makeEngineA(Board b) { return new PVSBot(b); }
    private static Engine makeEngineB(Board b) { return new TurquoiseBot(b); } // <<< swap here

    // ------------------------------------------------------------------ main
    public static void main(String[] args) {
        int aWins = 0, bWins = 0, draws = 0, timeoutDraws = 0, limitDraws = 0;

        long aTotalNodes = 0, bTotalNodes = 0;
        long aTotalMs    = 0, bTotalMs    = 0;
        long aTotalMoves = 0, bTotalMoves = 0;
        int  aMaxDepth   = 0, bMaxDepth   = 0;

        // pentanomial pair scores (from B's perspective): each completed pair contributes a
        // value in {0, 0.5, 1, 1.5, 2}. Kept in a list so we can compute variance for CI + SPRT.
        List<Double> pairScores = new ArrayList<>();

        // build the opening book once up front
        System.out.println("Building opening book (" + NUM_OPENINGS + " openings, " + OPENING_PLIES + " plies each)...");
        List<int[]> book = buildOpeningBook(NUM_OPENINGS, OPENING_PLIES, BOOK_SEED);
        System.out.println("Book ready: " + book.size() + " unique openings.\n");

        int totalGames = NUM_PAIRS * 2;
        System.out.println("=== Engine Self-Play Suite ===");
        System.out.printf("Pairs: %d (=%d games)  |  Time/move: %d ms  |  Move limit: %d half-moves%n",
                NUM_PAIRS, totalGames, MOVE_TIME_MS, MOVE_LIMIT);
        System.out.println("Per-move stats -> " + LOG_PATH + "   |   Per-game results -> " + RESULTS_PATH);
        if (SPRT_ENABLED) {
            System.out.printf("SPRT: H0 elo<=%.1f vs H1 elo>=%.1f  (alpha=%.2f, beta=%.2f)%n",
                    SPRT_ELO0, SPRT_ELO1, SPRT_ALPHA, SPRT_BETA);
        }
        System.out.println("------------------------------------------------------------");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        String sprtVerdict = null; // set if SPRT stops early

        try (BufferedWriter log = new BufferedWriter(new FileWriter(LOG_PATH));
             BufferedWriter results = new BufferedWriter(new FileWriter(RESULTS_PATH))) {

            log.write("game,halfMove,engineName,side,depthReached,nodesSearched,elapsedMs,nodesPerSecond");
            log.newLine();
            results.write("game,pair,openingIndex,aColor,outcome,reason,plies,bScore");
            results.newLine();

            pairsLoop:
            for (int p = 0; p < NUM_PAIRS; p++) {
                int[] opening = book.get(p % book.size());
                double pairScore = 0.0;

                // two games per pair: A as white, then A as black, same opening
                for (int side = 0; side < 2; side++) {
                    int g = p * 2 + side;
                    boolean aIsWhite = (side == 0);

                    Board boardA = freshBoard();
                    Board boardB = freshBoard();
                    applyOpening(boardA, opening);
                    applyOpening(boardB, opening);

                    Engine engineA = makeEngineA(boardA);
                    Engine engineB = makeEngineB(boardB);

                    ENGINE_A_NAME = engineA.getName() + " v" + engineA.getVersion();
                    ENGINE_B_NAME = engineB.getName() + " v" + engineB.getVersion();

                    final int gameNum = g;
                    final boolean aWht = aIsWhite;
                    final int openingLen = opening.length;

                    MatchResult mr = new MatchResult();
                    Future<?> future = executor.submit(() ->
                            playGame(boardA, boardB, engineA, engineB, aWht, gameNum, openingLen, log, mr));

                    try {
                        future.get(4, TimeUnit.MINUTES);
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        mr.outcome = "DRAW";
                        mr.reason  = "TIMEOUT";
                    } catch (Exception e) {
                        mr.outcome = "DRAW";
                        mr.reason  = "ERROR";
                        System.err.println("\nGame " + (g + 1) + " error: " + e.getMessage());
                    }

                    // score this game from B's perspective
                    double bScore;
                    switch (mr.outcome) {
                        case "A": aWins++; bScore = 0.0; break;
                        case "B": bWins++; bScore = 1.0; break;
                        default:
                            draws++; bScore = 0.5;
                            if ("TIMEOUT".equals(mr.reason)) timeoutDraws++;
                            if ("LIMIT".equals(mr.reason))   limitDraws++;
                            break;
                    }
                    pairScore += bScore;

                    aTotalNodes += mr.aTotalNodes; bTotalNodes += mr.bTotalNodes;
                    aTotalMs    += mr.aTotalMs;    bTotalMs    += mr.bTotalMs;
                    aTotalMoves += mr.aMoves;      bTotalMoves += mr.bMoves;
                    if (mr.aMaxDepth > aMaxDepth) aMaxDepth = mr.aMaxDepth;
                    if (mr.bMaxDepth > bMaxDepth) bMaxDepth = mr.bMaxDepth;

                    writeResultRow(results, g, p, p % book.size(), aIsWhite ? "white" : "black",
                            mr.outcome, mr.reason, mr.plies, bScore);

                    try { log.flush(); results.flush(); } catch (IOException ignored) {}
                }

                pairScores.add(pairScore);

                // live stats after each completed pair
                double llr = SPRT_ENABLED ? computeSprtLLR(pairScores) : Double.NaN;
                double lowerBound = Math.log(SPRT_BETA / (1.0 - SPRT_ALPHA));
                double upperBound = Math.log((1.0 - SPRT_BETA) / SPRT_ALPHA);

                double sprtNote = llr;
                System.out.print(String.format(
                    "\rPairs [%d/%d]  %s: %d  %s: %d  Draws: %d%s",
                    p + 1, NUM_PAIRS, ENGINE_A_NAME, aWins, ENGINE_B_NAME, bWins, draws,
                    SPRT_ENABLED ? String.format("  LLR: %+.2f (%.2f,%.2f)", sprtNote, lowerBound, upperBound) : ""));

                if (SPRT_ENABLED && pairScores.size() >= 2) {
                    if (llr >= upperBound && sprtVerdict == null) sprtVerdict = "H1 ACCEPTED: B is stronger (>= " + SPRT_ELO1 + " Elo).";
                    if (llr <= lowerBound && sprtVerdict == null) sprtVerdict = "H0 ACCEPTED: B is not an improvement (<= " + SPRT_ELO0 + " Elo).";
                    if (sprtVerdict != null && SPRT_STOP_EARLY) break pairsLoop;
                }
            }

        } catch (IOException e) {
            System.err.println("Failed to open output files: " + e.getMessage());
        }

        executor.shutdownNow();
        System.out.println();
        System.out.println("------------------------------------------------------------");

        printFinalStats(aWins, bWins, draws, timeoutDraws, limitDraws,
                aTotalNodes, bTotalNodes, aTotalMs, bTotalMs,
                aTotalMoves, bTotalMoves, aMaxDepth, bMaxDepth,
                pairScores, sprtVerdict);
    }

    // ------------------------------------------------------------- opening book

    /**
     * Builds a list of random opening move-sequences. Each opening plays only QUIET moves
     * (no captures, no promotions) so material stays equal and no side starts with a big edge.
     * Openings are deduplicated by the resulting position hash.
     */
    private static List<int[]> buildOpeningBook(int numOpenings, int plies, long seed) {
        Random rng = new Random(seed);
        List<int[]> book = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        int attempts = 0;
        int maxAttempts = numOpenings * 20; // safety cap so we can't loop forever
        while (book.size() < numOpenings && attempts < maxAttempts) {
            attempts++;
            Board b = freshBoard();
            int[] moves = new int[plies];
            boolean ok = true;

            for (int i = 0; i < plies; i++) {
                int chosen = pickQuietMove(b, rng);
                if (chosen == -1) { ok = false; break; } // dead end (rare) -> discard this opening
                moves[i] = chosen;
                b.makeMove(chosen);
            }

            if (!ok) continue;

            long hash = b.getCurrentHash();
            if (seen.contains(hash)) continue; // duplicate opening, try again
            seen.add(hash);
            book.add(moves);
        }

        if (book.isEmpty()) { // extreme fallback: at least one empty opening (standard start)
            book.add(new int[0]);
        }
        return book;
    }

    /**
     * Picks a random legal move that is quiet (not a capture, not a promotion). Falls back to
     * any legal move if no quiet move exists. Returns -1 only if there are no legal moves at all.
     */
    private static int pickQuietMove(Board b, Random rng) {
        int[] moves = new int[MoveGenerator.MAX_MOVES];
        int count = MoveGenerator.generateLegalMoves(b, moves);
        if (count == 0) return -1;

        int[] quiet = new int[count];
        int qn = 0;
        for (int i = 0; i < count; i++) {
            if (!Move.isCapture(moves[i]) && !Move.isPromotion(moves[i])) quiet[qn++] = moves[i];
        }

        if (qn > 0) return quiet[rng.nextInt(qn)];
        return moves[rng.nextInt(count)]; // no quiet move available -> accept any legal move
    }

    private static void applyOpening(Board b, int[] opening) {
        for (int m : opening) b.makeMove(m);
    }

    // ------------------------------------------------------------- game loop

    private static Board freshBoard() {
        Board b = new Board();
        b.convertFENtoPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        return b;
    }

    private static class MatchResult {
        String outcome = "DRAW";   // "A", "B", or "DRAW"
        String reason  = "NONE";   // CHECKMATE / STALEMATE / FIFTY / REPETITION / LIMIT / TIMEOUT / ERROR
        int    plies   = 0;        // engine-played plies (excludes the book opening)
        long aTotalNodes = 0, bTotalNodes = 0;
        long aTotalMs    = 0, bTotalMs    = 0;
        int  aMoves = 0,      bMoves = 0;
        int  aMaxDepth = 0,   bMaxDepth = 0;
    }

    private static void playGame(Board boardA,
                                 Board boardB,
                                 Engine engineA,
                                 Engine engineB,
                                 boolean aIsWhite,
                                 int gameNum,
                                 int openingLen,
                                 BufferedWriter log,
                                 MatchResult result) {
        int halfMoves = openingLen; // total plies including the book opening (for the MOVE_LIMIT cap)
        int enginePlies = 0;        // plies actually decided by the engines

        while (halfMoves < MOVE_LIMIT) {
            if (Thread.currentThread().isInterrupted()) {
                result.outcome = "DRAW"; result.reason = "TIMEOUT";
                result.plies = enginePlies;
                return;
            }

            // boardA is the authority for whose turn it is
            boolean isWhiteTurn = (boardA.turn == Board.WHITE);
            boolean isATurn     = (isWhiteTurn == aIsWhite);

            Engine active      = isATurn ? engineA : engineB;
            Board  ownBoard    = isATurn ? boardA  : boardB;   // the board the engine searches
            Board  otherBoard  = isATurn ? boardB  : boardA;   // the board we mirror onto
            String activeName  = isATurn ? ENGINE_A_NAME : ENGINE_B_NAME;

            int move = active.getBestMove(MOVE_TIME_MS, 0);

            if (move == -1) {
                // No legal moves: checkmate or stalemate
                boolean inCheck = ownBoard.getCheckers() != 0L;
                if (inCheck) {
                    result.outcome = isATurn ? "B" : "A"; // the side to move got mated -> the other engine wins
                    result.reason  = "CHECKMATE";
                } else {
                    result.outcome = "DRAW"; result.reason = "STALEMATE";
                }
                result.plies = enginePlies;
                return;
            }

            logMoveStats(log, gameNum, halfMoves, activeName,
                    isWhiteTurn ? "white" : "black",
                    active.lastDepthReached, active.lastNodesSearched,
                    active.lastElapsedMs,    active.lastNodesPerSecond);

            if (isATurn) {
                result.aTotalNodes += active.lastNodesSearched;
                result.aTotalMs    += active.lastElapsedMs;
                result.aMoves++;
                if (active.lastDepthReached > result.aMaxDepth) result.aMaxDepth = active.lastDepthReached;
            } else {
                result.bTotalNodes += active.lastNodesSearched;
                result.bTotalMs    += active.lastElapsedMs;
                result.bMoves++;
                if (active.lastDepthReached > result.bMaxDepth) result.bMaxDepth = active.lastDepthReached;
            }

            // apply to both boards so they stay in sync (same square scheme + Move encoding)
            ownBoard.makeMove(move);
            otherBoard.makeMove(move);
            halfMoves++;
            enginePlies++;

            if (ownBoard.isFiftyMoveRule())        { result.outcome = "DRAW"; result.reason = "FIFTY";      result.plies = enginePlies; return; }
            if (ownBoard.isThreefoldRepetition())  { result.outcome = "DRAW"; result.reason = "REPETITION"; result.plies = enginePlies; return; }
        }

        result.outcome = "DRAW"; result.reason = "LIMIT";
        result.plies = enginePlies;
    }

    // ------------------------------------------------------------- reporting

    private static void logMoveStats(BufferedWriter log,
                                     int gameNum, int halfMove,
                                     String engineName, String side,
                                     int depth, long nodes, long ms, long nps) {
        try {
            log.write(gameNum + "," + halfMove + "," + engineName + "," + side + ","
                    + depth + "," + nodes + "," + ms + "," + nps);
            log.newLine();
        } catch (IOException e) {
            System.err.println("Log write failed: " + e.getMessage());
        }
    }

    private static void writeResultRow(BufferedWriter results, int game, int pair, int openingIndex,
                                       String aColor, String outcome, String reason, int plies, double bScore) {
        try {
            results.write(game + "," + pair + "," + openingIndex + "," + aColor + ","
                    + outcome + "," + reason + "," + plies + "," + bScore);
            results.newLine();
        } catch (IOException e) {
            System.err.println("Result write failed: " + e.getMessage());
        }
    }

    private static void printFinalStats(int aWins, int bWins, int draws,
                                        int timeDraws, int limitDraws,
                                        long aTotalNodes, long bTotalNodes,
                                        long aTotalMs,    long bTotalMs,
                                        long aMoves,      long bMoves,
                                        int  aMaxDepth,   int  bMaxDepth,
                                        List<Double> pairScores, String sprtVerdict) {
        int total = aWins + bWins + draws;

        System.out.println("\n=== Final Results (B = " + ENGINE_B_NAME + ") ===");
        System.out.printf("%-24s %d%n", ENGINE_A_NAME + " wins:", aWins);
        System.out.printf("%-24s %d%n", ENGINE_B_NAME + " wins:", bWins);
        System.out.printf("%-24s %d%n", "Draws:", draws);
        System.out.printf("  %-22s %d%n", "Timeouts:",    timeDraws);
        System.out.printf("  %-22s %d%n", "Move limits:", limitDraws);
        System.out.printf("%-24s %d%n", "Total games:", total);

        // ---- strength statistics (from B's perspective) ----
        if (total > 0) {
            double score = (bWins + 0.5 * draws) / total; // B's score fraction in [0,1]
            double elo   = scoreToElo(score);

            // 95% CI on the Elo difference, using the variance of the pentanomial pair scores.
            // Pairing (each opening played both colors) removes color bias and cuts variance,
            // which is why we measure per-pair rather than per-game.
            String ciStr = "n/a (need >=2 pairs)";
            int nPairs = pairScores.size();
            if (nPairs >= 2) {
                double meanPair = 0.0;
                for (double x : pairScores) meanPair += x;
                meanPair /= nPairs;
                double var = 0.0;
                for (double x : pairScores) var += (x - meanPair) * (x - meanPair);
                var /= (nPairs - 1); // sample variance of a pair score (range 0..2)

                double gameScore   = meanPair / 2.0;                       // per-game score
                double stderrGame  = Math.sqrt(var / nPairs) / 2.0;        // stderr of the per-game score
                double loScore     = clamp01(gameScore - 1.96 * stderrGame);
                double hiScore     = clamp01(gameScore + 1.96 * stderrGame);
                ciStr = String.format("[%+.1f, %+.1f]", scoreToElo(loScore), scoreToElo(hiScore));
            }

            double los = los(bWins, aWins); // probability B is truly stronger than A

            System.out.println("\n=== Strength (B relative to A) ===");
            System.out.printf("Score:   %.1f%% (%.1f/%d)%n", score * 100.0, bWins + 0.5 * draws, total);
            System.out.printf("Elo:     %+.1f   95%% CI %s%n", elo, ciStr);
            System.out.printf("LOS:     %.1f%%   (likelihood B is stronger)%n", los * 100.0);

            if (SPRT_ENABLED) {
                double llr = computeSprtLLR(pairScores);
                double lowerBound = Math.log(SPRT_BETA / (1.0 - SPRT_ALPHA));
                double upperBound = Math.log((1.0 - SPRT_BETA) / SPRT_ALPHA);
                System.out.printf("SPRT:    LLR %+.2f  in (%.2f, %.2f)%n", llr, lowerBound, upperBound);
                System.out.println("Verdict: " + (sprtVerdict != null ? sprtVerdict : "INCONCLUSIVE (ran out of games before a bound was reached)."));
            }
        }

        System.out.println("\n=== Efficiency Summary ===");
        printEngineStats(ENGINE_A_NAME, aTotalNodes, aTotalMs, aMoves, aMaxDepth);
        System.out.println();
        printEngineStats(ENGINE_B_NAME, bTotalNodes, bTotalMs, bMoves, bMaxDepth);
    }

    private static void printEngineStats(String name, long totalNodes, long totalMs,
                                         long moves, int maxDepth) {
        long avgNodes = moves   > 0 ? totalNodes / moves   : 0;
        long avgMs    = moves   > 0 ? totalMs    / moves   : 0;
        long avgNps   = totalMs > 0 ? (totalNodes * 1000L) / totalMs : 0;

        System.out.printf("%s:%n", name);
        System.out.printf("  Moves played:       %d%n",   moves);
        System.out.printf("  Total nodes:        %,d%n",  totalNodes);
        System.out.printf("  Avg nodes/move:     %,d%n",  avgNodes);
        System.out.printf("  Avg time/move:      %d ms%n", avgMs);
        System.out.printf("  Avg nodes/second:   %,d%n",  avgNps);
        System.out.printf("  Max depth reached:  %d%n",   maxDepth);
    }

    // ------------------------------------------------------------- statistics helpers

    /** Convert a score fraction in [0,1] to an Elo difference (clamped to avoid infinities). */
    private static double scoreToElo(double score) {
        if (score <= 0.0) return -800.0;
        if (score >= 1.0) return  800.0;
        return -400.0 * Math.log10(1.0 / score - 1.0);
    }

    /** Convert an Elo difference to the expected score fraction in [0,1]. */
    private static double eloToScore(double elo) {
        return 1.0 / (1.0 + Math.pow(10.0, -elo / 400.0));
    }

    private static double clamp01(double x) {
        if (x < 0.0) return 0.0;
        if (x > 1.0) return 1.0;
        return x;
    }

    /** Likelihood of superiority: P(B truly stronger than A) given the decisive-game counts. */
    private static double los(int bWins, int aWins) {
        if (bWins + aWins == 0) return 0.5;
        return 0.5 * (1.0 + erf((bWins - aWins) / Math.sqrt(2.0 * (bWins + aWins))));
    }

    /**
     * Pentanomial GSPRT log-likelihood ratio. Each pair score (0..2) is treated as one sample;
     * we compare its empirical mean against the two hypotheses using the sample variance. This is
     * the standard practical approximation of the SPRT for the mean of an unknown-variance signal.
     */
    private static double computeSprtLLR(List<Double> pairScores) {
        int n = pairScores.size();
        if (n < 2) return 0.0;

        double sum = 0.0;
        for (double x : pairScores) sum += x;
        double mean = sum / n;

        double var = 0.0;
        for (double x : pairScores) var += (x - mean) * (x - mean);
        var /= (n - 1);
        if (var < 1e-9) var = 1e-9; // guard against divide-by-zero when every pair is identical

        // hypotheses expressed as expected PAIR scores (a pair is two games, so scale by 2)
        double mu0 = 2.0 * eloToScore(SPRT_ELO0);
        double mu1 = 2.0 * eloToScore(SPRT_ELO1);

        return (mu1 - mu0) / var * (sum - n * (mu0 + mu1) / 2.0);
    }

    /** Abramowitz & Stegun 7.1.26 approximation of the error function (max error ~1.5e-7). */
    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return (x >= 0 ? 1.0 : -1.0) * y;
    }
}
