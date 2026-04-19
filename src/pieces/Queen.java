package pieces;
import java.util.ArrayList;

import core.Board;
import core.GameState;

public class Queen extends Piece {
    public Queen(boolean color) {
        super(color, "q");
    }

    public ArrayList<Integer> getPseudoLegalMoves(int startSquare, Board game, GameState boardGameState) { 
        Piece[] board = game.getGameBoard();
        int[] directions = {8, -8, 1, -1, 9, -9, 7, -7};
        return this.getSlidingPseudoLegalMoves(startSquare, board, directions);
    }
}