package pieces;
import java.util.ArrayList;

import core.Board;
import core.GameState;
import core.Move;

public class Knight extends Piece {
    public Knight(boolean color) {
        super(color, "n");
    }

    public ArrayList<Integer> getPseudoLegalMoves(int startSquare, Board game, GameState boardGameState) { 
        Piece[] board = game.getGameBoard();
        ArrayList<Integer> moves = new ArrayList<>();
        int[] directions = {10, -10, 15, -15, 17, -17, 6, -6};
        int row = startSquare / 8; 
        int col = startSquare % 8; 
        int targetSquare = 0;

        for (int i=0; i<directions.length; i++) {
            targetSquare = startSquare + directions[i];
            if (targetSquare > 63 || targetSquare < 0) continue;

            int targetRow = targetSquare / 8;
            int targetCol = targetSquare % 8;

            // knight always moves 2 rows + 1 col OR 1 row + 2 cols
            // sum must be 3 and neither row/col must be 0
            if (Math.abs(targetRow - row) + Math.abs(targetCol - col) != 3 || Math.abs(targetRow - row) == 0 || Math.abs(targetCol - col) == 0) continue;
        
            Piece targetPiece = board[targetSquare];
            if (targetPiece == null) {
                moves.add(Move.encode(startSquare, targetSquare, Move.QUIET_MOVE));
            } else if (targetPiece.getColor() != this.getColor()) {
                moves.add(Move.encode(startSquare, targetSquare, Move.CAPTURE));
            }
        
        }

        return moves;
    }
}
