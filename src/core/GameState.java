package core;
import java.util.ArrayList;

import pieces.King;
import pieces.Pawn;
import pieces.Piece;

public class GameState {
    private boolean turn;
    private ArrayList<Integer> moveRecord;
    private int ply;
    private ArrayList<Piece> movedPieces;
    private ArrayList<Piece> capturedPieceHistory;
    private ArrayList<Long> hashHistory;

    private ArrayList<Integer> epSquareHistory;
    private ArrayList<Integer> castlingHistory;
    
    private int whiteKingSquare;
    private int blackKingSquare;

    private int whiteNonPawns;
    private int blackNonPawns;

    public GameState() {
        this.turn = true;
        this.ply = 0;
        this.movedPieces = new ArrayList<>();
        this.moveRecord = new ArrayList<>();
        this.capturedPieceHistory = new ArrayList<>();
        this.hashHistory = new ArrayList<>();

        this.epSquareHistory = new ArrayList<>();
        this.castlingHistory = new ArrayList<>();

        this.whiteNonPawns = 0;
        this.blackNonPawns = 0;
    }

    public boolean getTurn() { return this.turn; }
    public ArrayList<Integer> getMoveRecord() { return this.moveRecord; }
    public int getPly() { return this.ply; }
    public ArrayList<Piece> getMovedPieces() { return this.movedPieces; }

    public void switchTurn() { 
        this.turn = !this.turn; 
    }

    public void changePly(int increment) {
        this.ply += increment;
    }

    public void addMovetoRecord(int move) {
        this.moveRecord.add(move);
    }

    public void addMovedPiece(Piece piece) {
        this.movedPieces.add(piece);
    }

    public int getEnPassantSquare() {
        if (epSquareHistory.isEmpty()) return -1;
        return epSquareHistory.get(epSquareHistory.size() - 1);
    }

    public int getCastlingRights() {
        if (castlingHistory.isEmpty()) return 0;
        return castlingHistory.get(castlingHistory.size() - 1);
    }

    public boolean searchMovedPiecesForPiece(Piece piece) {
        if (this.movedPieces.contains(piece)) return true;

        return false;
    }

    public int getNonPawnCount() {
        if (this.turn) {
            return whiteNonPawns;
        } else {
            return blackNonPawns;
        }
    }

    public void pushState(int epSquare, int castlingRights) {
        this.epSquareHistory.add(epSquare);
        this.castlingHistory.add(castlingRights);
    }

    public void popState() {
        if (epSquareHistory.size() > 1) { // Keep the initial state
            this.epSquareHistory.remove(epSquareHistory.size() - 1);
            this.castlingHistory.remove(castlingHistory.size() - 1);
        }
    }

    public void pushCapture(Piece p) {
        this.capturedPieceHistory.add(p);

        if (p != null && !(p instanceof Pawn) && !(p instanceof King)) {
            if (p.getColor()) {
                whiteNonPawns--;
            } else {
                blackNonPawns--;
            }
        }
    }

    public Piece popCapture() {
        if (this.capturedPieceHistory.isEmpty()) return null;
        Piece p = this.capturedPieceHistory.get(this.capturedPieceHistory.size() - 1);
        
        if (p != null && !(p instanceof Pawn) && !(p instanceof King)) {
            if (p.getColor()) {
                whiteNonPawns++;
            } else {
                blackNonPawns++;
            }
        }

        return this.capturedPieceHistory.remove(this.capturedPieceHistory.size() - 1);
    }

    public void pushMovedPiece(Piece piece) { this.movedPieces.add(piece); }
    
    public void popMovedPiece() {
        if (!this.movedPieces.isEmpty()) {
            this.movedPieces.remove(this.movedPieces.size() - 1);
        }
    }

    public void setKingSquare(boolean turn, int square) {
        if (turn) this.whiteKingSquare = square;
        else blackKingSquare = square;
    }

    public int getKingSquare(boolean turn) {
        if (turn) return this.whiteKingSquare;
        return this.blackKingSquare;
    }

    public void pushHash(long hash) {
        hashHistory.add(hash);
    }

    public void popHash() {
        if (!hashHistory.isEmpty()) {
            hashHistory.remove(hashHistory.size() - 1);
        }
    }

    public ArrayList<Long> getHashHistory() {
        return hashHistory;
    } 

    public void addNonPawn(boolean color) {
        if (color) whiteNonPawns++;
        else blackNonPawns++;
    }

    public void removeNonPawn(boolean color) {
        if (color) whiteNonPawns--;
        else blackNonPawns--;
    }

    public boolean isThreefoldRepetition(long currentHash) {
        int count = 0;
        
        for (long h : hashHistory) {
            if (h == currentHash) {
                count++;
            }
        }
        return count >= 3;
    }
}
