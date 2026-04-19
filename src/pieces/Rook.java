package pieces;
import java.util.ArrayList;

import core.Board;
import core.GameState;

public class Rook extends Piece {
    public Rook(boolean color) {
        super(color, "r");
    }

    public ArrayList<Integer> getPseudoLegalMoves(int startSquare, Board game, GameState boardGameState) { 
        Piece[] board = game.getGameBoard();
        int[] directions = {8, -8, 1, -1};
        return this.getSlidingPseudoLegalMoves(startSquare, board, directions);
    }
    
}
