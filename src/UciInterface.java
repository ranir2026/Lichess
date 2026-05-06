import java.util.Scanner;

import core.Board;
import core.Move;
import engines.NMPBot;
// import engines.NMPBot;

import java.util.ArrayList;

public class UciInterface {
    private static Board board;
    private static NMPBot engine;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        
        // Default starting position
        board = new Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");

        while (sc.hasNextLine()) {
            String input = sc.nextLine();
            String[] tokens = input.split(" ");

            if (input.equals("uci")) {
                System.out.println("id name MyJavaBot");
                System.out.println("id author YourName");

                System.out.println("option name Move Overhead type spin default 10 min 0 max 5000");

                System.out.println("uciok");
            } 
            else if (input.equals("isready")) {
                System.out.println("readyok");
            } 
            else if (input.equals("ucinewgame")) {
                board = new Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
            } 
            else if (input.startsWith("position")) {
                handlePosition(tokens);
            } 
            // else if (input.startsWith("go")) {
            //     // Initialize engine with current board and correct turn color
            //     engine = new NMPBot(board, board.getStateTracker().getTurn());
                
            //     // Depth 3 search as seen in your previous main method
            //     int bestMoveInt = engine.getBestMove(8); 
                
            //     System.out.println("bestmove " + moveToLan(bestMoveInt));
            // } 
            else if (input.startsWith("setoption")) {
                
            }
            else if (input.equals("quit")) {
                break;
            }
            else if (input.startsWith("go")) {
                int wtime = 0, btime = 0, winc = 0, binc = 0;

                // Parse time and increment from the UCI command
                for (int i = 1; i < tokens.length - 1; i++) {
                    if (tokens[i].equals("wtime")) wtime = Integer.parseInt(tokens[i + 1]);
                    else if (tokens[i].equals("btime")) btime = Integer.parseInt(tokens[i + 1]);
                    else if (tokens[i].equals("winc")) winc = Integer.parseInt(tokens[i + 1]);
                    else if (tokens[i].equals("binc")) binc = Integer.parseInt(tokens[i + 1]);
                }

                // Determine which time to use based on the current turn
                int myTime = board.getStateTracker().getTurn() ? wtime : btime;
                int myInc = board.getStateTracker().getTurn() ? winc : binc;

                // Initialize engine
                engine = new NMPBot(board, board.getStateTracker().getTurn());

                // Pass the time to your engine's search method
                // You will need to update getBestMove to accept these parameters
                int bestMoveInt = engine.getBestMove(myTime, myInc); 
                
                System.out.println("bestmove " + moveToLan(bestMoveInt));
            }
        }
        sc.close();
    }

    private static void handlePosition(String[] tokens) {
        int movesIndex = -1;

        if (tokens[1].equals("startpos")) {
            board = new Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
            movesIndex = 2;
        } else if (tokens[1].equals("fen")) {
            // FEN strings are 6 tokens long; "moves" would start at index 8
            StringBuilder fen = new StringBuilder();
            for (int i = 2; i < 8; i++) fen.append(tokens[i]).append(" ");
            board = new Board(fen.toString().trim());
            movesIndex = 8;
        }

        // Apply moves if they exist in the command
        if (movesIndex != -1 && tokens.length > movesIndex && tokens[movesIndex].equals("moves")) {
            for (int i = movesIndex + 1; i < tokens.length; i++) {
                String moveStr = tokens[i];
                applyLanMove(moveStr);
            }
        }
    }

    private static void applyLanMove(String lan) {
        int start = (lan.charAt(0) - 'a') + (8 - Character.getNumericValue(lan.charAt(1))) * 8;
        int end = (lan.charAt(2) - 'a') + (8 - Character.getNumericValue(lan.charAt(3))) * 8;
        
        ArrayList<Integer> legalMoves = board.getLegalMoves(board.getStateTracker().getTurn());
        for (int move : legalMoves) {
            if (Move.getStart(move) == start && Move.getEnd(move) == end) {
                // If it's a promotion, we'd need extra logic here to match 'q', 'r', etc.
                // But for a basic implementation, we match start and end squares.
                board.makeMove(move);
                break;
            }
        }
    }

    private static String moveToLan(int move) {
        int start = Move.getStart(move);
        int end = Move.getEnd(move);
        int flags = Move.getFlags(move);

        String startSq = "" + (char) ('a' + (start % 8)) + (8 - (start / 8));
        String endSq = "" + (char) ('a' + (end % 8)) + (8 - (end / 8));
        
        String promotion = "";
        if (flags == Move.PROMOTION_QUIET || flags == Move.PROMOTION_CAPTURE) {
            promotion = "q"; // Defaulting to queen promotion for UCI compatibility
        }

        return startSq + endSq + promotion;
    }
}