package engines;

import core.Board;

import java.util.concurrent.*;

public class MatchRunner {

    public static void main(String[] args) {

        int totalGames = 1000;

        int newEngineWins = 0;
        int oldEngineWins = 0;
        int draws = 0;

        int timeoutDraws = 0;
        int moveLimitDraws = 0;

        long newNodes = 0;
        long oldNodes = 0;

        System.out.println("Starting Simulation: " + totalGames + " games.");
        System.out.println("--------------------------------------------");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        for (int i = 0; i < totalGames; i++) {

            boolean FirstEngineIsWhite = (i % 2 == 0);

            Board board =
                new Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");

            FirstEngine newBot =
                new FirstEngine(board, FirstEngineIsWhite);

            NMPBot oldBot =
                new NMPBot(board, !FirstEngineIsWhite);

            String result;

            Future<String> future =
                executor.submit(() ->
                    playMatch(board, newBot, oldBot));

            try {

                // 4 minute timeout
                result = future.get(4, TimeUnit.MINUTES);

            }
            catch (TimeoutException e) {

                future.cancel(true);

                System.out.println(
                    "\nGame " + (i + 1) + " timed out."
                );

                result = "DRAW_TIMEOUT";
            }
            catch (Exception e) {

                e.printStackTrace();

                result = "DRAW_ERROR";
            }

            // result handling
            if (result.equals("NEW")) {
                newEngineWins++;
            }
            else if (result.equals("OLD")) {
                oldEngineWins++;
            }
            else {

                draws++;

                if (result.equals("DRAW_TIMEOUT")) {
                    timeoutDraws++;
                }

                if (result.equals("DRAW_BY_LIMIT")) {
                    moveLimitDraws++;
                }
            }

            int completed = i + 1;

            double percent =
                (double) completed / totalGames * 100.0;

            newNodes += newBot.nodesSearched;
            oldNodes += oldBot.nodesSearched;

            System.out.print(
                String.format(
                    "\rProgress: [%d/%d] %.1f%% | " +
                    "New: %d | Old: %d | Draws: %d | " +
                    "Timeouts: %d | LimitDraws: %d | " +
                    "Nodes/Game: New %.3e | Old %.3e |",
                    completed,
                    totalGames,
                    percent,
                    newEngineWins,
                    oldEngineWins,
                    draws,
                    timeoutDraws,
                    moveLimitDraws,
                    (double)newNodes / completed,
                    (double)oldNodes / completed
                )
            );
        }

        executor.shutdownNow();

        System.out.println("\n--------------------------------------------");

        printFinalStats(
            newEngineWins,
            oldEngineWins,
            draws,
            timeoutDraws,
            moveLimitDraws
        );
    }

    private static String playMatch(
        Board board,
        FirstEngine newBot,
        NMPBot oldBot
    ) {

        int moveLimit = 260;
        int movesMade = 0;

        while (movesMade < moveLimit) {

            // allow cancellation
            if (Thread.currentThread().isInterrupted()) {
                return "DRAW_TIMEOUT";
            }

            boolean isWhiteTurn =
                board.getStateTracker().getTurn();

            int move;

            if (isWhiteTurn == newBot.getColor()) {

                move = newBot.getBestMove(3000, 0);

            }
            else {

                move = oldBot.getBestMove(3000, 0);
            }

            // no legal moves
            if (move == -1) {

                if (board.isInCheck(isWhiteTurn)) {

                    // side to move got checkmated
                    return
                        (isWhiteTurn == newBot.getColor())
                        ? "OLD"
                        : "NEW";
                }

                return "DRAW";
            }

            board.makeMove(move);

            movesMade++;
        }

        return "DRAW_BY_LIMIT";
    }

    private static void printFinalStats(
        int newW,
        int oldW,
        int d,
        int timeoutDraws,
        int moveLimitDraws
    ) {

        double score =
            newW + (0.5 * d);

        double total =
            newW + oldW + d;

        double winRate =
            (score / total) * 100.0;

        System.out.println("\n--- Final Results ---");

        System.out.println("New Engine Wins: " + newW);
        System.out.println("Old Engine Wins: " + oldW);
        System.out.println("Draws: " + d);

        System.out.println(
            "Timeout Draws: " + timeoutDraws
        );

        System.out.println(
            "Move Limit Draws: " + moveLimitDraws
        );

        System.out.println(
            "New Engine Win Rate: " +
            String.format("%.2f", winRate) +
            "%"
        );
    }
}