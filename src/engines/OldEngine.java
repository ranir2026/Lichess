package engines;
// FORKED ON 4-8-2026 @ 3:36 PM

import java.util.ArrayList;
import java.util.Arrays;

import core.*;
import pieces.*;

public class OldEngine {
    public long nodesSearched = 0; // for optimization testing

    // Piece values
    private static final double PAWN = 100.0;
    private static final double KNIGHT = 320.0;
    private static final double BISHOP = 330.0;
    private static final double ROOK = 500.0;
    private static final double QUEEN = 900.0;
    private static final double MATE_SCORE = 100000.0;
    private static final double DRAW_SCORE = 0.0;

    private Board game;
    private boolean color;

    private static final double[] PAWN_PST = {
        0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5,  5, 10, 25, 25, 10,  5,  5,
        0,  0,  0, 20, 20,  0,  0,  0,
        5, -5,-10,  0,  0,-10, -5,  5,
        5, 10, 10,-20,-20, 10, 10,  5,
        0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final double[] KNIGHT_PST = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    };

    private static final double[] BISHOP_PST = {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    };

    private static final double[] ROOK_PST = {
          0,  0,  0,  0,  0,  0,  0,  0,
          5, 10, 10, 10, 10, 10, 10,  5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
          0,  0,  0,  5,  5,  0,  0,  0
    };

    private static final double[] QUEEN_PST = {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
          0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    };

    private static final double[] KING_PST = {
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20,
         20, 30, 10,  0,  0, 10, 30, 20
    };

    public OldEngine(Board game, boolean color) {
        this.game = game;
        this.color = color;
    }

    public static double countMaterial(Piece p) {
        if (p instanceof Pawn)   return PAWN;
        if (p instanceof Knight) return KNIGHT;
        if (p instanceof Bishop) return BISHOP;
        if (p instanceof Rook)   return ROOK;
        if (p instanceof Queen)  return QUEEN;
        if (p == null) return 0.0;
        return 0;
    } 


    public double Evaluate() {
        double whiteScore = 0.0;
        double blackScore = 0.0;

        for (int i = 0; i < 64; i++) {
            Piece p = this.game.getGameBoard()[i];
            if (p == null) continue;

            double material = countMaterial(p);
            double positional = 0.0;
            
            // Check color for flip index
            int idx = p.getColor() ? i : flip(i);

            if (p instanceof Pawn) {
                positional = PAWN_PST[idx];
            } else if (p instanceof Knight) {
                positional = KNIGHT_PST[idx];
            } else if (p instanceof Bishop) {
                positional = BISHOP_PST[idx];
            } else if (p instanceof Rook) {
                positional = ROOK_PST[idx];
            } else if (p instanceof Queen) {
                positional = QUEEN_PST[idx];
            } else if (p instanceof King) {
                positional = KING_PST[idx];
            }

            if (p.getColor()) {
                whiteScore += (material + positional);
            } else {
                blackScore += (material + positional);
            }
        }

        // Perspective-based score for Negamax
        int perspective = this.game.getStateTracker().getTurn() ? 1 : -1;
        return (whiteScore - blackScore) * perspective;
    }

    private int flip(int i) {
        return (7 - (i / 8)) * 8 + (i % 8);
    }


    public double Search(int depth, double alpha, double beta) {
        nodesSearched++;

        // negamax search: https://www.chessprogramming.org/Negamax
        // alpha beta: https://www.chessprogramming.org/Alpha-Beta
        boolean isWhiteTurn = this.game.getStateTracker().getTurn();
        ArrayList<Integer> moves = this.game.getLegalMoves(this.game.getStateTracker().getTurn()); 

        if (moves.isEmpty()) {
            if (this.game.isInCheck(isWhiteTurn)) {
                return -MATE_SCORE - depth;
            } else {
                return DRAW_SCORE;
            }
        }

        if (depth == 0) {
            return SearchCaptures(alpha, beta);
        }

        int[] orderedMoves = OrderMoves(moves);

        for (int i=0; i<orderedMoves.length; i++) {
            this.game.makeMove(orderedMoves[i]);
            double score = -1 * Search(depth - 1, -beta, -alpha);
            this.game.unmakeMove(orderedMoves[i]);
            if (score >= beta) {
                return beta;
            }

            alpha = Math.max(alpha, score);
        }


        return alpha;
    }

    public int[] OrderMoves(ArrayList<Integer> moves) {
        long[] moveScores = new long[moves.size()];

        for (int i = 0; i < moves.size(); i++) {
            int move = moves.get(i);
            int score = 0;

            int startSq = Move.getStart(move);
            int endSq = Move.getEnd(move);
            
            Piece movingPiece = this.game.getGameBoard()[startSq];
            Piece targetPiece = this.game.getGameBoard()[endSq];

            if (targetPiece != null) {
                // Formula: (10 * VictimValue) - AttackerValue
                // This ensures Pawn takes Queen is ranked higher than Rook takes Queen
                score = (int)(10 * countMaterial(targetPiece) - countMaterial(movingPiece));
                score += 10000; // Offset to ensure captures are always above quiet moves
            }

            if ((Move.getFlags(move) & Move.PROMOTION_QUIET) != 0) {
                score += 8000;
            } else if ((Move.getFlags(move) & Move.PROMOTION_CAPTURE) != 0) {
                score += 8000;
            }

            moveScores[i] = ((long)score << 32) | (move & 0xFFFFFFFFL);
        }

        Arrays.sort(moveScores);

        int[] orderedMoves = new int[moves.size()];
        for (int i = 0; i < moveScores.length; i++) {
            orderedMoves[i] = (int)(moveScores[moveScores.length - 1 - i] & 0xFFFFFFFFL);
        }

        return orderedMoves;
    }

    public double SearchCaptures(double alpha, double beta) {
        double evaluation = Evaluate();
        if (evaluation >= beta) {
            return beta;
        }
        alpha = Math.max(alpha, evaluation);

        ArrayList<Integer> moves = this.game.getCaptureMoves(this.game.getStateTracker().getTurn());
        int[] sortedMoves = OrderMoves(moves);

        for (int move : sortedMoves) {
            if ((Move.getFlags(move) & Move.CAPTURE) != 0) {
                this.game.makeMove(move);
                
                double score = -SearchCaptures(-beta, -alpha);
                
                this.game.unmakeMove(move);

                if (score >= beta) {
                    return beta;
                }
                alpha = Math.max(alpha, score);
            }
        }

        return alpha;
    }

    public int getBestMove(int depth) {
        boolean turn = this.game.getStateTracker().getTurn();
        ArrayList<Integer> moves = this.game.getLegalMoves(turn);
        
        if (moves.isEmpty()) return -1; 

        int bestMove = moves.get(0);
        double alpha = Double.NEGATIVE_INFINITY;
        double beta = Double.POSITIVE_INFINITY;

        int[] orderedMoves = OrderMoves(moves);

        for (int move : orderedMoves) {
            this.game.makeMove(move);
            
            double score = -Search(depth - 1, -beta, -alpha);
            
            this.game.unmakeMove(move);

            if (score > alpha) {
                alpha = score;
                bestMove = move;
            }
        }
        
        return bestMove;
    }

    public boolean getColor() { return this.color; }
}