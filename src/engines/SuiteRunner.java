// package engines;

// import java.util.concurrent.*;
// import core.Move;
// import bitboardRewrite.SecondEngine;
// // import engines.old.*;

// public class SuiteRunner {

//     public static void main(String[] args) {
//         int totalGames = 1000; // Set to your desired match volume
//         int FirstEngineWins = 0;
//         int secondEngineWins = 0;
//         int draws = 0;
//         int timeoutDraws = 0;
//         int moveLimitDraws = 0;
//         String firstName = "";

//         System.out.println("Starting Cross-Architecture Simulation: " + totalGames + " games.");
//         System.out.println("---------------------------------------------------------");

//         ExecutorService executor = Executors.newSingleThreadExecutor();

//         for (int i = 0; i < totalGames; i++) {
//             boolean FirstEngineIsWhite = (i % 2 == 0);

//             // 1. Initialize BOTH board representations
//             core.Board oldBoard = new core.Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
//             bitboardRewrite.Board newBoard = new bitboardRewrite.Board();
//             newBoard.convertFENtoPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

//             // 2. Initialize engines with native environments
//             FirstEngine oldBot = new FirstEngine(oldBoard, FirstEngineIsWhite);
//             firstName = oldBot.name;
//             SecondEngine newBot = new SecondEngine(newBoard);

//             Future<String> future = executor.submit(() -> playMatch(oldBoard, newBoard, oldBot, newBot, FirstEngineIsWhite));

//             String result;
//             try {
//                 result = future.get(4, TimeUnit.MINUTES);
//             } catch (TimeoutException e) {
//                 future.cancel(true);
//                 System.out.println("\nGame " + (i + 1) + " timed out.");
//                 result = "DRAW_TIMEOUT";
//             } catch (Exception e) {
//                 e.printStackTrace();
//                 result = "DRAW_ERROR";
//             }

//             if (result.equals("FIRST")) FirstEngineWins++;
//             else if (result.equals("SECOND")) secondEngineWins++;
//             else {
//                 draws++;
//                 if (result.equals("DRAW_TIMEOUT")) timeoutDraws++;
//                 if (result.equals("DRAW_BY_LIMIT")) moveLimitDraws++;
//             }

//             double percent = (double) (i + 1) / totalGames * 100.0;
//             System.out.print(String.format(
//                 "\rProgress: [%d/%d] %.1f%% | %s: %d | Second(BB): %d | Draws: %d |",
//                 (i + 1), totalGames, percent, firstName, FirstEngineWins, secondEngineWins, draws
//             ));
//         }

//         executor.shutdownNow();
//         System.out.println("\n---------------------------------------------------------");
//         printFinalStats(FirstEngineWins, secondEngineWins, draws, timeoutDraws, moveLimitDraws);
//     }

//     private static String playMatch(core.Board oldBoard, bitboardRewrite.Board newBoard, FirstEngine oldBot, SecondEngine newBot, boolean FirstEngineIsWhite) {
//         int moveLimit = 260;
//         int movesMade = 0;

//         while (movesMade < moveLimit) {
//             if (Thread.currentThread().isInterrupted()) return "DRAW_TIMEOUT";

//             boolean isWhiteTurn = oldBoard.getStateTracker().getTurn(); 
            
//             int oldMove = -1;
//             int newMove = -1;

//             if (isWhiteTurn == FirstEngineIsWhite) {
//                 // --- FirstEngine (Array) Player ---
//                 oldMove = oldBot.getBestMove(3500, 0);
//                 if (oldMove == -1) {
//                     return oldBoard.isInCheck(isWhiteTurn) ? "SECOND" : "DRAW";
//                 }
//                 newMove = translateOldToNew(oldMove);
//             } else {
//                 // --- SecondEngine (Bitboard) Player ---
//                 newMove = newBot.getBestMove(3500, 0);
//                 if (newMove == -1) {
//                     return (newBoard.getCheckers() != 0L) ? "FIRST" : "DRAW";
//                 }
//                 oldMove = translateNewToOld(newMove);
//             }

//             int newFlags = Move.getFlags(newMove);
//             int newFrom = Move.getStart(newMove);
//             int newTo = Move.getEnd(newMove);
//             switch (newFlags) {
//                 case 32768: newFlags = 45056; break;
//                 case 36864: newFlags = 45056; break;
//                 case 40960: newFlags = 45056; break;

//                 case 49152: newFlags = 61440; break;
//                 case 53248: newFlags = 61440; break;
//                 case 57344: newFlags = 61440; break;
//             }

//             newMove = Move.encode(newFrom, newTo, newFlags);

//             // Execute on respective boards to lock simulation synchronization
//             oldBoard.makeMove(oldMove);
//             newBoard.makeMove(newMove);
//             movesMade++;
//         }

//         return "DRAW_BY_LIMIT";
//     }

//     // ==========================================
//     // --- TRANSLATION LAYER ---
//     // ==========================================

//     // Flips index between Array (0 = A8, 63 = H1) and Bitboard (0 = A1, 63 = H8)
//     private static int flipSquare(int sq) {
//         int rank = 7 - (sq / 8);
//         int file = sq % 8;
//         return rank * 8 + file;
//     }

//     private static int translateOldToNew(int oldMove) {
//         int oldStart = oldMove & 0x3F;
//         int oldEnd = (oldMove >> 6) & 0x3F;
//         int oldFlags = (oldMove >> 12) & 0xF;

//         int newStart = flipSquare(oldStart);
//         int newEnd = flipSquare(oldEnd);
//         int newFlags = 0;

//         switch (oldFlags) {
//             case 0: newFlags = 0; break;  // QUIET_MOVE
//             case 1: newFlags = 4; break;  // CAPTURE
//             case 2: newFlags = 1; break;  // DOUBLE_PAWN
//             case 3: newFlags = 5; break;  // EN_PASSANT
//             case 4: newFlags = 2; break;  // SHORT_CASTLE
//             case 5: newFlags = 3; break;  // LONG_CASTLE
//             case 8: newFlags = 11; break; // PROMOTION_QUIET -> PROMOTION_QUEEN
//             case 9: newFlags = 15; break; // PROMOTION_CAPTURE -> PROMO_CAP_QUEEN
//         }

//         return newStart | (newEnd << 6) | (newFlags << 12);
//     }

//     private static int translateNewToOld(int newMove) {
//         int newStart = newMove & 0x3F;
//         int newEnd = (newMove >> 6) & 0x3F;
//         int newFlags = (newMove >> 12) & 0xF;

//         int oldStart = flipSquare(newStart);
//         int oldEnd = flipSquare(newEnd);
//         int oldFlags = 0;

//         switch (newFlags) {
//             case 0: oldFlags = 0; break;  // QUIET_MOVE
//             case 1: oldFlags = 2; break;  // DOUBLE_PAWN
//             case 2: oldFlags = 4; break;  // SHORT_CASTLE
//             case 3: oldFlags = 5; break;  // LONG_CASTLE
//             case 4: oldFlags = 1; break;  // CAPTURE
//             case 5: oldFlags = 3; break;  // EN_PASSANT
            
//             // Handle variations of underpromotions gracefully back to Queen promotion types
//             case 8: case 9: case 10: case 11: 
//                 oldFlags = 8; 
//                 break; 
//             case 12: case 13: case 14: case 15: 
//                 oldFlags = 9; 
//                 break; 
//         }

//         return oldStart | (oldEnd << 6) | (oldFlags << 12);
//     }

//     private static void printFinalStats(int first, int second, int draws, int timeDraws, int limitDraws) {
//         double total = first + second;
//         double winRate = (second / total) * 100.0;

//         System.out.println("\n--- Final Results ---");
//         System.out.println("First Engine (Array): " + first);
//         System.out.println("Second Engine (BB):   " + second);
//         System.out.println("Draws:                " + draws);
//         System.out.println("  - Timeouts:         " + timeDraws);
//         System.out.println("  - Move Limits:      " + limitDraws);
//         System.out.println(String.format("Bitboard Win Rate vs Array: %.2f%%", winRate));
//     }
// }

package engines;

import java.util.concurrent.*;
import java.io.*;
import core.Move;
import bitboardRewrite.SecondEngine;
// import engines.old.*;

public class SuiteRunner {

    private static final String LOG_PATH = "suite_log.csv";

    public static void main(String[] args) {
        int totalGames = 1000; // Set to your desired match volume
        int FirstEngineWins = 0;
        int secondEngineWins = 0;
        int draws = 0;
        int timeoutDraws = 0;
        int moveLimitDraws = 0;
        String firstName = "";

        System.out.println("Starting Cross-Architecture Simulation: " + totalGames + " games.");
        System.out.println("Per-move stats logging to: " + LOG_PATH);
        System.out.println("---------------------------------------------------------");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (BufferedWriter logWriter = new BufferedWriter(new FileWriter(LOG_PATH))) {
            logWriter.write("game,moveNumber,engine,side,depthReached,nodesSearched,elapsedMs,nodesPerSecond");
            logWriter.newLine();

            for (int i = 0; i < totalGames; i++) {
                boolean FirstEngineIsWhite = (i % 2 == 0);

                // 1. Initialize BOTH board representations
                core.Board oldBoard = new core.Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
                bitboardRewrite.Board newBoard = new bitboardRewrite.Board();
                newBoard.convertFENtoPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

                // 2. Initialize engines with native environments
                FirstEngine oldBot = new FirstEngine(oldBoard, FirstEngineIsWhite);
                firstName = oldBot.name;
                SecondEngine newBot = new SecondEngine(newBoard);

                final int gameNum = i;
                Future<String> future = executor.submit(() -> playMatch(oldBoard, newBoard, oldBot, newBot, FirstEngineIsWhite, gameNum, logWriter));

                String result;
                try {
                    result = future.get(4, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    System.out.println("\nGame " + (i + 1) + " timed out.");
                    result = "DRAW_TIMEOUT";
                } catch (Exception e) {
                    e.printStackTrace();
                    result = "DRAW_ERROR";
                }

                if (result.equals("FIRST")) FirstEngineWins++;
                else if (result.equals("SECOND")) secondEngineWins++;
                else {
                    draws++;
                    if (result.equals("DRAW_TIMEOUT")) timeoutDraws++;
                    if (result.equals("DRAW_BY_LIMIT")) moveLimitDraws++;
                }

                double percent = (double) (i + 1) / totalGames * 100.0;
                System.out.print(String.format(
                    "\rProgress: [%d/%d] %.1f%% | %s: %d | Second(BB): %d | Draws: %d |",
                    (i + 1), totalGames, percent, firstName, FirstEngineWins, secondEngineWins, draws
                ));

                // flush periodically so partial data survives a crash/interrupt mid-suite
                try { logWriter.flush(); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            System.err.println("Failed to open log file: " + e.getMessage());
        }

        executor.shutdownNow();
        System.out.println("\n---------------------------------------------------------");
        printFinalStats(FirstEngineWins, secondEngineWins, draws, timeoutDraws, moveLimitDraws);
    }

    private static String playMatch(core.Board oldBoard, bitboardRewrite.Board newBoard, FirstEngine oldBot, SecondEngine newBot, boolean FirstEngineIsWhite, int gameNum, BufferedWriter logWriter) {
        int moveLimit = 260;
        int movesMade = 0;

        while (movesMade < moveLimit) {
            if (Thread.currentThread().isInterrupted()) return "DRAW_TIMEOUT";

            boolean isWhiteTurn = oldBoard.getStateTracker().getTurn(); 
            
            int oldMove = -1;
            int newMove = -1;

            if (isWhiteTurn == FirstEngineIsWhite) {
                // --- FirstEngine (Array) Player ---
                oldMove = oldBot.getBestMove(3500, 0);
                if (oldMove == -1) {
                    return oldBoard.isInCheck(isWhiteTurn) ? "SECOND" : "DRAW";
                }
                newMove = translateOldToNew(oldMove);
                logMoveStats(logWriter, gameNum, movesMade, "FirstEngine", isWhiteTurn,
                    oldBot.lastDepthReached, oldBot.lastNodesSearched, oldBot.lastElapsedMs, oldBot.lastNodesPerSecond);
            } else {
                // --- SecondEngine (Bitboard) Player ---
                newMove = newBot.getBestMove(3500, 0);
                if (newMove == -1) {
                    return (newBoard.getCheckers() != 0L) ? "FIRST" : "DRAW";
                }
                oldMove = translateNewToOld(newMove);
                logMoveStats(logWriter, gameNum, movesMade, "SecondEngine", isWhiteTurn,
                    newBot.lastDepthReached, newBot.lastNodesSearched, newBot.lastElapsedMs, newBot.lastNodesPerSecond);
            }

            int newFlags = Move.getFlags(newMove);
            int newFrom = Move.getStart(newMove);
            int newTo = Move.getEnd(newMove);
            switch (newFlags) {
                case 32768: newFlags = 45056; break;
                case 36864: newFlags = 45056; break;
                case 40960: newFlags = 45056; break;

                case 49152: newFlags = 61440; break;
                case 53248: newFlags = 61440; break;
                case 57344: newFlags = 61440; break;
            }

            newMove = Move.encode(newFrom, newTo, newFlags);

            // Execute on respective boards to lock simulation synchronization
            oldBoard.makeMove(oldMove);
            newBoard.makeMove(newMove);
            movesMade++;
        }

        return "DRAW_BY_LIMIT";
    }

    // ==========================================
    // --- TRANSLATION LAYER ---
    // ==========================================

    // Flips index between Array (0 = A8, 63 = H1) and Bitboard (0 = A1, 63 = H8)
    private static int flipSquare(int sq) {
        int rank = 7 - (sq / 8);
        int file = sq % 8;
        return rank * 8 + file;
    }

    private static int translateOldToNew(int oldMove) {
        int oldStart = oldMove & 0x3F;
        int oldEnd = (oldMove >> 6) & 0x3F;
        int oldFlags = (oldMove >> 12) & 0xF;

        int newStart = flipSquare(oldStart);
        int newEnd = flipSquare(oldEnd);
        int newFlags = 0;

        switch (oldFlags) {
            case 0: newFlags = 0; break;  // QUIET_MOVE
            case 1: newFlags = 4; break;  // CAPTURE
            case 2: newFlags = 1; break;  // DOUBLE_PAWN
            case 3: newFlags = 5; break;  // EN_PASSANT
            case 4: newFlags = 2; break;  // SHORT_CASTLE
            case 5: newFlags = 3; break;  // LONG_CASTLE
            case 8: newFlags = 11; break; // PROMOTION_QUIET -> PROMOTION_QUEEN
            case 9: newFlags = 15; break; // PROMOTION_CAPTURE -> PROMO_CAP_QUEEN
        }

        return newStart | (newEnd << 6) | (newFlags << 12);
    }

    private static int translateNewToOld(int newMove) {
        int newStart = newMove & 0x3F;
        int newEnd = (newMove >> 6) & 0x3F;
        int newFlags = (newMove >> 12) & 0xF;

        int oldStart = flipSquare(newStart);
        int oldEnd = flipSquare(newEnd);
        int oldFlags = 0;

        switch (newFlags) {
            case 0: oldFlags = 0; break;  // QUIET_MOVE
            case 1: oldFlags = 2; break;  // DOUBLE_PAWN
            case 2: oldFlags = 4; break;  // SHORT_CASTLE
            case 3: oldFlags = 5; break;  // LONG_CASTLE
            case 4: oldFlags = 1; break;  // CAPTURE
            case 5: oldFlags = 3; break;  // EN_PASSANT
            
            // Handle variations of underpromotions gracefully back to Queen promotion types
            case 8: case 9: case 10: case 11: 
                oldFlags = 8; 
                break; 
            case 12: case 13: case 14: case 15: 
                oldFlags = 9; 
                break; 
        }

        return oldStart | (oldEnd << 6) | (oldFlags << 12);
    }

    private static void printFinalStats(int first, int second, int draws, int timeDraws, int limitDraws) {
        double total = first + second;
        double winRate = (second / total) * 100.0;

        System.out.println("\n--- Final Results ---");
        System.out.println("First Engine (Array): " + first);
        System.out.println("Second Engine (BB):   " + second);
        System.out.println("Draws:                " + draws);
        System.out.println("  - Timeouts:         " + timeDraws);
        System.out.println("  - Move Limits:      " + limitDraws);
        System.out.println(String.format("Bitboard Win Rate vs Array: %.2f%%", winRate));
    }

    // writes one CSV row per move with the engine's self-reported search stats.
    // a write failure here shouldn't take down the whole suite, so IOException
    // is caught and reported rather than propagated.
    private static void logMoveStats(BufferedWriter logWriter, int gameNum, int moveNumber, String engineName,
                                      boolean isWhiteTurn, int depthReached, long nodesSearched, long elapsedMs, long nodesPerSecond) {
        String side = isWhiteTurn ? "white" : "black";
        try {
            logWriter.write(gameNum + "," + moveNumber + "," + engineName + "," + side + "," +
                depthReached + "," + nodesSearched + "," + elapsedMs + "," + nodesPerSecond);
            logWriter.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write log row: " + e.getMessage());
        }
    }
}