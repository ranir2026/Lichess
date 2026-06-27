package bitboardRewrite;

public class Board {
    // to avoid garbage collection, use an array of long type
    // binary numbers to represent different aspects of the
    // board. each one is called a 'bitboard' and it uses 0s
    // to represent vacant squares, and 1s to represent 
    // occupied ones.

    // pieceBitboards contains an individual bitboard for
    // each type of piece per color, and you can access
    // one of these bitboards with the constant indicies
    long[] pieceBitboards = new long[12];
    
    public static final int WP = 0;
    public static final int WN = 1;
    public static final int WB = 2;
    public static final int WR = 3;
    public static final int WQ = 4;
    public static final int WK = 5;

    public static final int BP = 6;
    public static final int BN = 7;
    public static final int BB = 8;
    public static final int BR = 9;
    public static final int BQ = 10;
    public static final int BK = 11;
    
    // for ease of access, we can have bitboards to represent
    // ALL the white pieces, or ALL the black pieces, or
    // ALL the pieces which can efficiently be made by using
    // the OR operator --> |
    // 100 | 010 = 110
    long whitePieces;
    long blackPieces;
    long allPieces;

    public static final int WHITE = 0;
    public static final int BLACK = 1;
    public int turn;

    // castling and en passant are two of the most annoying
    // moves to implement when programming chess since they
    // deviate from the standard rules for pieces. 
    // there will always be *at most* 1 en passant square. 
    // castling rights: QKqk
    public int enPassantSquare;
    public int castlingRights;

    private int[] historyCastling = new int[2048];
    private int[] historyEP = new int[2048];
    private int[] historyCapture = new int[2048];
    private int[] historyHalfmove = new int[2048];
    private long[] historyHash = new long[2048];
    private int historyPtr = 0;
    private int halfmoveClock = 0;

    public int[] boardArray = new int[64];
    public long currentHash;

    public void makeMove(int move) {
        // separate the move's components
        int from = Move.getStart(move);
        int to = Move.getEnd(move);
        int flags = Move.getFlags(move);
        int movingPiece = boardArray[from];
        int capturedPiece = -1;

        // if the move captures a piece, get the captured piece
        if (flags == Move.EN_PASSANT) {
            capturedPiece = (turn == WHITE) ? BP : WP;
        } else if (Move.isCapture(move)) {
            capturedPiece = boardArray[to];
        }

        // update the big arrays for move history
        historyCastling[historyPtr] = castlingRights;
        historyEP[historyPtr] = enPassantSquare;
        historyCapture[historyPtr] = capturedPiece;
        historyHalfmove[historyPtr] = halfmoveClock;
        historyHash[historyPtr] = currentHash;
        historyPtr++;

        // XOR out old castling rights, old en-passant and moving piece-from from the hash
        currentHash ^= Zobrist.castlingKeys[castlingRights];
        if (enPassantSquare != -1) currentHash ^= Zobrist.enPassantKeys[enPassantSquare % 8];
        currentHash ^= Zobrist.pieceKeys[movingPiece][from];

        // handle captures -- update bitboards/array and remove captured piece from hash
        if (capturedPiece != -1) {
            int capSq = to;
            if (flags == Move.EN_PASSANT) {
                capSq = (turn == WHITE) ? to - 8 : to + 8;
            }
            pieceBitboards[capturedPiece] ^= (1L << capSq);
            boardArray[capSq] = -1;
            currentHash ^= Zobrist.pieceKeys[capturedPiece][capSq];
        }

        // move the piece -- remove 1 from the starting bit and array
        pieceBitboards[movingPiece] ^= (1L << from);
        boardArray[from] = -1;

        // flags >= Move.PROMOTION_KNIGHT will be true for any promotion
        if (flags >= Move.PROMOTION_KNIGHT) {
            // get promotion type
            int promoType;
            if (flags == Move.PROMOTION_QUEEN || flags == Move.PROMO_CAP_QUEEN) promoType = (turn == WHITE) ? WQ : BQ;
            else if (flags == Move.PROMOTION_ROOK || flags == Move.PROMO_CAP_ROOK) promoType = (turn == WHITE) ? WR : BR;
            else if (flags == Move.PROMOTION_BISHOP || flags == Move.PROMO_CAP_BISHOP) promoType = (turn == WHITE) ? WB : BB;
            else promoType = (turn == WHITE) ? WN : BN;
            
            // update the boardArray and bitboards with the new piece
            pieceBitboards[promoType] |= (1L << to);
            boardArray[to] = promoType;

            // XOR in promoted piece to the hash
            currentHash ^= Zobrist.pieceKeys[promoType][to];
        } else {
            // toggle the bit of the landing square for the bitboard and update boardArray + hash
            pieceBitboards[movingPiece] |= (1L << to);
            boardArray[to] = movingPiece;
            currentHash ^= Zobrist.pieceKeys[movingPiece][to];
        }

        // castling logic
        if (flags == Move.SHORT_CASTLE) {
            int rFrom = (turn == WHITE) ? 7 : 63; // white's kingside rook is on 7, black's on 63
            int rTo = (turn == WHITE) ? 5 : 61; // white's kingside rook lands on 5, black's on 61
            int rook = (turn == WHITE) ? WR : BR; // get the rook index

            // update bitboards, boardArray, and hash
            pieceBitboards[rook] ^= (1L << rFrom);
            pieceBitboards[rook] ^= (1L << rTo);
            boardArray[rFrom] = -1;
            boardArray[rTo] = rook;
            currentHash ^= Zobrist.pieceKeys[rook][rFrom];
            currentHash ^= Zobrist.pieceKeys[rook][rTo];
        } else if (flags == Move.LONG_CASTLE) {
            int rFrom = (turn == WHITE) ? 0 : 56; // white's queenside rook is on 0, black's on 56
            int rTo = (turn == WHITE) ? 3 : 59; // white's queenside rook lands on 3, black's on 59
            int rook = (turn == WHITE) ? WR : BR; // get the rook index

            // update bitboards, boardArray, and hash
            pieceBitboards[rook] ^= (1L << rFrom);
            pieceBitboards[rook] ^= (1L << rTo);
            boardArray[rFrom] = -1;
            boardArray[rTo] = rook;
            currentHash ^= Zobrist.pieceKeys[rook][rFrom];
            currentHash ^= Zobrist.pieceKeys[rook][rTo];
        }

        // update the castling rights (regardless of if the move was a castle, since the rook could've moved, etc)
        updateCastlingRights(from, to, movingPiece);

        // update the halfmove clock: reset after a capture or pawn move
        if (capturedPiece != -1 || movingPiece == WP || movingPiece == BP) {
            halfmoveClock = 0;
        } else {
            halfmoveClock++;
        }
        
        // update the en passant square and the file hash
        // the en passant square will always be the square
        // behind the pawn that just pushed (if it went 2 spaces forward)
        enPassantSquare = -1;
        if (flags == Move.DOUBLE_PAWN) {
            enPassantSquare = (turn == WHITE) ? from + 8 : from - 8;
            currentHash ^= Zobrist.enPassantKeys[enPassantSquare % 8];
        }

        // flip side and XOR side key
        currentHash ^= Zobrist.sideToMove;
        turn = 1 - turn;
        currentHash ^= Zobrist.castlingKeys[castlingRights]; // XOR in new castling rights after update
        updateOccupancies();
    }

    public void unmakeMove(int move) {
        // restore previous hash and saved states
        turn = 1 - turn;
        historyPtr--;
        currentHash = historyHash[historyPtr];
        int oldCastling = historyCastling[historyPtr];
        int oldEP = historyEP[historyPtr];
        int capturedPiece = historyCapture[historyPtr];
        
        // extract move info
        int from = Move.getStart(move);
        int to = Move.getEnd(move);
        int flags = Move.getFlags(move);
        int movedPiece = boardArray[to];
        
        // to undo promotions, remove the bit from the promoted piece's bitboard, and put a pawn back
        if (flags >= Move.PROMOTION_KNIGHT) {
            movedPiece = (turn == WHITE) ? WQ : BQ; // Default check Queen
            if ((pieceBitboards[(turn == WHITE) ? WQ : BQ] & (1L << to)) != 0) movedPiece = (turn == WHITE) ? WQ : BQ;
            else if ((pieceBitboards[(turn == WHITE) ? WR : BR] & (1L << to)) != 0) movedPiece = (turn == WHITE) ? WR : BR;
            else if ((pieceBitboards[(turn == WHITE) ? WB : BB] & (1L << to)) != 0) movedPiece = (turn == WHITE) ? WB : BB;
            else movedPiece = (turn == WHITE) ? WN : BN;

            pieceBitboards[movedPiece] ^= (1L << to);
            int pawn = (turn == WHITE) ? WP : BP;
            pieceBitboards[pawn] |= (1L << from);
            boardArray[from] = pawn;
            boardArray[to] = -1;
        } else {
            // restoring normal moves is the same logic as making a move with different square indicies
            pieceBitboards[movedPiece] ^= (1L << to);
            pieceBitboards[movedPiece] |= (1L << from);
            boardArray[from] = movedPiece;
            boardArray[to] = -1;
        }

        // restoring captures
        if (capturedPiece != -1) {
            int capSq = to;
            if (flags == Move.EN_PASSANT) {
                capSq = (turn == WHITE) ? to - 8 : to + 8;
            }
            pieceBitboards[capturedPiece] |= (1L << capSq);
            boardArray[capSq] = capturedPiece;
        }

        // undoing castling
        if (flags == Move.SHORT_CASTLE) {
            int rFrom = (turn == WHITE) ? 7 : 63;
            int rTo = (turn == WHITE) ? 5 : 61;
            int rook = (turn == WHITE) ? WR : BR;
            pieceBitboards[rook] ^= (1L << rFrom);
            pieceBitboards[rook] ^= (1L << rTo);
            boardArray[rFrom] = rook;
            boardArray[rTo] = -1;
        } else if (flags == Move.LONG_CASTLE) {
            int rFrom = (turn == WHITE) ? 0 : 56;
            int rTo = (turn == WHITE) ? 3 : 59;
            int rook = (turn == WHITE) ? WR : BR;
            pieceBitboards[rook] ^= (1L << rFrom);
            pieceBitboards[rook] ^= (1L << rTo);
            boardArray[rFrom] = rook;
            boardArray[rTo] = -1;
        }

        // restore state variables
        this.castlingRights = oldCastling;
        this.enPassantSquare = oldEP;
        this.halfmoveClock = historyHalfmove[historyPtr];
        updateOccupancies();
    }

    private void updateCastlingRights(int from, int to, int piece) {
        // if king moves, lose all rights for that side
        if (piece == WK) castlingRights &= ~3;
        else if (piece == BK) castlingRights &= ~12;
        
        // if rooks move or are captured
        if (from == 0 || to == 0) castlingRights &= ~2; // white queenside
        if (from == 7 || to == 7) castlingRights &= ~1; // white kingside
        if (from == 56 || to == 56) castlingRights &= ~8; // black queenside
        if (from == 63 || to == 63) castlingRights &= ~4; // black kingside
    }

    public int getPieceAt(int square) {
        // loop over every bitboard and check the bit at the given square for a match

        long bit = 1L << square;
        for (int i = 0; i < 12; i++) {
            if ((pieceBitboards[i] & bit) != 0) return i;
        }
        return -1;
    }

    public void updateOccupancies() {
        // combine all the individual piece bitboards into bitboards for each side
        // combine the individual side bitboards into 1 big bitboards for all pieces
        whitePieces = pieceBitboards[WP] | pieceBitboards[WR] | pieceBitboards[WN]
                    | pieceBitboards[WB] | pieceBitboards[WQ] | pieceBitboards[WK];
        blackPieces = pieceBitboards[BP] | pieceBitboards[BR] | pieceBitboards[BN]
                    | pieceBitboards[BB] | pieceBitboards[BQ] | pieceBitboards[BK];
        allPieces = whitePieces | blackPieces;
    }

    public boolean isSquareAttacked(int square, int attackingSide, long occupancy) {
        // check whether any of the pawns attack the square
        int pawnIdx = (attackingSide == WHITE) ? WP : BP;
        long pawns = pieceBitboards[pawnIdx];
        long pawnAttacksMask = 0L;
        while (pawns != 0) {
            int p = Long.numberOfTrailingZeros(pawns);
            pawns &= pawns - 1;
            pawnAttacksMask |= AttackTables.pawnAttacks[attackingSide][p];
        }
        if ((pawnAttacksMask & (1L << square)) != 0) return true;

        // check the knight attack tables
        int enemyKnight = (attackingSide == WHITE) ? WN : BN;
        if ((AttackTables.knightAttacks[square] & pieceBitboards[enemyKnight]) != 0) return true;

        // check the king attacks
        int enemyKing = (attackingSide == WHITE) ? WK : BK;
        if ((AttackTables.kingAttacks[square] & pieceBitboards[enemyKing]) != 0) return true;

        // diagonals
        int enemyBishop = (attackingSide == WHITE) ? WB : BB;
        int enemyQueen = (attackingSide == WHITE) ? WQ : BQ;
        long diagonalSliders = pieceBitboards[enemyBishop] | pieceBitboards[enemyQueen];
        if ((AttackTables.getBishopAttacks(square, occupancy) & diagonalSliders) != 0) return true;

        // orthogonals
        int enemyRook = (attackingSide == WHITE) ? WR : BR;
        long orthogonalSliders = pieceBitboards[enemyRook] | pieceBitboards[enemyQueen];
        if ((AttackTables.getRookAttacks(square, occupancy) & orthogonalSliders) != 0) return true;

        return false;
    }

    public long getCheckers() {
        // similar to isSquareAttacked but returns bitmask of pieces checking the king
        int kingType = (turn == WHITE) ? WK : BK;
        long kingBB = pieceBitboards[kingType];
        if (kingBB == 0L) return 0L; // defensive: no king found
        int kingSquare = Long.numberOfTrailingZeros(kingBB);
        int opponent = 1 - turn;
        long checkers = 0L;

        int oppPawnIdx = (opponent == WHITE) ? WP : BP;
        long oppPawns = pieceBitboards[oppPawnIdx];
        long pawnCheckers = 0L;
        while (oppPawns != 0) {
            int p = Long.numberOfTrailingZeros(oppPawns);
            oppPawns &= oppPawns - 1;
            if ((AttackTables.pawnAttacks[opponent][p] & (1L << kingSquare)) != 0) pawnCheckers |= (1L << p);
        }
        checkers |= pawnCheckers;

        checkers |= (AttackTables.knightAttacks[kingSquare] & pieceBitboards[(opponent == WHITE) ? WN : BN]);
        long slidingAttacks = (AttackTables.getBishopAttacks(kingSquare, allPieces) & (pieceBitboards[(opponent == WHITE) ? WB : BB] | pieceBitboards[(opponent == WHITE) ? WQ : BQ]))
                            | (AttackTables.getRookAttacks(kingSquare, allPieces) & (pieceBitboards[(opponent == WHITE) ? WR : BR] | pieceBitboards[(opponent == WHITE) ? WQ : BQ]));
        
        return checkers | slidingAttacks;
    }

    public void makeNullMove() {
        historyCastling[historyPtr] = castlingRights;
        historyEP[historyPtr] = enPassantSquare;
        historyCapture[historyPtr] = -1; // No piece captured
        historyHalfmove[historyPtr] = halfmoveClock;
        historyHash[historyPtr] = currentHash;
        historyPtr++;

        currentHash ^= Zobrist.castlingKeys[castlingRights];

        if (enPassantSquare != -1) {
            currentHash ^= Zobrist.enPassantKeys[enPassantSquare % 8];
        }
        enPassantSquare = -1;

        currentHash ^= Zobrist.sideToMove;
        turn = 1 - turn;
        currentHash ^= Zobrist.castlingKeys[castlingRights];
    }

    public void unmakeNullMove() {
        historyPtr--;
        
        turn = 1 - turn;

        castlingRights = historyCastling[historyPtr];
        enPassantSquare = historyEP[historyPtr];
        halfmoveClock = historyHalfmove[historyPtr];
        currentHash = historyHash[historyPtr];
    }

    public String squareToCoordinates(int square) {
        char file = (char) ('a' + (square % 8));
        char rank = (char) ('1' + (square / 8));

        return "" + file + rank;
    }

    public long getCurrentHash() {
        return currentHash;
    }

    // Return the king square for the given color (Board.WHITE or Board.BLACK), or -1 if missing
    public int getKingSquare(int color) {
        int kingType = (color == WHITE) ? WK : BK;
        if (pieceBitboards[kingType] == 0L) return -1;
        return Long.numberOfTrailingZeros(pieceBitboards[kingType]);
    }

    public int getMoveCount() {
        return historyPtr;
    }

    public int getHalfmoveClock() {
        return halfmoveClock;
    }

    public int getRepetitionCount(long hash) {
        int count = 0;
        for (int i = 0; i < historyPtr; i++) {
            if (historyHash[i] == hash) count++;
        }
        return count;
    }

    public boolean isThreefoldRepetition(long hash) {
        return getRepetitionCount(hash) >= 2;
    }

    public boolean isThreefoldRepetition() {
        return isThreefoldRepetition(currentHash);
    }

    public boolean isFiftyMoveRule() {
        return halfmoveClock >= 100;
    }

    public int getPieceAtSquare(int square) {
        long mask = 1L << square;
        
        // Loop through all piece type bitboards (WP=0 to BK=11)
        for (int i = 0; i < 12; i++) {
            if ((pieceBitboards[i] & mask) != 0L) {
                return i;
            }
        }
        
        return -1; // Vacant square
    }

    public void calculateFullHash() {
        currentHash = 0L;
        for (int i = 0; i < 12; i++) {
            long pieces = pieceBitboards[i];
            while (pieces != 0) {
                int sq = Long.numberOfTrailingZeros(pieces);
                currentHash ^= Zobrist.pieceKeys[i][sq];
                pieces &= pieces - 1;
            }
        }

        if (turn == WHITE) currentHash ^= Zobrist.sideToMove;
        currentHash ^= Zobrist.castlingKeys[castlingRights];
        if (enPassantSquare != -1) currentHash ^= Zobrist.enPassantKeys[enPassantSquare % 8];
    }

    public void convertFENtoPosition(String fen) {
        for (int i = 0; i < 12; i++) {
            pieceBitboards[i] = 0L;
        }

        for (int i = 0; i < 64; i++) {
            boardArray[i] = -1;
        }

        whitePieces = 0L;
        blackPieces = 0L;
        allPieces = 0L;
        enPassantSquare = -1;
        castlingRights = 0;
        historyPtr = 0;

        String[] parts = fen.split(" ");
        String boardPart = parts[0];

        int rank = 7;
        int file = 0;

        for (int i = 0; i < boardPart.length(); i++) {
            char c = boardPart.charAt(i);

            if (c == '/') {
                rank--;
                file = 0;
            } else if (Character.isDigit(c)) {
                file += c - '0';
            } else {
                int square = rank * 8 + file;
                int pieceType = -1;

                switch (c) {
                    case 'P': pieceType = WP; break;
                    case 'R': pieceType = WR; break;
                    case 'N': pieceType = WN; break;
                    case 'B': pieceType = WB; break;
                    case 'Q': pieceType = WQ; break;
                    case 'K': pieceType = WK; break;
                    case 'p': pieceType = BP; break;
                    case 'r': pieceType = BR; break;
                    case 'n': pieceType = BN; break;
                    case 'b': pieceType = BB; break;
                    case 'q': pieceType = BQ; break;
                    case 'k': pieceType = BK; break;
                }

                if (pieceType != -1) {
                    pieceBitboards[pieceType] |= (1L << square);
                    boardArray[square] = pieceType;
                }

                file++;
            }
        }

        turn = parts[1].equals("w") ? WHITE : BLACK;
        String castling = parts[2];

        if (castling.contains("K")) castlingRights |= 1;
        if (castling.contains("Q")) castlingRights |= 2;
        if (castling.contains("k")) castlingRights |= 4;
        if (castling.contains("q")) castlingRights |= 8;

        String ep = parts[3];
        if (!ep.equals("-")) {
            int epFile = ep.charAt(0) - 'a';
            int epRank = ep.charAt(1) - '1';
            enPassantSquare = epRank * 8 + epFile;
        }

        halfmoveClock = 0;
        if (parts.length > 4) {
            try {
                halfmoveClock = Integer.parseInt(parts[4]);
            } catch (NumberFormatException ignored) {
                halfmoveClock = 0;
            }
        }

        updateOccupancies();
        calculateFullHash();
    }

    public void prettyPrintBitboard() {
        for (int rank = 7; rank >= 0; rank--) {
            for (int file = 0; file < 8; file++) {

                int square = rank * 8 + file;

                char piece = '.';

                if (Bitboards.getBit(pieceBitboards[WP], square)) piece = 'P';
                else if (Bitboards.getBit(pieceBitboards[WN], square)) piece = 'N';
                else if (Bitboards.getBit(pieceBitboards[WB], square)) piece = 'B';
                else if (Bitboards.getBit(pieceBitboards[WR], square)) piece = 'R';
                else if (Bitboards.getBit(pieceBitboards[WQ], square)) piece = 'Q';
                else if (Bitboards.getBit(pieceBitboards[WK], square)) piece = 'K';

                else if (Bitboards.getBit(pieceBitboards[BP], square)) piece = 'p';
                else if (Bitboards.getBit(pieceBitboards[BN], square)) piece = 'n';
                else if (Bitboards.getBit(pieceBitboards[BB], square)) piece = 'b';
                else if (Bitboards.getBit(pieceBitboards[BR], square)) piece = 'r';
                else if (Bitboards.getBit(pieceBitboards[BQ], square)) piece = 'q';
                else if (Bitboards.getBit(pieceBitboards[BK], square)) piece = 'k';

                System.out.print(piece + " ");
            }

            System.out.println();
        }

        System.out.println("Side to move: " + (turn == 0 ? "white" : "black"));
        System.out.println("Castling rights: "
                + ((castlingRights & 1) != 0 ? "K" : "")
                + ((castlingRights & 2) != 0 ? "Q" : "")
                + ((castlingRights & 4) != 0 ? "k" : "")
                + ((castlingRights & 8) != 0 ? "q" : ""));
        System.out.println("En passant square: " + (enPassantSquare == -1 ? "none" : squareToCoordinates(enPassantSquare)));

    }
}
