import java.util.concurrent.*;
import java.io.*;

public class SuiteRunner {

    // config
    private static final int    TOTAL_GAMES  = 1000;
    private static final int    MOVE_TIME_MS = 3500;  // ms per move per engine
    private static final int    MOVE_LIMIT   = 400;   // half-move ceiling → draw
    private static final String LOG_PATH     = "suite_log.csv";

    public static String ENGINE_A_NAME = "SecondEngine v2.0";
    public static String ENGINE_B_NAME = "Updated";
    
    private static Engine makeEngineA(Board b) { return new SecondEngine(b); }
    private static Engine makeEngineB(Board b) { return new TurquoiseBot(b); } // <<< swap here
    public static void main(String[] args) {
        int aWins = 0, bWins = 0, draws = 0, timeoutDraws = 0, limitDraws = 0;

        long aTotalNodes = 0, bTotalNodes = 0;
        long aTotalMs    = 0, bTotalMs    = 0;
        long aTotalMoves = 0, bTotalMoves = 0;
        int  aMaxDepth   = 0, bMaxDepth   = 0;

        System.out.println("=== TurquoiseBot Self-Play Suite ===");
        System.out.println(ENGINE_A_NAME + " vs " + ENGINE_B_NAME);
        System.out.printf("Games: %d  |  Time/move: %d ms  |  Move limit: %d half-moves%n",
                TOTAL_GAMES, MOVE_TIME_MS, MOVE_LIMIT);
        System.out.println("Logging per-move stats to: " + LOG_PATH);
        System.out.println("------------------------------------------------------------");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (BufferedWriter log = new BufferedWriter(new FileWriter(LOG_PATH))) {
            log.write("game,halfMove,engineName,side,depthReached,nodesSearched,elapsedMs,nodesPerSecond");
            log.newLine();

            for (int g = 0; g < TOTAL_GAMES; g++) {
                boolean aIsWhite = (g % 2 == 0); // alternate colours for fairness

                // Each engine gets its own independent Board
                Board boardA = freshBoard();
                Board boardB = freshBoard();

                Engine engineA = makeEngineA(boardA);
                Engine engineB = makeEngineB(boardB);

                ENGINE_A_NAME = engineA.getName() + " v" + engineA.getVersion();
                ENGINE_B_NAME = engineB.getName() + " v" + engineB.getVersion();

                final int gameNum  = g;
                final boolean aWht = aIsWhite;

                MatchResult mr = new MatchResult();
                Future<?> future = executor.submit(() ->
                        playGame(boardA, boardB, engineA, engineB, aWht, gameNum, log, mr));

                try {
                    future.get(4, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    mr.outcome = "DRAW_TIMEOUT";
                } catch (Exception e) {
                    mr.outcome = "DRAW_ERROR";
                    System.err.println("\nGame " + (g + 1) + " error: " + e.getMessage());
                }

                switch (mr.outcome) {
                    case "A": aWins++; break;
                    case "B": bWins++; break;
                    default:
                        draws++;
                        if (mr.outcome.equals("DRAW_TIMEOUT"))  timeoutDraws++;
                        if (mr.outcome.equals("DRAW_BY_LIMIT")) limitDraws++;
                        break;
                }

                aTotalNodes += mr.aTotalNodes; bTotalNodes += mr.bTotalNodes;
                aTotalMs    += mr.aTotalMs;    bTotalMs    += mr.bTotalMs;
                aTotalMoves += mr.aMoves;      bTotalMoves += mr.bMoves;
                if (mr.aMaxDepth > aMaxDepth) aMaxDepth = mr.aMaxDepth;
                if (mr.bMaxDepth > bMaxDepth) bMaxDepth = mr.bMaxDepth;

                try { log.flush(); } catch (IOException ignored) {}

                double pct = (double)(g + 1) / TOTAL_GAMES * 100.0;
                System.out.print(String.format(
                    "\rProgress: [%d/%d] %.1f%%  |  %s: %d  |  %s: %d  |  Draws: %d",
                    g + 1, TOTAL_GAMES, pct,
                    ENGINE_A_NAME, aWins, ENGINE_B_NAME, bWins, draws));
            }

        } catch (IOException e) {
            System.err.println("Failed to open log file: " + e.getMessage());
        }

        executor.shutdownNow();
        System.out.println();
        System.out.println("------------------------------------------------------------");
        printFinalStats(aWins, bWins, draws, timeoutDraws, limitDraws,
                aTotalNodes, bTotalNodes, aTotalMs, bTotalMs,
                aTotalMoves, bTotalMoves, aMaxDepth, bMaxDepth);
    }

    // --------------------------------------------------------------- game loop

    private static Board freshBoard() {
        Board b = new Board();
        b.convertFENtoPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        return b;
    }

    private static class MatchResult {
        String outcome  = "DRAW";
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
                                 BufferedWriter log,
                                 MatchResult result) {
        int halfMoves = 0;

        while (halfMoves < MOVE_LIMIT) {
            if (Thread.currentThread().isInterrupted()) {
                result.outcome = "DRAW_TIMEOUT";
                return;
            }

            // boardA is the authority for whose turn it is
            boolean isWhiteTurn = (boardA.turn == Board.WHITE);
            boolean isATurn     = (isWhiteTurn == aIsWhite);

            Engine active  = isATurn ? engineA : engineB;
            Board        ownBoard = isATurn ? boardA  : boardB;   // the board the engine searches
            Board        otherBoard = isATurn ? boardB : boardA;  // the board we mirror onto
            String       activeName = isATurn ? ENGINE_A_NAME : ENGINE_B_NAME;

            int move = active.getBestMove(MOVE_TIME_MS, 0);

            if (move == -1) {
                // No legal moves: checkmate or stalemate
                boolean inCheck = ownBoard.getCheckers() != 0L;
                if (inCheck) {
                    // The engine whose turn it was got checkmated — the other engine wins
                    result.outcome = isATurn ? "B" : "A";
                } else {
                    result.outcome = "DRAW"; // stalemate
                }
                return;
            }

            // Log and accumulate stats
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

            // Apply the move to both boards so they stay in sync.
            // Both boards use the same square scheme (a1=0, h8=63) and the
            // same Move encoding, so the integer can be used on either board directly.
            ownBoard.makeMove(move);
            otherBoard.makeMove(move);
            halfMoves++;

            if (ownBoard.isFiftyMoveRule() || ownBoard.isThreefoldRepetition()) {
                result.outcome = "DRAW";
                return;
            }
        }

        result.outcome = "DRAW_BY_LIMIT";
    }

    // --------------------------------------------------------------- reporting

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

    private static void printFinalStats(int aWins, int bWins, int draws,
                                        int timeDraws, int limitDraws,
                                        long aTotalNodes, long bTotalNodes,
                                        long aTotalMs,    long bTotalMs,
                                        long aMoves,      long bMoves,
                                        int  aMaxDepth,   int  bMaxDepth) {
        int    total   = aWins + bWins + draws;
        double decisive = aWins + bWins;

        System.out.println("\n=== Final Results ===");
        System.out.printf("%-22s %d%n", ENGINE_A_NAME + " wins:", aWins);
        System.out.printf("%-22s %d%n", ENGINE_B_NAME + " wins:", bWins);
        System.out.printf("%-22s %d%n", "Draws:", draws);
        System.out.printf("  %-20s %d%n", "Timeouts:",    timeDraws);
        System.out.printf("  %-20s %d%n", "Move limits:", limitDraws);
        System.out.printf("%-22s %d%n", "Total games:", total);

        if (decisive > 0) {
            System.out.printf("%n%s win rate (decisive only): %.2f%%%n",
                    ENGINE_B_NAME, (bWins / decisive) * 100.0);
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
}
