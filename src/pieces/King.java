package pieces;
import java.util.ArrayList;

import core.Board;
import core.GameState;
import core.Move;

public class King extends Piece {
    public King(boolean color) {
        super(color, "k");
    }

    public ArrayList<Integer> getPseudoLegalMoves(int startSquare, Board game, GameState boardGameState) { 
        Piece[] board = game.getGameBoard();
        int[] directions = {8, -8, 1, -1, 9, -9, 7, -7}; 
        
        ArrayList<Integer> moves = new ArrayList<>();
        int row = startSquare / 8; 
        int col = startSquare % 8; 

        for (int dir : directions) {
            int targetSquare = startSquare + dir;
            if (targetSquare < 0 || targetSquare >= 64) continue;

            int targetRow = targetSquare / 8;
            int targetCol = targetSquare % 8;
            if (Math.abs(targetRow - row) > 1 || Math.abs(targetCol - col) > 1) continue;

            Piece targetPiece = board[targetSquare];

            if (targetPiece == null) { // empty square
                moves.add(Move.encode(startSquare, targetSquare, Move.QUIET_MOVE));
            } else if (targetPiece.getColor() != this.getColor()) {
                moves.add(Move.encode(startSquare, targetSquare, Move.CAPTURE));
            }
        }

        // CASTLING
        if (this.getColor() && !game.isSquareAttacked(60, false)) { // if is white + not in check
            if (startSquare == 60 && !boardGameState.searchMovedPiecesForPiece(this)) {   // on starting square and hasn't moved
                if (                                                         // --- short castle ---
                    board[63] != null &&                                     // square 63 occupied
                    board[63] instanceof Rook &&                             // Rook on square 63
                    !boardGameState.searchMovedPiecesForPiece(board[63]) &&  // the piece hasn't moved
                    (board[61] == null && board[62] == null) &&              // squares between are empty
                    (!game.isSquareAttacked(61, false) && !game.isSquareAttacked(62, false)) // squares not attacked
                ) {
                moves.add( Move.encode(startSquare, 62, Move.SHORT_CASTLE) );
                } 
                
                if (                                                        // --- long castle ---
                    board[56] != null &&                                    // square 56 (bottom left) occupied
                    board[56] instanceof Rook &&                            // occupied by white Rook
                    !boardGameState.searchMovedPiecesForPiece(board[56]) && // the piece hasn't moved
                    (board[57] == null && board[58] == null && board[59] == null) && // no pieces in between
                    (!game.isSquareAttacked(59, false) && !game.isSquareAttacked(58, false))
                ) { 
                    moves.add( Move.encode(startSquare, 58, Move.LONG_CASTLE) );
                }
            } 
        } 

        if (!this.getColor() && !game.isSquareAttacked(4, true)) { // if is black + not in check
            if (startSquare == 4 && !boardGameState.searchMovedPiecesForPiece(this)) { // on starting square and hasn't moved
                
            
                if ( // short castle
                    board[7] != null && 
                    board[7] instanceof Rook && 
                    !boardGameState.searchMovedPiecesForPiece(board[7]) && 
                    (board[6] == null && board[5] == null) &&
                    (!game.isSquareAttacked(5, true) && !game.isSquareAttacked(6, true))
                ) {
                    moves.add( Move.encode(startSquare, 6, Move.SHORT_CASTLE) );
                } 
                    

                if (
                    board[0] != null && 
                    board[0] instanceof Rook && 
                    !boardGameState.searchMovedPiecesForPiece(board[0]) && 
                    (board[1] == null && board[2] == null && board[3] == null) &&
                    (!game.isSquareAttacked(3, true) && !game.isSquareAttacked(2, true))
                ) { // long castle 
                    moves.add( Move.encode(startSquare, 2, Move.LONG_CASTLE) );
                }
            } 
        } 
        

        return moves;
    }
}
