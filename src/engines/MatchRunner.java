package engines;
import core.Board;

public class MatchRunner {
    public static void main(String[] args) {
        int totalGames = 1000;
        int newEngineWins = 0;
        int oldEngineWins = 0;
        int draws = 0;
        long newNodes = 0;
        long oldNodes = 0;

        System.out.println("Starting Simulation: " + totalGames + " games.");
        System.out.println("--------------------------------------------");

        for (int i=0; i<totalGames; i++) {
            boolean FirstEngineIsWhite = (i % 2 == 0);
            Board board = new Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");

            FirstEngine newBot = new FirstEngine(board, FirstEngineIsWhite);
            NMPBot oldBot = new NMPBot(board, !FirstEngineIsWhite);

            String result = playMatch(board, newBot, oldBot);

            if (result.equals("NEW")) newEngineWins++;
            else if (result.equals("OLD")) oldEngineWins++;
            else draws++;

            int completed = i + 1;
            double percent = (double) completed / totalGames * 100;

            newNodes += newBot.nodesSearched;
            oldNodes += oldBot.nodesSearched;
            
            // Overwrites the same line in the console (\r)
            System.out.print(String.format("\rProgress: [%d/%d] %.1f%% | New: %d | Old: %d | Draws: %d | Nodes: New %.3e | Old %.3e |", 
                completed, totalGames, percent, newEngineWins, oldEngineWins, draws, (double)newNodes/completed, (double)oldNodes/completed));
        }

        System.out.println("\n--------------------------------------------");
        printFinalStats(newEngineWins, oldEngineWins, draws);
        
    }

    private static String playMatch(Board board, FirstEngine newBot, NMPBot oldBot) {
        int moveLimit = 260; // Hard cap to prevent infinite shuffling
        int movesMade = 0;

        while (movesMade < moveLimit) {
            boolean isWhiteTurn = board.getStateTracker().getTurn();
            int move;

            // Determine whose turn it is and get their best move
            if (isWhiteTurn == newBot.getColor()) {
                move = newBot.getBestMove(3500, 0);
            } else {
                move = oldBot.getBestMove(3500, 0);
            }

            // Game Over Detection: No legal moves left
            if (move == -1) {
                if (board.isInCheck(isWhiteTurn)) {
                    // Current player is in checkmate, so the OTHER player wins
                    return (isWhiteTurn == newBot.getColor()) ? "OLD" : "NEW";
                }
                return "DRAW"; // Stalemate
            }

            board.makeMove(move);
            movesMade++;
        }
        return "DRAW_BY_LIMIT";
    }

    private static void printFinalStats(int newW, int oldW, int d) {
        double score = newW + (0.5 * d);
        double total = newW + oldW + d;
        double winRate = (score / total) * 100;
        
        System.out.println("\n--- Final Results ---");
        System.out.println("New Engine Wins: " + newW);
        System.out.println("Old Engine Wins: " + oldW);
        System.out.println("Draws: " + d);
        System.out.println("New Engine Win Rate: " + String.format("%.2f", winRate) + "%");
    }
}