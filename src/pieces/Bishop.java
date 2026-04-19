package pieces;
import java.util.ArrayList;

import core.Board;
import core.GameState;

public class Bishop extends Piece {
    public Bishop(boolean color) {
        super(color, "b");
    }

    public ArrayList<Integer> getPseudoLegalMoves(int startSquare, Board game, GameState boardGameState) { 
        Piece[] board = game.getGameBoard();
        int[] directions = {9, -9, 7, -7};
        return this.getSlidingPseudoLegalMoves(startSquare, board, directions);
    }
}
