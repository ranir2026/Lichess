public class MoveGenerator {
    public static final int MAX_MOVES = 256;
    private static final long RANK_7 = 0x00FF000000000000L; 
    private static final long RANK_2 = 0x000000000000FF00L;

    public static int generateLegalMoves(Board board, int[] legalMoves) {
        int[] pseudoMoves = new int[MAX_MOVES];
        int pseudoCount = generatePseudoLegalMoves(board, pseudoMoves);
        int legalCount = 0;

        int side = board.turn;
        int kingType = (side == Board.WHITE) ? Board.WK : Board.BK;
        if (board.pieceBitboards[kingType] == 0L) {
            System.err.println("ERROR: missing king for side " + side + " — dumping board:");
            board.prettyPrintBitboard();
            throw new IllegalStateException("Missing king for side " + side);
        }
        int kingSq = Long.numberOfTrailingZeros(board.pieceBitboards[kingType]);

        long checkers = board.getCheckers();
        long pinned = getPinnedBitboard(board, kingSq, side);

        for (int i=0; i<pseudoCount; i++) {
            int move = pseudoMoves[i];
            int from = Move.getStart(move);

            // if the piece isn't pinned and we're not in check,
            // it's legal. EXCEPTIONS: king moves, en passant
            if (checkers == 0 && (pinned & (1L << from)) == 0 && from != kingSq && Move.getFlags(move) != Move.EN_PASSANT) {
                legalMoves[legalCount++] = move;
                continue;
            }

            // otherwise, run more expensive verification
            if (isMoveLegal(board, move, kingSq, checkers, pinned)) {
                legalMoves[legalCount++] = move;
            }
        }

        return legalCount;
    }

    public static boolean isMoveLegal(Board board, int move, int kingSq, long checkers, long pinned) {
        int from = Move.getStart(move);
        int to = Move.getEnd(move);
        int flags = Move.getFlags(move);
        int side = board.turn;

        // case 1: king moves
        // king must not land on square attacked by enemy
        if (from == kingSq) {
            // simulate occupancy after king move: remove king from 'from', remove any captured piece on 'to', add king on 'to'
            long occAfter = board.allPieces ^ (1L << from);
            if ((board.allPieces & (1L << to)) != 0) occAfter ^= (1L << to); // remove captured piece if any
            occAfter |= (1L << to); // king now on destination
            return !board.isSquareAttacked(to, 1 - side, occAfter);
        }

        // case 2: in double check
        // only king moves are legal (handled above)
        if (Long.bitCount(checkers) > 1) return false;

        // case 3: single check
        // move must block the check or capture the checker
        if (checkers != 0) {
            int checkerSq = Long.numberOfTrailingZeros(checkers);
            long checkRay = getRay(kingSq, checkerSq); // Squares between king and checker
            if (((1L << to) & (checkers | checkRay)) == 0) return false;
        }

        // case 4: pinned piece
        // piece may only move along the pin
        if ((pinned & (1L << from)) != 0) {
            return (getLine(kingSq, from) & (1L << to)) != 0;
        }

        // case 5: en passant
        if (flags == Move.EN_PASSANT) {
            return isEnPassantLegal(board, from, to);
        }

        return true;
    }

    private static long getPinnedBitboard(Board board, int kingSq, int side) {
        long pinned = 0L;
        int opponent = 1 - side;
        long opponentRooks = board.pieceBitboards[(opponent == Board.WHITE) ? Board.WR : Board.BR];
        long opponentBishops = board.pieceBitboards[(opponent == Board.WHITE) ? Board.WB : Board.BB];
        long opponentQueens = board.pieceBitboards[(opponent == Board.WHITE) ? Board.WQ : Board.BQ];

        // Rook-like pinners (rooks and queens on rook lines)
        long rookPinners = AttackTables.getRookAttacks(kingSq, 0) & (opponentRooks | opponentQueens);
        while (rookPinners != 0) {
            int pinnerSq = Long.numberOfTrailingZeros(rookPinners);
            long ray = getRay(kingSq, pinnerSq);
            long blockers = ray & board.allPieces;
            if (Long.bitCount(blockers) == 1) {
                long ourPieces = (side == Board.WHITE) ? board.whitePieces : board.blackPieces;
                pinned |= (blockers & ourPieces);
            }
            rookPinners &= rookPinners - 1;
        }

        // Bishop-like pinners (bishops and queens on bishop diagonals)
        long bishopPinners = AttackTables.getBishopAttacks(kingSq, 0) & (opponentBishops | opponentQueens);
        while (bishopPinners != 0) {
            int pinnerSq = Long.numberOfTrailingZeros(bishopPinners);
            long ray = getRay(kingSq, pinnerSq);
            long blockers = ray & board.allPieces;
            if (Long.bitCount(blockers) == 1) {
                long ourPieces = (side == Board.WHITE) ? board.whitePieces : board.blackPieces;
                pinned |= (blockers & ourPieces);
            }
            bishopPinners &= bishopPinners - 1;
        }
        return pinned;
    }

    private static long getRay(int a, int b) {
        // get ray returns all the bits between a and b, excluding endpoints (on same orthogonal/diagonal)
        long allBetween = getLine(a, b);
        long aBit = 1L << a;
        long bBit = 1L << b;
        
        long mask = ((1L << a) - 1) ^ ((1L << b) - 1);
        return allBetween & mask & ~aBit & ~bBit;
    }

    private static long getLine(int a, int b) {
        // getLine(a, b) returns all the bits on an orthogonal/diagonal, provided that they are orthogonally/diagonally on the same line
        long aBit = 1L << a;
        long bBit = 1L << b;

        if ((AttackTables.getRookAttacks(a, 0) & bBit) != 0) {
            return (AttackTables.getRookAttacks(a, 0) & AttackTables.getRookAttacks(b, 0)) | aBit | bBit;
        }
        if ((AttackTables.getBishopAttacks(a, 0) & bBit) != 0) {
            return (AttackTables.getBishopAttacks(a, 0) & AttackTables.getBishopAttacks(b, 0)) | aBit | bBit;
        }
        return 0L;
    }

    private static boolean isEnPassantLegal(Board board, int from, int to) {
        int side = board.turn;
        int kingType = (side == Board.WHITE) ? Board.WK : Board.BK;
        int kingSq = Long.numberOfTrailingZeros(board.pieceBitboards[kingType]);
        int capturedPawnSq = (side == Board.WHITE) ? to - 8 : to + 8; // square of the pawn being captured en passant
        long occupancyAfterEP = board.allPieces 
                                ^ (1L << from)           // Remove moving pawn
                                ^ (1L << to)             // Add pawn to destination
                                ^ (1L << capturedPawnSq); // Remove captured pawn

        // check if we opened the door to sliding piece attacks
        int opponent = 1 - side;
        long enemySliders = board.pieceBitboards[(opponent == Board.WHITE) ? Board.WR : Board.BR]
                        | board.pieceBitboards[(opponent == Board.WHITE) ? Board.WQ : Board.BQ];
        long rookAttacks = AttackTables.getRookAttacks(kingSq, occupancyAfterEP);
        if ((rookAttacks & enemySliders) != 0) return false;
        long bishopAttacks = AttackTables.getBishopAttacks(kingSq, occupancyAfterEP);
        long enemyBishops = board.pieceBitboards[(opponent == Board.WHITE) ? Board.WB : Board.BB]
                        | board.pieceBitboards[(opponent == Board.WHITE) ? Board.WQ : Board.BQ];
        
        return (bishopAttacks & enemyBishops) == 0;
    }
    
    public static int generatePseudoLegalMoves(Board board, int[] moves) {
        // Pseudo legal moves are all the squares that the piece could move to if there were
        // no other pieces on the board. it will need to be filtered later to get ONLY legals
        int count = 0;
        int side = board.turn;

        long ownPieces = (side == Board.WHITE) ? board.whitePieces : board.blackPieces;
        long opponentPieces = (side == Board.WHITE) ? board.blackPieces : board.whitePieces;
        long allPieces = board.allPieces;

        for (int type=0; type<6; type++) {
            int pieceIdx = (side == Board.WHITE) ? type : type + 6;
            long bitboard = board.pieceBitboards[pieceIdx];

            while (bitboard != 0) {
                int from = Long.numberOfTrailingZeros(bitboard);
                
                switch (type) {
                    case Board.WP:
                        count = generatePawnMoves(board, from, side, moves, count);
                        break;
                    case Board.WN:
                        long knightAttacks = AttackTables.knightAttacks[from] & ~ownPieces;
                        count = addStandardMoves(from, knightAttacks, opponentPieces, moves, count);
                        break;
                    case Board.WB:
                        long bishopAttacks = AttackTables.getBishopAttacks(from, allPieces) & ~ownPieces;
                        count = addStandardMoves(from, bishopAttacks, opponentPieces, moves, count);
                        break;
                    case Board.WR:
                        long rookAttacks = AttackTables.getRookAttacks(from, allPieces) & ~ownPieces;
                        count = addStandardMoves(from, rookAttacks, opponentPieces, moves, count);
                        break;
                    case Board.WQ:
                        long queenAttacks = AttackTables.getQueenAttacks(from, allPieces) & ~ownPieces;
                        count = addStandardMoves(from, queenAttacks, opponentPieces, moves, count);
                        break;
                    case Board.WK:
                        long kingAttacks = AttackTables.kingAttacks[from] & ~ownPieces;
                        count = addStandardMoves(from, kingAttacks, opponentPieces, moves, count);
                        count = generateCastling(board, from, side, moves, count);
                        break;
                }
                
                bitboard &= bitboard - 1; // Clear LSB
            }
        }

        return count;
    }

    private static int addStandardMoves(int from, long attacks, long opponentPieces, int[] moves, int count) {
        // standard moves are captures or quiet moves
        // they both function the same: set the end square bit to the moving piece and the starting square bit to 0
        while (attacks != 0) {
            int to = Long.numberOfTrailingZeros(attacks);
            boolean isCapture = (opponentPieces & (1L << to)) != 0;
            int flags = isCapture ? Move.CAPTURE : Move.QUIET_MOVE;
            moves[count++] = Move.encode(from, to, flags);
            attacks &= attacks - 1;
        }
        return count;
    }

    private static int generatePawnMoves(Board board, int from, int side, int[] moves, int count) {
        long allPieces = board.allPieces;
        long opponentPieces = (side == Board.WHITE) ? board.blackPieces : board.whitePieces;
        long bit = 1L << from;

        if (side == Board.WHITE) {
            // quiet moves (1 or 2 squares forward)
            int target = from + 8;
            if (target < 64 && (allPieces & (1L << target)) == 0) {
                if (target >= 56) count = addPromotions(from, target, false, moves, count);
                else {
                    moves[count++] = Move.encode(from, target, Move.QUIET_MOVE);
                    if ((bit & RANK_2) != 0 && (allPieces & (1L << (from + 16))) == 0) {
                        moves[count++] = Move.encode(from, from + 16, Move.DOUBLE_PAWN);
                    }
                }
            }

            // captures
            long attacks = AttackTables.pawnAttacks[Board.WHITE][from];
            long captures = attacks & opponentPieces;
            while (captures != 0) {
                int to = Long.numberOfTrailingZeros(captures);
                if (to >= 56) count = addPromotions(from, to, true, moves, count);
                else moves[count++] = Move.encode(from, to, Move.CAPTURE);
                captures &= captures - 1;
            }

            // En Passant
            if (board.enPassantSquare != -1 && (attacks & (1L << board.enPassantSquare)) != 0) {
                moves[count++] = Move.encode(from, board.enPassantSquare, Move.EN_PASSANT);
            }
        } else {
            // Black Pawns
            int target = from - 8;
            if (target >= 0 && (allPieces & (1L << target)) == 0) {
                if (target <= 7) count = addPromotions(from, target, false, moves, count);
                else {
                    moves[count++] = Move.encode(from, target, Move.QUIET_MOVE);
                    if ((bit & RANK_7) != 0 && (allPieces & (1L << (from - 16))) == 0) {
                        moves[count++] = Move.encode(from, from - 16, Move.DOUBLE_PAWN);
                    }
                }
            }

            long attacks = AttackTables.pawnAttacks[Board.BLACK][from];
            long captures = attacks & opponentPieces;
            while (captures != 0) {
                int to = Long.numberOfTrailingZeros(captures);
                if (to <= 7) count = addPromotions(from, to, true, moves, count);
                else moves[count++] = Move.encode(from, to, Move.CAPTURE);
                captures &= captures - 1;
            }

            if (board.enPassantSquare != -1 && (attacks & (1L << board.enPassantSquare)) != 0) {
                moves[count++] = Move.encode(from, board.enPassantSquare, Move.EN_PASSANT);
            }
        }

        return count;
    }

    private static int addPromotions(int from, int to, boolean isCapture, int[] moves, int count) {
        // add promotions to the list of pseudo/legal moves
        if (isCapture) {
            moves[count++] = Move.encode(from, to, Move.PROMO_CAP_QUEEN);
            moves[count++] = Move.encode(from, to, Move.PROMO_CAP_ROOK);
            moves[count++] = Move.encode(from, to, Move.PROMO_CAP_BISHOP);
            moves[count++] = Move.encode(from, to, Move.PROMO_CAP_KNIGHT);
        } else {
            moves[count++] = Move.encode(from, to, Move.PROMOTION_QUEEN);
            moves[count++] = Move.encode(from, to, Move.PROMOTION_ROOK);
            moves[count++] = Move.encode(from, to, Move.PROMOTION_BISHOP);
            moves[count++] = Move.encode(from, to, Move.PROMOTION_KNIGHT);
        }
        return count;
    }

    private static int generateCastling(Board board, int from, int side, int[] moves, int count) {
        long all = board.allPieces;
        int opponent = 1 - side;

        // you can't castle if you're in check
        if (board.getCheckers() != 0) return count;

        if (side == Board.WHITE) {
            // kingside castling. conditions: rights exist, path is empty, path not attacked
            if ((board.castlingRights & 1) != 0 && (all & 0x60L) == 0) {
                if (!board.isSquareAttacked(5, opponent, all) && !board.isSquareAttacked(6, opponent, all)) {
                    moves[count++] = Move.encode(4, 6, Move.SHORT_CASTLE);
                }
            }
            
            // queenside castling. conditions: rights exist, path is empty, path not attacked
            if ((board.castlingRights & 2) != 0 && (all & 0xEL) == 0) {
                if (!board.isSquareAttacked(3, opponent, all) && !board.isSquareAttacked(2, opponent, all)) {
                    moves[count++] = Move.encode(4, 2, Move.LONG_CASTLE);
                }
            }
        } else {
            
            // black kingside castling
            if ((board.castlingRights & 4) != 0 && (all & 0x6000000000000000L) == 0) {
                if (!board.isSquareAttacked(61, opponent, all) && !board.isSquareAttacked(62, opponent, all)) {
                    moves[count++] = Move.encode(60, 62, Move.SHORT_CASTLE);
                }
            }

            // black queenside castling
            if ((board.castlingRights & 8) != 0 && (all & 0x0E00000000000000L) == 0) {
                if (!board.isSquareAttacked(59, opponent, all) && !board.isSquareAttacked(58, opponent, all)) {
                    moves[count++] = Move.encode(60, 58, Move.LONG_CASTLE);
                }
            }
        }

        return count;
    }
}
