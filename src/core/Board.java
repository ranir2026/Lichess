package core;
import java.util.ArrayList;

import pieces.Bishop;
import pieces.King;
import pieces.Knight;
import pieces.Pawn;
import pieces.Piece;
import pieces.Queen;
import pieces.Rook;

public class Board
{
    private Piece[] gameBoard;
    private GameState stateTracker;
    private long currentHash;
    
    public Board(String FEN) {
        this.gameBoard = new Piece[64];
        this.stateTracker = new GameState();

        this.convertFENToPosition(FEN);
    }
    
    private int updateCastlingRights(int start, int end, int flags) {
        int rights = stateTracker.getCastlingRights();
        
        if (gameBoard[start] instanceof King) {
            if (gameBoard[start].getColor()) rights &= ~0b0011; 
            else rights &= ~0b1100;
        }
        
        if (start == 56 || end == 56) rights &= ~0b0010;
        if (start == 63 || end == 63) rights &= ~0b0001;
        if (start == 0 || end == 0) rights &= ~0b1000;
        if (start == 7 || end == 7) rights &= ~0b0100;
        
        return rights;
    }

    private boolean canMoveInDir(int sq, int dir) {
        int row = sq / 8;
        int col = sq % 8;

        if (dir == 8)  return row < 7;         // Down
        if (dir == -8) return row > 0;         // Up
        if (dir == 1)  return col < 7;         // Right
        if (dir == -1) return col > 0;         // Left
        if (dir == 7)  return row < 7 && col > 0; // Down-Left
        if (dir == 9)  return row < 7 && col < 7; // Down-Right
        if (dir == -7) return row > 0 && col < 7; // Up-Right
        if (dir == -9) return row > 0 && col > 0; // Up-Left
        
        return false;
    }

    public boolean isInCheck(boolean color) {
        int kingSquare = this.stateTracker.getKingSquare(color);
        return isSquareAttacked(kingSquare, !color);
    }

    private boolean isCorrectSlider(Piece p, int dir) {
        // If the direction is diagonal (7, -7, 9, -9), only Bishops and Queens can attack
        boolean isDiagonal = (Math.abs(dir) == 7 || Math.abs(dir) == 9);
        
        if (p instanceof Queen) return true; // Queens attack everywhere
        if (isDiagonal) return (p instanceof Bishop); // Diagonals
        return (p instanceof Rook); // Orthogonals
    }

    public void makeMove(int move) {
        // long incrementalHash = this.getCurrentHash();
        // long actualHash = this.calculateManualHash();
        
        // if (incrementalHash != actualHash) {
        //     System.out.println("Move: " + moveToLan(move) + " (Flags: " + Integer.toBinaryString(Move.getFlags(move) >> 12) + ")");
        //     System.out.println("Last move: " + moveToLan(this.stateTracker.getMoveRecord().get(this.stateTracker.getMoveRecord().size() - 1))  );
        //     System.out.println("Board State: " + this.toString());
        //     System.out.println("EP Square: " + this.stateTracker.getEnPassantSquare());
        //     System.out.println("Turn: " + this.stateTracker.getTurn());
        //     throw new RuntimeException("Hash Mismatch"); 
        // }

        int startSquare = Move.getStart(move);
        int endSquare = Move.getEnd(move);
        int flags = Move.getFlags(move);

        Piece movingPiece = this.gameBoard[startSquare];

        currentHash ^= Zobrist.castlingRights[stateTracker.getCastlingRights()];
        currentHash ^= Zobrist.table[Zobrist.getPieceIndex(movingPiece)][startSquare];

        int oldEPSquare = stateTracker.getEnPassantSquare();
        if (oldEPSquare != -1) currentHash ^= Zobrist.enPassantFile[oldEPSquare % 8];

        int newEPSquare = -1;

        if (flags == Move.EN_PASSANT) {
            int victimSquare = (movingPiece.getColor()) ? endSquare + 8 : endSquare - 8; 
            Piece victim = this.gameBoard[victimSquare];
            
            if (victim != null) {   
                currentHash ^= Zobrist.table[Zobrist.getPieceIndex(victim)][victimSquare];
            }

            this.stateTracker.pushCapture(victim);
            this.gameBoard[victimSquare] = null;
        }
        else if (flags == Move.DOUBLE_PAWN) {
            newEPSquare = (startSquare + endSquare) / 2;
            currentHash ^= Zobrist.enPassantFile[newEPSquare % 8];
            this.stateTracker.pushCapture(null);
        }
        else { // normal capture
            Piece target = this.gameBoard[endSquare];
            if (target != null) {
                currentHash ^= Zobrist.table[Zobrist.getPieceIndex(target)][endSquare];
            }
            this.stateTracker.pushCapture(target);
        }

        if (flags == Move.SHORT_CASTLE) {
            int rookStart = startSquare + 3;
            int rookEnd = startSquare + 1;
            Piece rook = this.gameBoard[rookStart];

            currentHash ^= Zobrist.table[Zobrist.getPieceIndex(rook)][rookStart];
            currentHash ^= Zobrist.table[Zobrist.getPieceIndex(rook)][rookEnd];
            
            this.gameBoard[rookEnd] = rook;
            this.gameBoard[rookStart] = null;
        } else if (flags == Move.LONG_CASTLE) {
            int rookStart = startSquare - 4;
            int rookEnd = startSquare - 1;
            Piece rook = this.gameBoard[rookStart];

            currentHash ^= Zobrist.table[Zobrist.getPieceIndex(rook)][rookStart];
            currentHash ^= Zobrist.table[Zobrist.getPieceIndex(rook)][rookEnd];

            this.gameBoard[rookEnd] = rook;
            this.gameBoard[rookStart] = null;
        }

        this.gameBoard[startSquare] = null;
        if (flags == Move.PROMOTION_QUIET || flags == Move.PROMOTION_CAPTURE) {
            Piece queen = new Queen(movingPiece.getColor());
            this.gameBoard[endSquare] = queen;
            this.stateTracker.addNonPawn(movingPiece.getColor());
            currentHash ^= Zobrist.table[Zobrist.getPieceIndex(queen)][endSquare];
        } else {
            this.gameBoard[endSquare] = movingPiece;
            currentHash ^= Zobrist.table[Zobrist.getPieceIndex(movingPiece)][endSquare];
        }

        if (movingPiece instanceof King) { 
            stateTracker.setKingSquare(movingPiece.getColor(), endSquare); 
        }

        currentHash ^= Zobrist.sideToMove;

        int newCastlingRights = updateCastlingRights(startSquare, endSquare, flags);
        currentHash ^= Zobrist.castlingRights[newCastlingRights];

        this.stateTracker.pushState(newEPSquare, newCastlingRights);
        this.stateTracker.pushHash(currentHash);
        this.stateTracker.addMovedPiece(this.gameBoard[endSquare]); 
        this.stateTracker.changePly(1);
        this.stateTracker.addMovetoRecord(move);
        this.stateTracker.switchTurn();
    }

    public void unmakeMove(int move) {
        int start = Move.getStart(move);
        int end = Move.getEnd(move);
        int flags = Move.getFlags(move);

        this.stateTracker.switchTurn();

        this.stateTracker.popHash(); // discard current position's hash
        this.currentHash = this.stateTracker.peekHash(); // restore hash to state before the move
        this.stateTracker.popState();
        this.stateTracker.popMovedPiece();
        this.stateTracker.changePly(-1);

        ArrayList<Integer> record = this.stateTracker.getMoveRecord();
        if (!record.isEmpty()) record.remove(record.size() - 1);

        Piece movedPiece = this.gameBoard[end];
        Piece capturedPiece = this.stateTracker.popCapture();

        if (movedPiece != null) {
            if (flags == Move.PROMOTION_QUIET || flags == Move.PROMOTION_CAPTURE) {
                this.gameBoard[start] = new Pawn(movedPiece.getColor());
                this.stateTracker.removeNonPawn(movedPiece.getColor());
            } else {
                this.gameBoard[start] = movedPiece;
            }
            this.gameBoard[end] = null;
        }

        if (flags == Move.EN_PASSANT) {
            int victimSquare = (movedPiece.getColor()) ? end + 8 : end - 8;
            this.gameBoard[end] = null;
            this.gameBoard[victimSquare] = capturedPiece;
        } else if (capturedPiece != null) {
            this.gameBoard[end] = capturedPiece;
        }

        if (flags == Move.SHORT_CASTLE) {
            int rookStart = start + 3;
            int rookEnd = start + 1;
            Piece rook = this.gameBoard[rookEnd];
            this.gameBoard[rookStart] = rook;
            this.gameBoard[rookEnd] = null;
        } else if (flags == Move.LONG_CASTLE) {
            int rookStart = start - 4;
            int rookEnd = start - 1;
            Piece rook = this.gameBoard[rookEnd];
            this.gameBoard[rookStart] = rook;
            this.gameBoard[rookEnd] = null;
        }

        if (movedPiece instanceof King) {
            stateTracker.setKingSquare(movedPiece.getColor(), start);
        }
    }

    public void makeNullMove() {
        // XOR out the active EP square — null moves forfeit en passant
        int oldEPSquare = stateTracker.getEnPassantSquare();
        if (oldEPSquare != -1) currentHash ^= Zobrist.enPassantFile[oldEPSquare % 8];

        currentHash ^= Zobrist.sideToMove;
        this.stateTracker.switchTurn();

        this.stateTracker.pushHash(currentHash);
        this.stateTracker.pushState(-1, stateTracker.getCastlingRights()); // clear EP for null move
        this.stateTracker.changePly(1);
    }

    public void unmakeNullMove() {
        this.stateTracker.popHash();
        this.stateTracker.popState();
        this.stateTracker.switchTurn();

        this.currentHash = this.stateTracker.peekHash();
        this.stateTracker.changePly(-1);
    }

    public boolean isSquareAttacked(int targetSquare, boolean attackerColor) {
        // check pawns first -- only need to check diagonals
        int pawnDir = attackerColor ? 8 : -8;
        int[] pawnCaptureOffsets = {pawnDir - 1, pawnDir + 1};
        for (int offset : pawnCaptureOffsets) {
            int square = targetSquare + offset;
            if (square >= 0 && square < 64 && Math.abs((square % 8) - (targetSquare % 8)) == 1) {
                Piece p = this.gameBoard[square];
                if (p != null && p.getColor() == attackerColor && p instanceof Pawn) return true;
            }
        }

        // knights
        int[] knightMoves = {10, -10, 15, -15, 17, -17, 6, -6};
        for (int offset : knightMoves) {
            int square = targetSquare + offset;
            if (square >= 0 && square < 64) {
                if (Math.abs((square % 8) - (targetSquare % 8)) <= 2) { // Column wrap check
                    Piece p = gameBoard[square];
                    if (p != null && p.getColor() == attackerColor && p instanceof Knight) return true;
                }
            }
        }

        // sliding pieces
        int[] slidingDirs = {8, -8, 1, -1, 7, -7, 9, -9};
        for (int dir : slidingDirs) {
            int sq = targetSquare;
            while (true) {
                if (!canMoveInDir(sq, dir)) break; 
                sq += dir;
                
                Piece p = gameBoard[sq];
                if (p != null) {
                    if (p.getColor() == attackerColor) {
                        if (isCorrectSlider(p, dir)) return true;
                    }
                    break;
                }
            }
        }

        // king
        int[] kingDirs = {8, -8, 1, -1, 7, -7, 9, -9};
        for (int dir : kingDirs) {
            int sq = targetSquare + dir;
            if (sq >= 0 && sq < 64 && Math.abs((sq % 8) - (targetSquare % 8)) <= 1) {
                Piece p = gameBoard[sq];
                if (p != null && p.getColor() == attackerColor && p instanceof King) return true;
            }
        }

        return false;
    }

    public ArrayList<Integer> getLegalMoves(boolean color) {
        ArrayList<Integer> legalMoves = new ArrayList<>();

        for (int i=0; i<64; i++) {
            Piece piece = gameBoard[i];
            if (piece == null || piece.getColor() != color) continue;

            ArrayList<Integer> pseudo = piece.getPseudoLegalMoves(i, this, this.stateTracker);
            for (int move : pseudo) {
                makeMove(move);
                
                int kingSquare = this.stateTracker.getKingSquare(color);

                // if the king is on safe square, then it's legal
                // stateTracker.getTurn() is the enemy color because the move has been made
                if (!isSquareAttacked(kingSquare, stateTracker.getTurn())) {
                    legalMoves.add(move);
                }

                unmakeMove(move);
            }
        }

        return legalMoves;
    }

    
    public ArrayList<Integer> getCaptureMoves(boolean color) {
        ArrayList<Integer> captureMoves = new ArrayList<>();

        for (int i=0; i<64; i++) {
            Piece piece = gameBoard[i];
            if (piece == null || piece.getColor() != color) continue;
            
            ArrayList<Integer> pseudo = piece.getPseudoLegalMoves(i, this, this.stateTracker);
            for (int move : pseudo) {
                int flags = Move.getFlags(move);
                boolean isCapture = flags == Move.CAPTURE ||
                flags == Move.EN_PASSANT ||
                flags == Move.PROMOTION_CAPTURE;
                
                if (isCapture) {
                    // same logic as legal move generation with an added boolean check
                    makeMove(move);
                    int kingSquare = this.stateTracker.getKingSquare(color);
                    if (!isSquareAttacked(kingSquare, stateTracker.getTurn())) {
                        captureMoves.add(move);
                    }
                    
                    unmakeMove(move);
                }
                
            }
        }
        
        return captureMoves;
    }
    
    public void convertFENToPosition(String FEN) {
        // https://en.wikipedia.org/wiki/Forsyth%E2%80%93Edwards_Notation
        this.gameBoard = new Piece[64];
        int row = 0;
        int col = 0;
        
        for (char c : FEN.toCharArray()) {
            if (c == '/') { // slashes indicate new rows
                row++;
                col = 0;
            } else if (Character.isDigit(c)) { // numbers indicate consecutive empty squares
                col += Character.getNumericValue(c);
            } else { // otherwise, it's a letter for the piece nmae
                boolean isWhite = Character.isUpperCase(c);
                Piece piece = null;
                
                switch (Character.toLowerCase(c)) {
                    case 'p': piece = new Pawn(isWhite); break;
                    case 'r': piece = new Rook(isWhite); break;
                    case 'n': piece = new Knight(isWhite); break;
                    case 'b': piece = new Bishop(isWhite); break;
                    case 'q': piece = new Queen(isWhite); break;
                    case 'k': 
                    piece = new King(isWhite);
                    this.stateTracker.setKingSquare(isWhite, row * 8 + col); 
                    break;
                }
                
                if (!(piece instanceof Pawn) && !(piece instanceof King)) this.stateTracker.addNonPawn(isWhite);

                this.gameBoard[row * 8 + col] = piece;
                col++;
            }
        }

        this.currentHash = 0L;
        for (int i = 0; i < 64; i++) {
            Piece p = gameBoard[i];
            if (p != null) {
                this.currentHash ^= Zobrist.table[Zobrist.getPieceIndex(p)][i];
            }
        }
        
        if (!this.stateTracker.getTurn()) {
            this.currentHash ^= Zobrist.sideToMove;
        }

        int initialRights = this.stateTracker.getCastlingRights();
        this.currentHash ^= Zobrist.castlingRights[initialRights];

        int epSquare = this.stateTracker.getEnPassantSquare();
        if (epSquare != -1) {
            this.currentHash ^= Zobrist.enPassantFile[epSquare % 8];
        }
        
        this.stateTracker.pushHash(this.currentHash);
    }
    
    public String toString() {
        String pretty_output = "";
        
        for (int i=0; i<this.gameBoard.length; i++) {
            if (i % 8 == 0) {
                pretty_output += "\n";
            }
            
            if (this.gameBoard[i] == null) { pretty_output += ". "; continue; }
            
            pretty_output += this.gameBoard[i].getSymbol() + " ";
        }
        
        return pretty_output;
    }
    
    public Piece[] getGameBoard() { 
        return this.gameBoard; 
    }
    
    public GameState getStateTracker() {
        return this.stateTracker;
    }
    
    public long getCurrentHash() {
        return this.currentHash;
    }

    public long calculateManualHash() {
        long hash = 0L;

        for (int i = 0; i < 64; i++) {
            Piece p = this.gameBoard[i];
            if (p != null) {
                int pieceIdx = Zobrist.getPieceIndex(p);
                hash ^= Zobrist.table[pieceIdx][i];
            }
        }

        if (!this.stateTracker.getTurn()) {
            hash ^= Zobrist.sideToMove;
        }

        int castlingRights = this.stateTracker.getCastlingRights();
        hash ^= Zobrist.castlingRights[castlingRights];

        int epSquare = this.stateTracker.getEnPassantSquare();
        if (epSquare != -1) {
            hash ^= Zobrist.enPassantFile[epSquare % 8];
        }

        return hash;
    }

}