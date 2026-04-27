package pieces;
import java.util.ArrayList;

import core.Board;
import core.GameState;
import core.Move;

public class Pawn extends Piece {
    public Pawn(boolean color) {
        super(color, "p");
    }

    public ArrayList<Integer> getPseudoLegalMoves(int startSquare, Board game, GameState boardGameState) { 
        Piece[] board = game.getGameBoard();
        ArrayList<Integer> moves = new ArrayList<>();
        int row = startSquare / 8;
        int col = startSquare % 8;

        // figure out direction 
        int forward = this.getColor() ? -8 : 8; // white = -8; black = +8
        int startingRow = this.getColor() ? 6 : 1; // starting row is 6 for white; 1 for black

        // forward moves
        int targetSquare = startSquare + forward;

        if (targetSquare >= 0 && targetSquare <= 63 && board[targetSquare] == null) { // check if 0<=x<=63 AND target square is empty
            if (targetSquare <= 7 && this.getColor() == true) {
                moves.add(Move.encode(startSquare, targetSquare, Move.PROMOTION_QUIET));
            } else if (targetSquare >= 56 && this.getColor() == false) {
                moves.add(Move.encode(startSquare, targetSquare, Move.PROMOTION_QUIET));
            } else {
                moves.add(Move.encode(startSquare, targetSquare, Move.QUIET_MOVE));
            }


            int doubleTarget = startSquare + (forward * 2);
            if (row == startingRow && board[doubleTarget] == null && !boardGameState.searchMovedPiecesForPiece(this)) { // if on starting square AND double jump square is empty
                moves.add(Move.encode(startSquare, doubleTarget, Move.DOUBLE_PAWN));
            }
        }

        // captures
        int[] captureOffsets = {forward - 1, forward + 1};
        
        for (int i : captureOffsets) {
            targetSquare = startSquare + i;
            if (targetSquare < 0 || targetSquare > 63) continue;

            int targetCol = targetSquare % 8; 
            if (Math.abs(targetCol - col) != 1) continue; // prevents wrap-around -- absolute difference must be 1 (changes column by exactly +/- 1)

            Piece targetPiece = board[targetSquare];
            
            if (targetPiece != null && targetPiece.getColor() != this.getColor())
            {
                if (targetSquare >= 0 && targetSquare <= 7 && this.getColor() == true) {
                    moves.add(Move.encode(startSquare, targetSquare, Move.PROMOTION_CAPTURE));
                } else if (targetSquare >= 56 && targetSquare <= 63 && this.getColor() == false) {
                    moves.add(Move.encode(startSquare, targetSquare, Move.PROMOTION_CAPTURE));
                } else { // check if it's enemy piece
                    moves.add(Move.encode(startSquare, targetSquare, Move.CAPTURE));
                }
            }
            
        }

        // en passant
        // if (boardGameState.getMoveRecord().size() > 0) {
        //     int lastMove = boardGameState.getMoveRecord().get(boardGameState.getMoveRecord().size() - 1);
        //     if (Move.getFlags(lastMove) == Move.DOUBLE_PAWN) {
        //         int lastEnd = Move.getEnd(lastMove);
        //         int lastFile = lastEnd % 8;
        //         int lastRank = lastEnd / 8;
    
        //         // same row?
        //         if (lastRank == row) {
        //             // same col?
        //             if (Math.abs(lastFile - col) == 1) {                    
        //                 if (this.getColor()) { // White
        //                     targetSquare = lastEnd - 8; 
        //                 } else { // Black
        //                     targetSquare = lastEnd + 8;
        //                 }
    
        //                 moves.add(Move.encode(startSquare, targetSquare, Move.EN_PASSANT));
        //             }
        //         }
        //     }
        // }

        int epSquare = boardGameState.getEnPassantSquare();
        if (epSquare != -1) {
            int epCol = epSquare % 8;
            int epRow = epSquare / 8;

            if (Math.abs(epCol - col) == 1) {
                if (this.getColor() && row == 3 && epRow == 2) {
                    moves.add(Move.encode(startSquare, epSquare, Move.EN_PASSANT));
                } 
                else if (!this.getColor() && row == 4 && epRow == 5) {
                    moves.add(Move.encode(startSquare, epSquare, Move.EN_PASSANT));
                }
            }
        }

        return moves;
    }
}
