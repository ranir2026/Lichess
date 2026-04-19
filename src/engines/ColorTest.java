package engines;
import core.Board;

public class ColorTest {
    public static void main(String[] args) {
        int totalGames = 250;
        int whiteWins = 0;
        int blackWins = 0;
        int draws = 0;

        System.out.println("Starting Simulation: " + totalGames + " games.");
        System.out.println("--------------------------------------------");

        for (int i = 0; i < totalGames; i++) {
            // We still use two engine instances, but we track by color result
            boolean firstEngineIsWhite = (i % 2 == 0);
            Board board = new Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");

            FirstEngine engine1 = new FirstEngine(board, firstEngineIsWhite);
            FirstEngine engine2 = new FirstEngine(board, !firstEngineIsWhite);

            // playMatch now returns "WHITE", "BLACK", or "DRAW"
            String result = playMatch(board, engine1, engine2);

            if (result.equals("WHITE")) whiteWins++;
            else if (result.equals("BLACK")) blackWins++;
            else draws++;

            int completed = i + 1;
            double percent = (double) completed / totalGames * 100;
            
            System.out.print(String.format("\rProgress: [%d/%d] %.1f%% | White: %d | Black: %d | Draws: %d", 
                completed, totalGames, percent, whiteWins, blackWins, draws));
        }

        System.out.println("\n--------------------------------------------");
        printFinalStats(whiteWins, blackWins, draws);
    }

    private static String playMatch(Board board, FirstEngine engine1, FirstEngine engine2) {
        int moveLimit = 150; 
        int movesMade = 0;

        while (movesMade < moveLimit) {
            boolean isWhiteTurn = board.getStateTracker().getTurn();
            int move;

            // Get move from whichever engine is assigned to the current turn's color
            if (isWhiteTurn == engine1.getColor()) {
                move = engine1.getBestMove(2000, 0);
            } else {
                move = engine2.getBestMove(2000, 0);
            }

            if (move == -1) {
                if (board.isInCheck(isWhiteTurn)) {
                    // If it's White's turn and they are in checkmate, Black wins.
                    return isWhiteTurn ? "BLACK" : "WHITE";
                }
                return "DRAW"; 
            }

            board.makeMove(move);
            movesMade++;
        }
        return "DRAW";
    }

    private static void printFinalStats(int whiteW, int blackW, int d) {
        double total = whiteW + blackW + d;
        double whiteWinRate = (whiteW / total) * 100;
        double blackWinRate = (blackW / total) * 100;
        
        System.out.println("\n--- Color Performance Results ---");
        System.out.println("White Wins: " + whiteW + " (" + String.format("%.1f", whiteWinRate) + "%)");
        System.out.println("Black Wins: " + blackW + " (" + String.format("%.1f", blackWinRate) + "%)");
        System.out.println("Draws: " + d);
        System.out.println("---------------------------------");
    }
}