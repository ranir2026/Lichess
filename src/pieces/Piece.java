package pieces;
import java.util.ArrayList;

import core.Board;
import core.GameState;
import core.Move;

public class Piece {
    private boolean color; // white = true; black = false
    private String symbol;

    public Piece(boolean color, String symbol) {
        this.color = color;
        this.symbol = symbol;

        if (this.color) { this.symbol = this.symbol.toUpperCase();}
    }

    public void setColor(boolean color) {
        this.color = color;
    }

    public boolean getColor() {
        return this.color;
    }

    public String getSymbol() {
        return this.symbol;
    }

    public ArrayList<Integer> getSlidingPseudoLegalMoves(int startSquare, Piece[] board, int[] directions) {
        ArrayList<Integer> moves = new ArrayList<>();
        int row = startSquare / 8; 
        int col = startSquare % 8; 

        for (int dir : directions) {
            int numSquaresToEdge = 0;

            if (dir == 8)  numSquaresToEdge = 7 - row;                    // Down
            if (dir == -8) numSquaresToEdge = row;                        // Up
            if (dir == 1)  numSquaresToEdge = 7 - col;                    // Right
            if (dir == -1) numSquaresToEdge = col;                        // Left
            if (dir == 7)  numSquaresToEdge = Math.min(7 - row, col);     // Down-Left
            if (dir == 9)  numSquaresToEdge = Math.min(7 - row, 7 - col); // Down-Right
            if (dir == -7) numSquaresToEdge = Math.min(row, 7 - col);     // Up-Right
            if (dir == -9) numSquaresToEdge = Math.min(row, col);         // Up-Left

            for (int j = 1; j <= numSquaresToEdge; j++) {
                int targetSquare = startSquare + (dir * j);                
                if (targetSquare < 0 || targetSquare >= 64) break; // to prevent out of bounds
                Piece targetPiece = board[targetSquare];

                if (targetPiece == null) { // empty square
                    moves.add(Move.encode(startSquare, targetSquare, Move.QUIET_MOVE));
                } else {
                    if (targetPiece.getColor() != this.getColor()) { // if enemy: capture, then stop; if friend: just stop
                        moves.add(Move.encode(startSquare, targetSquare, Move.CAPTURE));
                    }

                    break;
                }
            }
        }
        return moves;
    }

    public ArrayList<Integer> getPseudoLegalMoves(int startSquare, Board game, GameState boardGameState) { return null; }


}
