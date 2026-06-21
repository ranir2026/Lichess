package bitboardRewrite;

import java.util.Scanner;

public class UciInterface {
    private static Board board = new Board();
    private static SecondEngine engine;

    public static void main(String[] args) {
        // try-with-resources ensures sc.close() is called automatically
        try (Scanner sc = new Scanner(System.in)) {
            
            // Initial setup
            board = new Board();
            board.convertFENtoPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

            while (sc.hasNextLine()) {
                String input = sc.nextLine();
                if (input == null || input.isEmpty()) continue;
                
                String[] tokens = input.split("\\s+");

                if (tokens[0].equals("uci")) {
                    System.out.println("id name TurquoiseBot");
                    System.out.println("id author RR");
                    System.out.println("uciok");
                } 
                else if (tokens[0].equals("isready")) {
                    System.out.println("readyok");
                } 
                else if (tokens[0].equals("ucinewgame")) {
                    board.convertFENtoPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
                } 
                else if (tokens[0].equals("position")) {
                    handlePosition(tokens);
                } 
                else if (tokens[0].equals("go")) {
                    handleGo(tokens);
                } 
                else if (tokens[0].equals("quit")) {
                    break; // Exiting the loop triggers the close()
                }
            }
        } catch (Throwable t) {
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("engine_crash.log", true))) {
                pw.println("=== Crash at " + java.time.LocalDateTime.now() + " ===");
                t.printStackTrace(pw);
            } catch (Exception logFailure) {
                // If even logging fails (e.g. disk full, no write permission), there's
                // nothing safe left to do -- swallow it rather than touch System.out.
            }
        }
    }

    private static void handlePosition(String[] tokens) {
        int movesIndex = -1;

        if (tokens[1].equals("startpos")) {
            board.convertFENtoPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
            movesIndex = 2;
        } else if (tokens[1].equals("fen")) {
            StringBuilder fen = new StringBuilder();
            // FEN usually spans tokens 2 to 7
            for (int i = 2; i < Math.min(tokens.length, 8); i++) {
                fen.append(tokens[i]).append(" ");
            }
            board.convertFENtoPosition(fen.toString().trim());
            movesIndex = 8;
        }

        if (movesIndex != -1 && tokens.length > movesIndex && tokens[movesIndex].equals("moves")) {
            for (int i = movesIndex + 1; i < tokens.length; i++) {
                applyLanMove(tokens[i]);
            }
        }
    }

    private static void handleGo(String[] tokens) {
        int wtime = 0;
        int btime = 0;
        int winc = 0;
        int binc = 0;

        for (int i = 1; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "wtime":
                    wtime = Integer.parseInt(tokens[++i]);
                    break;
                case "btime":
                    btime = Integer.parseInt(tokens[++i]);
                    break;
                case "winc":
                    winc = Integer.parseInt(tokens[++i]);
                    break;
                case "binc":
                    binc = Integer.parseInt(tokens[++i]);
                    break;
                case "movestogo":
                    break;
                case "depth":
                    i++; 
                    break;
            }
        }

        int timeLeft = (board.turn == Board.WHITE) ? wtime : btime;
        int increment = (board.turn == Board.WHITE) ? winc : binc;

        engine = new SecondEngine(board);
        
        if (timeLeft == 0) timeLeft = 5000; 

        int bestMove = engine.getBestMove(timeLeft, increment); 
        System.out.println("bestmove " + moveToLan(bestMove));
    }

    private static void applyLanMove(String lan) {
        // Convert UCI string (e2e4) to bitboard squares (a1=0)
        int startFile = lan.charAt(0) - 'a';
        int startRank = lan.charAt(1) - '1';
        int startSq = startRank * 8 + startFile;

        int endFile = lan.charAt(2) - 'a';
        int endRank = lan.charAt(3) - '1';
        int endSq = endRank * 8 + endFile;

        char promoChar = (lan.length() > 4) ? lan.charAt(4) : ' ';

        int[] moves = new int[MoveGenerator.MAX_MOVES];
        int count = MoveGenerator.generateLegalMoves(board, moves);

        for (int i = 0; i < count; i++) {
            int move = moves[i];
            if (Move.getStart(move) == startSq && Move.getEnd(move) == endSq) {
                // If move is a promotion, check if promo piece matches UCI char
                if (Move.isPromotion(move)) {
                    int flags = Move.getFlags(move);
                    if (promoChar == 'q' && (flags == Move.PROMOTION_QUEEN || flags == Move.PROMO_CAP_QUEEN)) {
                        board.makeMove(move); return;
                    }
                    if (promoChar == 'n' && (flags == Move.PROMOTION_KNIGHT || flags == Move.PROMO_CAP_KNIGHT)) {
                        board.makeMove(move); return;
                    }
                    if (promoChar == 'r' && (flags == Move.PROMOTION_ROOK || flags == Move.PROMO_CAP_ROOK)) {
                        board.makeMove(move); return;
                    }
                    if (promoChar == 'b' && (flags == Move.PROMOTION_BISHOP || flags == Move.PROMO_CAP_BISHOP)) {
                        board.makeMove(move); return;
                    }
                    continue; // Promo char didn't match this move
                }
                
                board.makeMove(move);
                return;
            }
        }
    }

    private static String moveToLan(int move) {
        if (move == -1) return "0000";
        
        int start = Move.getStart(move);
        int end = Move.getEnd(move);
        int flags = Move.getFlags(move);

        // Map squares back to UCI (0 -> a1)
        String startSq = "" + (char) ('a' + (start % 8)) + (char) ('1' + (start / 8));
        String endSq = "" + (char) ('a' + (end % 8)) + (char) ('1' + (end / 8));

        String promo = "";
        // Check for promotion flags and append correct character
        switch (flags) {
            case Move.PROMOTION_QUEEN:  case Move.PROMO_CAP_QUEEN:  promo = "q"; break;
            case Move.PROMOTION_KNIGHT: case Move.PROMO_CAP_KNIGHT: promo = "n"; break;
            case Move.PROMOTION_ROOK:   case Move.PROMO_CAP_ROOK:   promo = "r"; break;
            case Move.PROMOTION_BISHOP: case Move.PROMO_CAP_BISHOP: promo = "b"; break;
        }

        return startSq + endSq + promo;
    }
}