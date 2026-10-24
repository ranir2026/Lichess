public class KingSafetyBot extends Engine {
    String name;
    double version;
    String author;

    // default piece values
    private static int PAWN   = 93;
    private static int KNIGHT = 338;
    private static int BISHOP = 352;
    private static int ROOK   = 526;
    private static int QUEEN  = 950;
    private static final int MATE_SCORE = 100000;
    private static final int DRAW_SCORE = 0;
    private static final int REPETITION_DRAW_SCORE = -2;

    private static int KNIGHT_MOB_MG = 2;
    private static int KNIGHT_MOB_EG = -2;
    private static int BISHOP_MOB_MG = 6;
    private static int BISHOP_MOB_EG = 0;
    private static int ROOK_MOB_MG   = 4;
    private static int ROOK_MOB_EG   = 5;
    private static int QUEEN_MOB_MG  = 1;
    private static int QUEEN_MOB_EG  = 13;

    // Passed pawn bonus, indexed by how many ranks the pawn has advanced from its own
    // back rank (0 = home rank, 6 = one step from promoting). Index 0 and 7 are unused
    // padding since a pawn is never on its own back rank or the promotion rank.
    private static int[] PASSED_PAWN_MG = {0, 8, 10, 2, 25, 63, 69, 0};
    private static int[] PASSED_PAWN_EG = {0, 18, 24, 57, 91, 150, 184, 0};

    // endgame-only bonus per square of king-distance advantage to the pawn's promotion
    // square (rewards a friendly king escorting the pawn, penalizes a nearby enemy king)
    private static int KING_PROXIMITY_WEIGHT = 27;

    // PASSED_MASK_WHITE[sq] / PASSED_MASK_BLACK[sq]: squares on the same file or adjacent
    // files, ahead of sq from that color's perspective. If any enemy pawn occupies one of
    // these squares, it can block or capture the pawn on its way to promotion, so the pawn
    // on sq is NOT passed.
    private static final long[] PASSED_MASK_WHITE = new long[64];
    private static final long[] PASSED_MASK_BLACK = new long[64];
    static {
        for (int sq = 0; sq < 64; sq++) {
            int rank = sq / 8;
            int file = sq % 8;
            int loFile = Math.max(0, file - 1);
            int hiFile = Math.min(7, file + 1);

            long whiteMask = 0L;
            for (int r = rank + 1; r < 8; r++) {
                for (int f = loFile; f <= hiFile; f++) whiteMask |= (1L << (r * 8 + f));
            }
            PASSED_MASK_WHITE[sq] = whiteMask;

            long blackMask = 0L;
            for (int r = 0; r < rank; r++) {
                for (int f = loFile; f <= hiFile; f++) blackMask |= (1L << (r * 8 + f));
            }
            PASSED_MASK_BLACK[sq] = blackMask;
        }
    }

    private static int ISOLATED_PAWN_PENALTY_MG = 20;
    private static int ISOLATED_PAWN_PENALTY_EG = 8;

    // ADJACENT_FILE_MASKS[f]: every square on the two files next to file f (all ranks).
    // If a pawn has no friendly pawn anywhere in this mask, it's isolated -- no pawn on
    // either neighboring file can ever step up to defend it.
    private static final long[] ADJACENT_FILE_MASKS = new long[8];
    static {
        long[] fileMasks = new long[8];
        for (int f = 0; f < 8; f++) {
            long mask = 0L;
            for (int r = 0; r < 8; r++) mask |= (1L << (r * 8 + f));
            fileMasks[f] = mask;
        }
        for (int f = 0; f < 8; f++) {
            long adjacent = 0L;
            if (f > 0) adjacent |= fileMasks[f - 1];
            if (f < 7) adjacent |= fileMasks[f + 1];
            ADJACENT_FILE_MASKS[f] = adjacent;
        }
    }

    // King safety. Every term is a penalty against the side whose king is exposed, scaled by how
    // much attacking material the OPPONENT has left (4 per queen, 2 per rook, 1 per minor, out of
    // KS_ATTACKER_MATERIAL_FULL), so it fades out as the attacker's pieces come off and is exactly
    // 0 in a pawn endgame (where the king should be active). Scaling by the game phase instead
    // counted the defender's own pieces too, so trading them away "cured" an unsafe king -- a pull
    // toward trading into endgames that showed up as extra endgame losses in testing.
    // All terms look at the same 3 files: the king's file and its neighbours, shifted inward
    // for a king on the a/h file so a corner king isn't "safer" just because it has fewer files.
    private static final int KS_ATTACKER_MATERIAL_FULL = 12; // one side's full set of pieces

    // The values below are Texel-tuned (TexelTuner --ks-only --ks-bounds --bound 491:2:2) with
    // every other constant held at SEEBot's values, first with a phase taper and then re-tuned
    // with the attacker-material taper (only KS_SHIELD_MISSING moved, 15 -> 14). Hand-set
    // starting values are in brackets.

    // pawn shield / storm -- only for a king on a wing (files a-c or f-h). A king still on the
    // d/e files hasn't castled yet, and penalizing its "shield" would punish 1.e4 or 1.d4.
    private static int KS_SHIELD_MISSING  = 14; // [20] no own pawn on the 2nd/3rd rank of a shield file -- a hole
    private static int KS_SHIELD_ADVANCED = 1;  // [8]  shield pawn pushed to the 3rd rank: still covers, but loosened
    // the storm penalties tuned to 0 (unbounded tuning even made them slightly negative), so the
    // data gives no support for a storm term on top of the pawn PSTs. Kept for future tuning.
    private static int KS_STORM_RANK3 = 0;      // [25] enemy pawn on our 3rd rank (or closer) on a shield file
    private static int KS_STORM_RANK4 = 0;      // [15] ...on our 4th rank: one push from contact
    private static int KS_STORM_RANK5 = 0;      // [5]  ...on our 5th rank: early storm

    // open files next to the king (any king position). A fully open file costs both.
    private static int KS_FILE_NO_OWN_PAWN   = 22; // [15] no friendly pawn left to block enemy rooks/queen
    private static int KS_FILE_NO_ENEMY_PAWN = 10; // [8]  enemy heavy pieces can use the file freely

    // attacker counting: every enemy knight/bishop/rook/queen that attacks at least one square
    // of the king zone adds its weight to the attack units ONCE (not per attacked square --
    // that would just be the mobility term again). The units then go through a nonlinear
    // table, because one attacker is harmless but three together usually aren't. A bishop with
    // weight 0 still counts toward KS_MIN_ATTACKERS, it just adds no units.
    private static int KS_ATTACK_WEIGHT_N = 1;   // [2]
    private static int KS_ATTACK_WEIGHT_B = 0;   // [2]
    private static int KS_ATTACK_WEIGHT_R = 3;   // [3]
    private static int KS_ATTACK_WEIGHT_Q = 3;   // [5]
    private static int KS_MIN_ATTACKERS   = 2;   // [2]   fewer attackers than this -> no danger at all
    private static int KS_DANGER_QUAD     = 19;  // [18]  KING_DANGER[u] = min(KS_DANGER_MAX, KS_DANGER_QUAD * u * u / 16)
    private static int KS_DANGER_MAX      = 500; // [500]
    private static int KS_NO_QUEEN_SCALE  = 6;   // [4]   out of 16: how much an attack counts without a queen on the board

    // attack units -> centipawns, and storm penalty by the storming pawn's relative rank. Both
    // are rebuilt from the constants whenever the params change, so evaluate() only does lookups.
    private static final int[] KING_DANGER = new int[64];
    private static final int[] STORM_BY_RANK = new int[8];

    private static void buildKingSafetyTables() {
        for (int u = 0; u < KING_DANGER.length; u++) {
            KING_DANGER[u] = Math.min(KS_DANGER_MAX, KS_DANGER_QUAD * u * u / 16);
        }
        for (int r = 0; r < 8; r++) {
            STORM_BY_RANK[r] = (r <= 2) ? KS_STORM_RANK3 : (r == 3) ? KS_STORM_RANK4 : (r == 4) ? KS_STORM_RANK5 : 0;
        }
    }

    // KING_ZONE[color][sq]: the squares around a king on sq that enemy attackers are counted
    // against -- 3 files by 4 ranks, from one rank behind the king to two ranks in front of it.
    // SHIELD_HOME / SHIELD_ADVANCED[color][sq]: on the king's 3 files, the squares one and two
    // ranks in front of it. FORWARD_RANKS[color][r]: every square on a relative rank above r.
    private static final long[] FILE_MASKS = new long[8];
    private static final long[][] KING_ZONE = new long[2][64];
    private static final long[][] SHIELD_HOME = new long[2][64];
    private static final long[][] SHIELD_ADVANCED = new long[2][64];
    private static final long[][] FORWARD_RANKS = new long[2][8];
    static {
        for (int f = 0; f < 8; f++) {
            long mask = 0L;
            for (int r = 0; r < 8; r++) mask |= (1L << (r * 8 + f));
            FILE_MASKS[f] = mask;
        }
        for (int color = 0; color < 2; color++) {
            for (int r = 0; r < 8; r++) {
                long mask = 0L;
                for (int ahead = r + 1; ahead < 8; ahead++) mask |= 0xFFL << (absoluteRank(color, ahead) * 8);
                FORWARD_RANKS[color][r] = mask;
            }
            for (int sq = 0; sq < 64; sq++) {
                int centerFile = Math.max(1, Math.min(6, sq % 8));
                int kingRank = relativeRank(color, sq);
                long zone = 0L, home = 0L, advanced = 0L;
                for (int f = centerFile - 1; f <= centerFile + 1; f++) {
                    for (int r = Math.max(0, kingRank - 1); r <= Math.min(7, kingRank + 2); r++) {
                        zone |= squareBit(color, f, r);
                    }
                    if (kingRank + 1 <= 7) home |= squareBit(color, f, kingRank + 1);
                    if (kingRank + 2 <= 7) advanced |= squareBit(color, f, kingRank + 2);
                }
                KING_ZONE[color][sq] = zone;
                SHIELD_HOME[color][sq] = home;
                SHIELD_ADVANCED[color][sq] = advanced;
            }
        }
        buildKingSafetyTables();
    }

    // rank counted from the given color's own back rank (0 = 1st rank for white, 8th for black)
    private static int relativeRank(int color, int sq) {
        return (color == Board.WHITE) ? sq / 8 : 7 - sq / 8;
    }

    private static int absoluteRank(int color, int relRank) {
        return (color == Board.WHITE) ? relRank : 7 - relRank;
    }

    private static long squareBit(int color, int file, int relRank) {
        return 1L << (absoluteRank(color, relRank) * 8 + file);
    }

    // the last king-safety penalties evaluate() applied (already phase-scaled), exposed so the
    // unit tests can check the king-safety term on its own through the real evaluation path
    int lastKingSafetyWhite = 0;
    int lastKingSafetyBlack = 0;

    // interpolation constant for keeping track
    // of how far the game has progressed (endgame vs middlegame)
    private static final int TOTAL_PHASE = 24;

    private Board board;

    public String getName() { return name; }
    public double getVersion() {return version; }


    // transposition table
    public static final int TABLE_SIZE = 1048576; // 2^20 entries
    private TranspositionTableEntry[] transpositionTable = new TranspositionTableEntry[TABLE_SIZE];

    // tables to handle late-move reductions, killer moves, and history/countermove heuristics
    private static final int[][] LMR_TABLE = new int[64][64];
    private static final int MAX_HISTORY = 16384;
    private int[][] killerMoves = new int[64][2];
    private int[][] counterMoves = new int[64][64];
    private int[][][] historyTable = new int[2][64][64];
    private int[] moveHistory = new int[128];

    private int[][] moveStack; // per-ply preallocated move buffers to reduce allocations during search
    private int[][] quietMoveStack;

    // search control
    private volatile boolean stopSearch = false;
    private long searchStopTime = 0L;

    private boolean isTimeUp() {
        if (stopSearch) return true;
        if (searchStopTime > 0 && System.currentTimeMillis() >= searchStopTime) {
            stopSearch = true;
            return true;
        }
        return false;
    }

    // PSTs
    private static int[] PAWN_PST = {
        0,   0,   0,   0,   0,   0,   0,   0,
        -6,  -4, -11, -12,  -3,  16,  13,  -6,
        -5,  -5,   0,   0,   8,   5,   7,  -2,
        -4,  -2,   2,  11,  11,   2,   3,  -5,
        9,   9,   4,  10,  11,   8,  13,   7,
        29,  30,  23,   9,  13,  24,  31,  25,
        75,  83,  62,  60,  68,  54,  60,  74,
        0,   0,   0,   0,   0,   0,   0,   0
    };

    private static int[] KNIGHT_PST = {
        -58, -20, -30, -17, -11, -17, -19, -54,
        -30, -28,  -7,   7,   9,   5, -18, -22,
        -25,  -4,  12,  23,  26,  21,  11, -20,
        -15,   5,  26,  25,  31,  25,  18, -10,
        -11,  15,  30,  47,  35,  44,  19,   2,
        -32,  13,  28,  38,  36,  39,  22, -16,
        -45, -17,  14,  14,   6,   7, -15, -44,
        -100, -53, -14, -33, -13, -53, -59,-100
    };

    private static int[] BISHOP_PST = {
        -24,  -1,  -4,  -4,  -5,  -5, -12, -17,
        -3,  10,   6,   1,   9,   5,  23,  -9,
        -5,   9,  12,   8,  11,  14,   7,  -2,
        -4,   5,   8,  20,  16,  -1,   0,   0,
        -3,   0,  12,  22,  19,  15,   1,   1,
        -10,   4,   8,   9,   8,  14,  13,  -1,
        -17,  -2,  -5, -14,   5,   9,   1, -35,
        -26, -17, -26, -18, -12, -16, -11, -19
    };

    private static int[] ROOK_PST = {
        -4,   4,  11,  13,  13,  10, -13, -13,
        -14,   0,   1,   7,   4,   5,  -2, -30,
        -10,   0,   0,   2,   2,   0,  -1, -16,
        -2,   5,   9,   6,   5,   0,   3,  -7,
        7,   4,  14,   9,   9,  15,   2,   7,
        12,  16,  13,  16,   7,  11,  14,   6,
        21,  22,  26,  25,  18,  22,  17,  18,
        20,  17,  19,  21,  22,  17,  15,  16
    };

    private static int[] QUEEN_PST = {
        9,  -1,   7,  24,   9,  -1,  -3, -24,
        -8,   0,  18,  14,  22,  17,  -4,  13,
        0,   7,   4,   7,   7,  13,  22,  21,
        -3,  -8,  -1,  -3,   3,  10,  20,  20,
        -7, -15, -11,  -8,  11,  19,  29,  19,
        -11, -11,  -8,  11,  30,  46,  48,  40,
        -15, -28,   5,  18,  17,  39,  37,  40,
        -10,  16,  19,  19,  43,  40,  35,  30
    };

    private static int[] KING_PST = {
        3,  38,  25, -50,   3, -34,  36,  20,
        13,  24,   3, -50, -48, -23,  18,  23,
        2, -17, -32, -42, -40, -36, -12,  -1,
        -22, -23, -43, -68, -63, -48, -23, -22,
        -29, -21, -31, -68, -72, -52, -18, -29,
        -5, -22, -38, -63, -61, -19,  -1, -14,
        -29, -11, -33, -40, -40, -22, -16, -15,
        -66, -37, -29, -44, -54, -20, -24,  -9
    };

    private static int[] KING_ENDGAME_PST = {
        -65, -34, -23, -17, -21, -14, -36, -52,
        -29, -16,  -6,   5,   7,  -2, -13, -28,
        -18,  -4,   7,   9,  11,   7,   2, -20,
        -29,  -8,  13,  15,  16,  12,   1, -22,
        -30,  11,  13,  18,  18,  20,  12, -30,
        -5,   7,  11,  11,  13,  31,  25, -15,
        -30,  -3,   7,  10,  10,  19,  -7, -11,
        -86, -32, -22, -27, -30, -11, -16, -34
    };

    static {
        for (int depth = 1; depth < 64; depth++) {
            for (int moves = 1; moves < 64; moves++) {
                LMR_TABLE[depth][moves] = (int) (0.75 + Math.log(depth) * Math.log(moves) / 2.25);
            }
        }
    }

    // king value used only for SEE's internal exchange simulation -- large enough that
    // SEE never treats losing the king as an acceptable trade. Safe to include in
    // evaluate()'s material sum too since both sides always have exactly one king, so
    // it cancels out in the final (whiteScore - blackScore) difference.
    private static final int KING_SEE_VALUE = 20000;

    private int getPieceValue(int type) {
        switch(type % 6) {
            case 0: return PAWN;
            case 1: return KNIGHT;
            case 2: return BISHOP;
            case 3: return ROOK;
            case 4: return QUEEN;
            case 5: return KING_SEE_VALUE;
            default: return 0;
        }
    }

    private int getStandardPST(int type, int sq) {
        switch(type % 6) {
            case 0: return PAWN_PST[sq];
            case 1: return KNIGHT_PST[sq];
            case 2: return BISHOP_PST[sq];
            case 3: return ROOK_PST[sq];
            case 4: return QUEEN_PST[sq];
            case 5: return KING_PST[sq];
            
            default: return 0;
        }
    }

    // SEE (Static Exchange Evaluation): simulates the full capture sequence on a
    // square -- both sides recapturing with their least valuable attacker each
    // time -- and returns the net material result from the mover's perspective.
    // Used to order/prune captures that lose material even after all recaptures,
    // which plain MVV-LVA (based only on the first capture) can't detect.
    private long attackersTo(int square, long occupancy) {
        long attackers = 0L;
        attackers |= AttackTables.pawnAttacks[Board.BLACK][square] & board.pieceBitboards[Board.WP];
        attackers |= AttackTables.pawnAttacks[Board.WHITE][square] & board.pieceBitboards[Board.BP];
        attackers |= AttackTables.knightAttacks[square] & (board.pieceBitboards[Board.WN] | board.pieceBitboards[Board.BN]);
        attackers |= AttackTables.kingAttacks[square] & (board.pieceBitboards[Board.WK] | board.pieceBitboards[Board.BK]);

        long bishopsQueens = board.pieceBitboards[Board.WB] | board.pieceBitboards[Board.BB]
                           | board.pieceBitboards[Board.WQ] | board.pieceBitboards[Board.BQ];
        attackers |= AttackTables.getBishopAttacks(square, occupancy) & bishopsQueens;

        long rooksQueens = board.pieceBitboards[Board.WR] | board.pieceBitboards[Board.BR]
                          | board.pieceBitboards[Board.WQ] | board.pieceBitboards[Board.BQ];
        attackers |= AttackTables.getRookAttacks(square, occupancy) & rooksQueens;

        return attackers & occupancy;
    }

    private int leastValuableAttackerSquare(long attackers, int side) {
        int base = (side == Board.WHITE) ? 0 : 6;
        for (int type = 0; type < 6; type++) {
            long bb = board.pieceBitboards[base + type] & attackers;
            if (bb != 0) return Long.numberOfTrailingZeros(bb);
        }
        return -1;
    }

    public int see(int move) {
        int from = Move.getStart(move);
        int to = Move.getEnd(move);
        int side = board.turn;
        boolean isEnPassant = Move.getFlags(move) == Move.EN_PASSANT;

        long occupancy = board.allPieces;

        int capturedType = isEnPassant ? ((side == Board.WHITE) ? Board.BP : Board.WP) : board.getPieceAt(to);
        int[] gain = new int[32];
        int d = 0;
        gain[0] = (capturedType == -1) ? 0 : getPieceValue(capturedType);

        int lastAttackerValue = getPieceValue(board.getPieceAt(from));

        occupancy ^= (1L << from);
        if (isEnPassant) {
            int capSq = (side == Board.WHITE) ? to - 8 : to + 8;
            occupancy ^= (1L << capSq);
        }

        long attackers = attackersTo(to, occupancy);
        int sideToMove = 1 - side;

        while (true) {
            int sq = leastValuableAttackerSquare(attackers, sideToMove);
            if (sq == -1) break;

            d++;
            gain[d] = lastAttackerValue - gain[d - 1];
            // if even in the best case this recapture doesn't improve on what's already
            // been established, the exchange is decided -- stop simulating further
            if (Math.max(-gain[d - 1], gain[d]) < 0) break;

            lastAttackerValue = getPieceValue(board.getPieceAt(sq));
            occupancy ^= (1L << sq);
            attackers = attackersTo(to, occupancy);
            sideToMove = 1 - sideToMove;
        }

        // fold from the deepest simulated ply back up to gain[0] -- each side, in turn,
        // picks whichever is better: stopping here (gain[i]) or continuing the
        // exchange (-gain[i+1], the result one ply deeper from the opponent's view)
        for (int i = d - 1; i >= 0; i--) {
            gain[i] = -Math.max(-gain[i], gain[i + 1]);
        }
        return gain[0];
    }

    private int getIndex(long hash) {
        return (int) (Math.abs(hash) % TABLE_SIZE);
    }

    private void updateHistory(int color, int from, int to, int bonus) {
        int current = historyTable[color][from][to];
        historyTable[color][from][to] = current + bonus - (current * Math.abs(bonus) / MAX_HISTORY);
    }

    private int chebyshevDistance(int sq1, int sq2) {
        int fileDiff = Math.abs((sq1 % 8) - (sq2 % 8));
        int rankDiff = Math.abs((sq1 / 8) - (sq2 / 8));
        return Math.max(fileDiff, rankDiff);
    }

    private int computePassedPawnBonus(long ownPawns, long enemyPawns, boolean isWhite,
                                        int ownKingSq, int enemyKingSq, int currentPhase) {
        int total = 0;
        long pawns = ownPawns;

        while (pawns != 0) {
            int sq = Long.numberOfTrailingZeros(pawns);
            pawns &= pawns - 1;

            long mask = isWhite ? PASSED_MASK_WHITE[sq] : PASSED_MASK_BLACK[sq];
            if ((enemyPawns & mask) != 0) continue; // an enemy pawn can block/capture it -- not passed

            int rank = sq / 8;
            int advancement = isWhite ? rank : (7 - rank);

            // king tropism: friendly king escorting the pawn helps, enemy king nearby hurts.
            // only meaningful in the endgame, so it lives entirely in the EG term and gets
            // phased out automatically as currentPhase rises toward the middlegame. Scaled by
            // advancement too -- a pawn on its 2nd rank isn't in a promotion race yet, so king
            // distance shouldn't matter much until the pawn is actually close to queening.
            int promoSq = isWhite ? (56 + (sq % 8)) : (sq % 8);
            int kingDistDiff = chebyshevDistance(enemyKingSq, promoSq) - chebyshevDistance(ownKingSq, promoSq);
            int kingBonus = (kingDistDiff * KING_PROXIMITY_WEIGHT * advancement) / 6;

            int mgBonus = PASSED_PAWN_MG[advancement];
            int egBonus = PASSED_PAWN_EG[advancement] + kingBonus;

            total += ((mgBonus * currentPhase) + (egBonus * (TOTAL_PHASE - currentPhase))) / TOTAL_PHASE;
        }

        return total;
    }

    private int computeIsolatedPawnPenalty(long ownPawns, int currentPhase) {
        int total = 0;
        long pawns = ownPawns;

        while (pawns != 0) {
            int sq = Long.numberOfTrailingZeros(pawns);
            pawns &= pawns - 1;

            int file = sq % 8;
            if ((ownPawns & ADJACENT_FILE_MASKS[file]) != 0) continue; // supported by a pawn on a neighboring file

            total += ((ISOLATED_PAWN_PENALTY_MG * currentPhase) + (ISOLATED_PAWN_PENALTY_EG * (TOTAL_PHASE - currentPhase))) / TOTAL_PHASE;
        }

        return total;
    }

    // 8-bit set of the files that hold at least one of these pawns (all ranks folded onto one)
    private static int pawnFiles(long pawns) {
        pawns |= pawns >>> 32;
        pawns |= pawns >>> 16;
        pawns |= pawns >>> 8;
        return (int) (pawns & 0xFF);
    }

    // pawn-structure part of king safety for one king: open files, shield, storm (untapered
    // middlegame penalty). Everything is in relative ranks so both colors run identical logic.
    private int kingPawnPenalty(int color, int kingSq, long ownPawns, long enemyPawns, int ownFiles, int enemyFiles) {
        int kingFile = kingSq % 8;
        int centerFile = Math.max(1, Math.min(6, kingFile));
        int window = 7 << (centerFile - 1); // the king's 3 files as file bits
        int penalty = Integer.bitCount(window & ~ownFiles) * KS_FILE_NO_OWN_PAWN
                    + Integer.bitCount(window & ~enemyFiles) * KS_FILE_NO_ENEMY_PAWN;
        if (kingFile == 3 || kingFile == 4) return penalty; // d/e-file king: no shield or storm

        // shield: a pawn right in front of the king's rank is ideal, one square further is
        // "advanced", and anything else (pushed further or gone) leaves a hole. Advanced pawns
        // are shifted back onto the home row so a file holding both only counts once, as home.
        long home = ownPawns & SHIELD_HOME[color][kingSq];
        long advanced = ownPawns & SHIELD_ADVANCED[color][kingSq];
        long advancedOnHomeRow = (color == Board.WHITE) ? advanced >>> 8 : advanced << 8;
        int homeCount = Long.bitCount(home);
        int advancedCount = Long.bitCount(advancedOnHomeRow & ~home);
        penalty += advancedCount * KS_SHIELD_ADVANCED + (3 - homeCount - advancedCount) * KS_SHIELD_MISSING;

        // storm: on each shield file, the enemy pawn that has come closest to our king
        long stormers = enemyPawns & FORWARD_RANKS[color][relativeRank(color, kingSq)];
        for (int f = centerFile - 1; f <= centerFile + 1; f++) {
            long onFile = stormers & FILE_MASKS[f];
            if (onFile == 0) continue;
            int sq = (color == Board.WHITE) ? Long.numberOfTrailingZeros(onFile) : 63 - Long.numberOfLeadingZeros(onFile);
            penalty += STORM_BY_RANK[relativeRank(color, sq)];
        }
        return penalty;
    }

    // one side's attacking material on the phase scale (Q=4, R=2, B=N=1), capped at a full set
    private int attackingMaterial(int queen, int rook, int bishop, int knight) {
        int material = 4 * Long.bitCount(board.pieceBitboards[queen]) + 2 * Long.bitCount(board.pieceBitboards[rook])
                     + Long.bitCount(board.pieceBitboards[bishop]) + Long.bitCount(board.pieceBitboards[knight]);
        return Math.min(material, KS_ATTACKER_MATERIAL_FULL);
    }

    // attacker-counting part of king safety (untapered middlegame penalty for the defender)
    private int kingDanger(int attackers, int attackUnits, boolean attackerHasQueen) {
        if (attackers < KS_MIN_ATTACKERS) return 0;
        int danger = KING_DANGER[Math.max(0, Math.min(attackUnits, KING_DANGER.length - 1))];
        if (!attackerHasQueen) danger = danger * KS_NO_QUEEN_SCALE / 16;
        return danger;
    }

    public KingSafetyBot(Board board) {
        this.board = board;
        this.moveStack = new int[128][MoveGenerator.MAX_MOVES]; // allocate per-ply move buffers (depth headroom)
        this.quietMoveStack = new int[128][MoveGenerator.MAX_MOVES];

        this.name = "KingSafetyBot";
        this.author = "RR";
        this.version = 2.8;
    }


    public int evaluate() {
        int whiteScore = 0;
        int blackScore = 0;
        int phase = 0;

        // we need phase before the main loop so king PST interpolation is correct,
        // and also so we can interpolate mobility weights between mg and eg.
        for (int i = 0; i < 12; i++) {
            int count = Long.bitCount(board.pieceBitboards[i]);
            if (i == Board.WQ || i == Board.BQ) phase += count * 4;
            else if (i == Board.WR || i == Board.BR) phase += count * 2;
            else if (i == Board.WN || i == Board.BN || i == Board.WB || i == Board.BB) phase += count * 1;
        }
        int currentPhase = Math.min(phase, TOTAL_PHASE);

        // precompute combined occupancy once -- used for all sliding-piece mobility lookups
        long allPieces = board.allPieces;

        // king zones for attacker counting. A missing king (only possible in a hand-made
        // position) gets an empty zone, and its king-safety terms are skipped further down.
        int whiteKingSq = board.getKingSquare(Board.WHITE);
        int blackKingSq = board.getKingSquare(Board.BLACK);
        long whiteKingZone = (whiteKingSq >= 0) ? KING_ZONE[Board.WHITE][whiteKingSq] : 0L;
        long blackKingZone = (blackKingSq >= 0) ? KING_ZONE[Board.BLACK][blackKingSq] : 0L;
        int whiteAttackers = 0, whiteAttackUnits = 0; // white pieces bearing on the black king zone
        int blackAttackers = 0, blackAttackUnits = 0; // black pieces bearing on the white king zone

        // material + PST + mobility (+ king-zone attacks, read off the same attack bitboards)
        for (int i = 0; i < 12; i++) {
            long pieces = board.pieceBitboards[i];
            boolean isWhite = (i < 6);

            // friendly pieces -- mobility squares occupied by own pieces don't count
            long ownPieces = isWhite ? board.whitePieces : board.blackPieces;
            long enemyKingZone = isWhite ? blackKingZone : whiteKingZone;

            while (pieces != 0) {
                int sq = Long.numberOfTrailingZeros(pieces);
                pieces &= pieces - 1;

                int score = getPieceValue(i);
                int pstScore = 0;
                int flipSq = isWhite ? sq : sq ^ 56; // flip orientation for black

                if (i == Board.WK || i == Board.BK) {
                    // interpolate king PST between middlegame and endgame
                    int mg = KING_PST[flipSq];
                    int eg = KING_ENDGAME_PST[flipSq];
                    pstScore = ((mg * currentPhase) + (eg * (TOTAL_PHASE - currentPhase))) / TOTAL_PHASE;
                } else {
                    pstScore = getStandardPST(i, flipSq);
                }

                // count the number of squares this piece can move to (excluding own pieces).
                // we use the raw attack tables rather than legal-move generation to keep
                // evaluation fast -- this is the standard approach and still a strong signal.
                int mobilityBonus = 0;
                int type = i % 6;
                long attacks = 0L;
                int attackWeight = 0;

                if (type == Board.WN) { // knight
                    attacks = AttackTables.knightAttacks[sq];
                    attackWeight = KS_ATTACK_WEIGHT_N;
                    int mobCount = Long.bitCount(attacks & ~ownPieces);
                    int mgBonus = mobCount * KNIGHT_MOB_MG;
                    int egBonus = mobCount * KNIGHT_MOB_EG;
                    mobilityBonus = ((mgBonus * currentPhase) + (egBonus * (TOTAL_PHASE - currentPhase))) / TOTAL_PHASE;

                } else if (type == Board.WB) { // bishop
                    attacks = AttackTables.getBishopAttacks(sq, allPieces);
                    attackWeight = KS_ATTACK_WEIGHT_B;
                    int mobCount = Long.bitCount(attacks & ~ownPieces);
                    int mgBonus = mobCount * BISHOP_MOB_MG;
                    int egBonus = mobCount * BISHOP_MOB_EG;
                    mobilityBonus = ((mgBonus * currentPhase) + (egBonus * (TOTAL_PHASE - currentPhase))) / TOTAL_PHASE;

                } else if (type == Board.WR) { // rook
                    attacks = AttackTables.getRookAttacks(sq, allPieces);
                    attackWeight = KS_ATTACK_WEIGHT_R;
                    int mobCount = Long.bitCount(attacks & ~ownPieces);
                    int mgBonus = mobCount * ROOK_MOB_MG;
                    int egBonus = mobCount * ROOK_MOB_EG;
                    mobilityBonus = ((mgBonus * currentPhase) + (egBonus * (TOTAL_PHASE - currentPhase))) / TOTAL_PHASE;

                } else if (type == Board.WQ) { // queen
                    attacks = AttackTables.getQueenAttacks(sq, allPieces);
                    attackWeight = KS_ATTACK_WEIGHT_Q;
                    int mobCount = Long.bitCount(attacks & ~ownPieces);
                    int mgBonus = mobCount * QUEEN_MOB_MG;
                    int egBonus = mobCount * QUEEN_MOB_EG;
                    mobilityBonus = ((mgBonus * currentPhase) + (egBonus * (TOTAL_PHASE - currentPhase))) / TOTAL_PHASE;
                }

                // king-zone attack: counted once per piece, however many zone squares it hits
                if ((attacks & enemyKingZone) != 0) {
                    if (isWhite) { whiteAttackers++; whiteAttackUnits += attackWeight; }
                    else { blackAttackers++; blackAttackUnits += attackWeight; }
                }

                if (isWhite) whiteScore += (score + pstScore + mobilityBonus);
                else blackScore += (score + pstScore + mobilityBonus);
            }
        }

        int whitePassedBonus = computePassedPawnBonus(board.pieceBitboards[Board.WP], board.pieceBitboards[Board.BP], true, whiteKingSq, blackKingSq, currentPhase);
        int blackPassedBonus = computePassedPawnBonus(board.pieceBitboards[Board.BP], board.pieceBitboards[Board.WP], false, blackKingSq, whiteKingSq, currentPhase);
        whiteScore += whitePassedBonus;
        blackScore += blackPassedBonus;

        int whiteIsolatedPenalty = computeIsolatedPawnPenalty(board.pieceBitboards[Board.WP], currentPhase);
        int blackIsolatedPenalty = computeIsolatedPawnPenalty(board.pieceBitboards[Board.BP], currentPhase);
        whiteScore -= whiteIsolatedPenalty;
        blackScore -= blackIsolatedPenalty;

        // king safety, scaled by the attacker's material. Skipped when no pieces are left at all,
        // so pawn endgames pay nothing for it.
        lastKingSafetyWhite = 0;
        lastKingSafetyBlack = 0;
        if (currentPhase > 0 && whiteKingSq >= 0 && blackKingSq >= 0) {
            int whiteMaterial = attackingMaterial(Board.WQ, Board.WR, Board.WB, Board.WN);
            int blackMaterial = attackingMaterial(Board.BQ, Board.BR, Board.BB, Board.BN);
            long whitePawns = board.pieceBitboards[Board.WP];
            long blackPawns = board.pieceBitboards[Board.BP];
            int whiteFiles = pawnFiles(whitePawns);
            int blackFiles = pawnFiles(blackPawns);
            int whiteKingPenalty = kingPawnPenalty(Board.WHITE, whiteKingSq, whitePawns, blackPawns, whiteFiles, blackFiles)
                                 + kingDanger(blackAttackers, blackAttackUnits, board.pieceBitboards[Board.BQ] != 0);
            int blackKingPenalty = kingPawnPenalty(Board.BLACK, blackKingSq, blackPawns, whitePawns, blackFiles, whiteFiles)
                                 + kingDanger(whiteAttackers, whiteAttackUnits, board.pieceBitboards[Board.WQ] != 0);
            lastKingSafetyWhite = (whiteKingPenalty * blackMaterial) / KS_ATTACKER_MATERIAL_FULL;
            lastKingSafetyBlack = (blackKingPenalty * whiteMaterial) / KS_ATTACKER_MATERIAL_FULL;
            whiteScore -= lastKingSafetyWhite;
            blackScore -= lastKingSafetyBlack;
        }

        int perspective = (board.turn == Board.WHITE) ? 1 : -1;
        return (whiteScore - blackScore) * perspective;
    }

    public int search(int depth, int alpha, int beta) {
        return search(depth, alpha, beta, 0, true);
    }

    public int search(int depth, int alpha, int beta, int ply) {
        return search(depth, alpha, beta, ply, true);
    }

    public int search(int depth, int alpha, int beta, int ply, boolean allowNMP) {
        // alpha = lower bound
        // beta = higher bound
        long hash = board.getCurrentHash();
        int ttIndex = getIndex(hash);
        TranspositionTableEntry entry = transpositionTable[ttIndex];

        // look moves up in the transposition table to see if this position has
        // already triggered a beta cutoff. if it has, then check to make sure
        // that the beta cutoff occurred at the same or higher depth. if both
        // of these conditions are met, we should feel comfortable using the
        // transpotion table's evaluation instead of researching and wasting time
        if (entry != null && entry.key == hash && entry.depth >= depth) {
            if (entry.type == TranspositionTableEntry.EXACT) return (int)entry.score;
            if (entry.type == TranspositionTableEntry.LOWER_BOUND) alpha = Math.max(alpha, (int)entry.score);
            if (entry.type == TranspositionTableEntry.UPPER_BOUND) beta = Math.min(beta, (int)entry.score);
            if (alpha >= beta) return (int) entry.score;
        }
        
        // stop searching when time is up, but keep the current alpha bound.
        if (isTimeUp()) { stopSearch = true; return alpha; }
        nodesSearched++;

        // to prevent the horizon effect (searching deep, and then overlooking a 
        // capture on the next move), do a deeper search for only captures once 
        // the normal search is done 
        if (depth == 0) return quiescenceSearch(alpha, beta, ply);

        // get the moves to search through, but before we do anything check if the 
        // game has ended on this branch. if the game ends by mate, factor in the 
        // depth of the search so that the engine will find quicker mates
        int[] moves;
        if (ply >= 0 && ply < moveStack.length) moves = moveStack[ply];
        else moves = new int[MoveGenerator.MAX_MOVES];
        int count = MoveGenerator.generateLegalMoves(board, moves);
        if (board.isFiftyMoveRule()) return DRAW_SCORE;
        if (board.isThreefoldRepetition()) return REPETITION_DRAW_SCORE;

        if (count == 0) {
            if (board.getCheckers() != 0) return -MATE_SCORE - depth;
            return DRAW_SCORE;
        }

        int side = board.turn;
        long nonPawnPieces = (side == Board.WHITE)
            ? (board.pieceBitboards[Board.WN] | board.pieceBitboards[Board.WB] | board.pieceBitboards[Board.WR] | board.pieceBitboards[Board.WQ])
            : (board.pieceBitboards[Board.BN] | board.pieceBitboards[Board.BB] | board.pieceBitboards[Board.BR] | board.pieceBitboards[Board.BQ]);
        if (allowNMP && depth >= 3 && board.getCheckers() == 0 && Long.bitCount(nonPawnPieces) > 2) {
            int staticEval = evaluate();
            if (staticEval >= beta) {
                int R = (depth >= 8) ? depth/6 + 1: depth/4 + 2;
                
                board.makeNullMove();
                int nullScore = -search(depth - 1 - R, -beta, -beta + 1, ply + 1, false);
                board.unmakeNullMove();

                if (stopSearch) return alpha;

                if (depth >= 6 && nullScore >= beta) {
                    int verifyScore = search(depth - 1, alpha, beta, ply, false);
                    if (stopSearch) return alpha;
                    if (verifyScore >= beta) return beta;
                }
            }
        }

        int ttMove = (entry != null && entry.key == hash) ? entry.bestMove : -1;
        int prevMove = (ply > 0) ? moveHistory[ply - 1] : -1;
        orderMoves(moves, count, ttMove, ply, prevMove);
        int originalAlpha = alpha;
        int bestMoveFound = -1;
        int movesSearched = 0;

        int[] quietMoves;
        if (ply >= 0 && ply < quietMoveStack.length) quietMoves = quietMoveStack[ply];
        else quietMoves = new int[MoveGenerator.MAX_MOVES];
        int quietCount = 0;

        for (int i = 0; i < count; i++) {
            movesSearched++;
            int move = moves[i];

            // play each move out on the board and continuously search and evaluate the 
            // possible next moves before unmaking the move and evaluating the best ones
            moveHistory[ply] = move;
            board.makeMove(move);

            if (isTimeUp()) { board.unmakeMove(move); stopSearch = true; return alpha; }

            int score;

            boolean isCapture = Move.isCapture(move);
            boolean isPromotion = Move.isPromotion(move);
            boolean inCheck = board.getCheckers() > 0;

            if (movesSearched == 1) {
                // Principal Variation Search: the first move (best-ordered, likely the PV
                // move) is searched with the full alpha-beta window. Every subsequent move
                // is assumed to be worse, so we prove that cheaply with a null (zero-width)
                // window before paying for a full-window re-search.
                score = -search(depth - 1, -beta, -alpha, ply + 1);
                if (stopSearch) { board.unmakeMove(moves[i]); return alpha; }
            } else {
                // Late Move Reductions. These work by assuming that the move list is ordered
                // well enough for the bad moves to generally be at the end of the list. because
                // this is true, we can cheat a little and search moves at the end on a lower
                // depth to be more efficient -- BUT, if we find that a move at the end is actually
                // better than α (from a lower depth search), search that move AGAIN at a
                // higher depth to be safe (yes inefficient but rare enough that it's fine)
                int newDepth = depth - 1;
                if (depth >= 3 && movesSearched > 4 && !isCapture && !isPromotion && !inCheck) {
                    // don't want to reduce important moves such as captures, promotions, moves in check
                    int reduction = LMR_TABLE[depth][Math.min(movesSearched, 63)];
                    newDepth = Math.max(1, depth - 1 - reduction);
                }

                // null-window search to cheaply verify this move doesn't beat the PV move
                score = -search(newDepth, -(alpha + 1), -alpha, ply + 1);
                if (stopSearch) { board.unmakeMove(move); return alpha; }

                // it beat alpha -- re-search at full depth (if reduced) and/or full window
                // (if it could still land inside (alpha, beta)) to get an accurate score
                if (score > alpha && (newDepth < depth - 1 || score < beta)) {
                    score = -search(depth - 1, -beta, -alpha, ply + 1);
                    if (stopSearch) { board.unmakeMove(move); return alpha; }
                }
            }

            // revert the board back to the actual position
            board.unmakeMove(moves[i]);

            // if score >= beta, the move "failed-high" meaning it is so good that the 
            // opponent should not let you play this move. Because this move is so good, 
            // it isn't necessary to continue searching the children nodes (beta-cutoff). 
            if (score >= beta) {
                // Killer Moves. if a quiet move causes a beta-cutoff, it is more likely
                // to cause a beta cutoff in other parts of the tree. use 2 spaces (the
                // first one being weighted heavier) so that you can store more and also
                // overwrite older beta-cutoffs without completely getting rid of them
                if (ply < 64 && !isCapture && move != killerMoves[ply][0]) {
                    killerMoves[ply][1] = killerMoves[ply][0];
                    killerMoves[ply][0] = move;
                }

                if (!isCapture && !isPromotion) {
                    int colorIdx = board.turn;
                    int from = Move.getStart(move);
                    int to = Move.getEnd(move);
                    int bonus = Math.min(depth * depth, MAX_HISTORY);
                    updateHistory(colorIdx, from, to, bonus); // reward the cutoff move

                    // penalize the rest
                    for (int q=0; q<quietCount; q++) {
                        int prevQuiet = quietMoves[q];
                        if (prevQuiet == move) continue;
                        updateHistory(colorIdx, Move.getStart(prevQuiet), Move.getEnd(prevQuiet), -bonus);
                    }
                }

                if (ply > 0) {
                    int prev = moveHistory[ply - 1];
                    if (prev != -1) {
                        int prevFrom = Move.getStart(prev);
                        int prevTo = Move.getEnd(prev);
                        counterMoves[prevFrom][prevTo] = move;
                    }
                }

                // store beta-cutoff info in the TT and then return the higher bound.
                if (!stopSearch) transpositionTable[ttIndex] = new TranspositionTableEntry(hash, beta, depth, TranspositionTableEntry.LOWER_BOUND, moves[i]);
                return beta;
            }

            // this move didn't cause a beta cutoff; so if it was quiet, remember it so
            // that a later cutoff at this node can penalize it in the history table
            if (!isCapture && !isPromotion && quietCount < quietMoves.length) {
                quietMoves[quietCount++] = move;
            }

            // if score > alpha, the player has found a new best move within the search
            // window. because this is true, we should raise the lower bound to the
            // score of this particular move, so that we are able to find even better
            // moves by comparing other moves' score to this move's score
            if (score > alpha) {
                alpha = score;
                bestMoveFound = moves[i];
            }
        }

        // store the resulting information in the transposition table
        int type = (alpha <= originalAlpha) ? TranspositionTableEntry.UPPER_BOUND : TranspositionTableEntry.EXACT;
        if (!stopSearch) transpositionTable[ttIndex] = new TranspositionTableEntry(hash, alpha, depth, type, bestMoveFound);

        return alpha;
    }

    public int quiescenceSearch(int alpha, int beta, int ply) {
        // same type of alpha-beta pruning on a minimax searching
        // algorithm that we did in SecondEngine.search(depth, α, β)
        if (board.isFiftyMoveRule()) return DRAW_SCORE;
        if (board.isThreefoldRepetition()) return REPETITION_DRAW_SCORE;

        int eval = evaluate();
        if (eval >= beta) return beta;
        if (eval > alpha) alpha = eval;
        if (isTimeUp()) { stopSearch = true; return alpha; }
        nodesSearched++;

        int[] moves;
        if (ply >= 0 && ply < moveStack.length) moves = moveStack[ply];
        else moves = new int[MoveGenerator.MAX_MOVES];
        int count = MoveGenerator.generateLegalMoves(board, moves);
        boolean inCheck = board.getCheckers() != 0;

        for (int i = 0; i < count; i++) {
            // if the move isn't a capture, we can disregard it
            if (!Move.isCapture(moves[i])) continue;
            if (isTimeUp()) { stopSearch = true; return alpha; }
            int mv = moves[i];
            int fromSq2 = Move.getStart(mv);
            if (fromSq2 < 0 || fromSq2 >= 64) continue;
            if (board.boardArray[fromSq2] == -1) continue; // sanity: skip moves from empty squares

            // skip captures that lose material even after all recaptures -- no point
            // searching a line the opponent would never let us reach. Skip this pruning
            // while in check though: the stand-pat eval above is invalid when in check
            // (we might be getting mated), so every capture -- even a "losing" one --
            // may be a necessary defensive try.
            if (!inCheck && see(mv) < 0) continue;

            board.makeMove(mv);
            int score = -quiescenceSearch(-beta, -alpha, ply + 1);
            if (stopSearch) { board.unmakeMove(moves[i]); return alpha; }
            board.unmakeMove(moves[i]);

            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private void orderMoves(int[] moves, int count, int ttMove, int ply, int prevMove) {
        // alpha-beta pruning and iterative deeepning is effective only
        // as much as move ordering is successful. If moves are ordered
        // in an optimal way, lots of branches can be pruned. These 
        // weightings have been tuned through the scientific approach
        // of pulling numbers straight out of my butt. The most optimal 
        // hierarchy, however, is known to be: 1. transposition tables
        // 2. promotions 3. killers 4. countermoves 5. history
        int[] scores = new int[count];

        for (int i = 0; i < count; i++) {
            int move = moves[i];
            
            if (move == ttMove) { // transposition table
                scores[i] = 1000000;
            } 
            else if (Move.isCapture(move)) { // capture
                // SEE tells us the net material result of the full capture sequence on
                // this square (all recaptures included), not just the first exchange --
                // so a capture that wins the piece back can still be sorted correctly
                // even if the immediate trade looks even or bad by MVV-LVA alone.
                int seeScore = see(move);
                scores[i] = (seeScore >= 0) ? (100000 + seeScore) : seeScore;
            }
            else if (Move.getFlags(move) == Move.SHORT_CASTLE || Move.getFlags(move) == Move.LONG_CASTLE) { // prefer castling
                scores[i] = 110000;
            }
            else {
                if (ply < 64) {
                    if (move == killerMoves[ply][0]) {
                        scores[i] = 90000; // Primary killer move
                    } else if (move == killerMoves[ply][1]) {
                        scores[i] = 80000; // Secondary killer move
                    }
                }

                if (prevMove != -1) {
                    int prevFrom = Move.getStart(prevMove);
                    int prevTo = Move.getEnd(prevMove);
                    if (move == counterMoves[prevFrom][prevTo]) {
                        scores[i] = Math.max(scores[i], 85000);
                    }
                }

                int from = Move.getStart(move);
                int to = Move.getEnd(move);
                scores[i] += historyTable[board.turn][from][to] / 32;
            }
            
            if (Move.getFlags(move) >= Move.PROMOTION_KNIGHT) {
                scores[i] += 300000;
            }
        }

        for (int i = 1; i < count; i++) {
            int keyMove = moves[i];
            int keyScore = scores[i];
            int j = i - 1;
            
            while (j >= 0 && scores[j] < keyScore) {
                moves[j + 1] = moves[j];
                scores[j + 1] = scores[j];
                j--;
            }
            moves[j + 1] = keyMove;
            scores[j + 1] = keyScore;
        }
    }

    public int getBestMove(int totalTimeLeft, int increment) {
        int maxDepth = 40;
        long startTime = System.currentTimeMillis();
        long timeLimit = Math.max(1, (totalTimeLeft / 40) + increment);

        // if we're still in the opening (first 4 full moves = 8 plies), play faster
        if (board.getMoveCount() < 8) {
            timeLimit = Math.min(timeLimit, 1000 + increment);
        }

        // hardcode in 1. e4 e5
        if (board.getMoveCount() == 1 && board.turn == Board.BLACK) {
            int e4 = 3 * 8 + 4; // e4 = 28
            int e5 = 4 * 8 + 4; // e5 = 36
            int e7 = 6 * 8 + 4; // e7 = 52

            if (board.boardArray[e4] == Board.WP && board.boardArray[e5] == -1 && board.boardArray[e7] == Board.BP) {
                int reply = Move.encode(e7, e5, Move.DOUBLE_PAWN);
                int[] quick = new int[MoveGenerator.MAX_MOVES];
                int n = MoveGenerator.generateLegalMoves(board, quick);
                for (int i = 0; i < n; i++) {
                    if (quick[i] == reply) return reply;
                }
            }
        }

        int overallBestMove = -1;
        stopSearch = false;
        searchStopTime = startTime + timeLimit - 10;
        if (searchStopTime <= startTime) searchStopTime = startTime + 1;

        nodesSearched = 0;
        int completedDepth = 0;

        int previousScore = 0;
        int aspirationWindow = 50;

        // this is the iterative deepening loop. this works by searching to a shallow 
        // depth first, and then searching to the next highest depth, and then the next,
        // and so on until the allotted time is up.
        for (int currentDepth = 1; currentDepth <= maxDepth; currentDepth++) {
            int alpha = -Integer.MAX_VALUE;
            int beta = Integer.MAX_VALUE;

            if (currentDepth > 1) {
                alpha = previousScore - aspirationWindow;
                beta = previousScore + aspirationWindow;
            }

            int result = search(currentDepth, alpha, beta, 0);
            if (stopSearch) break;

            if (currentDepth > 1) {
                if (result <= alpha) {
                    result = search(currentDepth, -Integer.MAX_VALUE, beta, 0);
                    if (stopSearch) break;
                } else if (result >= beta) {
                    result = search(currentDepth, alpha, Integer.MAX_VALUE, 0);
                    if (stopSearch) break;
                }
            }

            long hash = board.getCurrentHash();
            TranspositionTableEntry entry = transpositionTable[getIndex(hash)];
            if (entry != null && entry.key == hash && entry.bestMove != -1) {
                overallBestMove = entry.bestMove;
            }

            completedDepth = currentDepth;

            previousScore = result;
            if (System.currentTimeMillis() >= searchStopTime) {
                break;
            }
        }

        if (overallBestMove == -1) {
            int[] fallbackMoves = new int[MoveGenerator.MAX_MOVES];
            int count = MoveGenerator.generateLegalMoves(board, fallbackMoves);
            if (count > 0) overallBestMove = fallbackMoves[0];
        }

        long elapsedMs = Math.max(1, System.currentTimeMillis() - startTime);
        lastNodesSearched = nodesSearched;
        lastDepthReached = completedDepth;
        lastElapsedMs = elapsedMs;
        lastNodesPerSecond = (nodesSearched * 1000L) / elapsedMs;
        
        return overallBestMove;
    }

    public void applyParams(int[] p) {
        PAWN   = p[0]; KNIGHT = p[1]; BISHOP = p[2]; ROOK = p[3]; QUEEN = p[4];
        KNIGHT_MOB_MG = p[5];  KNIGHT_MOB_EG = p[6];
        BISHOP_MOB_MG = p[7];  BISHOP_MOB_EG = p[8];
        ROOK_MOB_MG   = p[9];  ROOK_MOB_EG   = p[10];
        QUEEN_MOB_MG  = p[11]; QUEEN_MOB_EG  = p[12];
        for (int i = 0; i < 8; i++) {
            PASSED_PAWN_MG[i] = p[13 + i];
            PASSED_PAWN_EG[i] = p[21 + i];
        }
        KING_PROXIMITY_WEIGHT    = p[29];
        ISOLATED_PAWN_PENALTY_MG = p[30];
        ISOLATED_PAWN_PENALTY_EG = p[31];
        System.arraycopy(p,  32, PAWN_PST,        0, 64);
        System.arraycopy(p,  96, KNIGHT_PST,      0, 64);
        System.arraycopy(p, 160, BISHOP_PST,      0, 64);
        System.arraycopy(p, 224, ROOK_PST,        0, 64);
        System.arraycopy(p, 288, QUEEN_PST,       0, 64);
        System.arraycopy(p, 352, KING_PST,        0, 64);
        System.arraycopy(p, 416, KING_ENDGAME_PST,0, 64);
        KS_SHIELD_MISSING     = p[480]; KS_SHIELD_ADVANCED    = p[481];
        KS_STORM_RANK3        = p[482]; KS_STORM_RANK4        = p[483]; KS_STORM_RANK5 = p[484];
        KS_FILE_NO_OWN_PAWN   = p[485]; KS_FILE_NO_ENEMY_PAWN = p[486];
        KS_ATTACK_WEIGHT_N    = p[487]; KS_ATTACK_WEIGHT_B    = p[488];
        KS_ATTACK_WEIGHT_R    = p[489]; KS_ATTACK_WEIGHT_Q    = p[490];
        KS_MIN_ATTACKERS      = p[491]; KS_DANGER_QUAD        = p[492];
        KS_DANGER_MAX         = p[493]; KS_NO_QUEEN_SCALE     = p[494];
        buildKingSafetyTables();
    }

    // the current constants in applyParams() layout (495 values) -- lets the tuner start from the
    // engine's real values instead of a hand-copied table that can drift out of date
    public static final int NUM_PARAMS = 495;

    public int[] getParams() {
        int[] p = new int[NUM_PARAMS];
        p[0] = PAWN; p[1] = KNIGHT; p[2] = BISHOP; p[3] = ROOK; p[4] = QUEEN;
        p[5] = KNIGHT_MOB_MG;  p[6] = KNIGHT_MOB_EG;
        p[7] = BISHOP_MOB_MG;  p[8] = BISHOP_MOB_EG;
        p[9] = ROOK_MOB_MG;    p[10] = ROOK_MOB_EG;
        p[11] = QUEEN_MOB_MG;  p[12] = QUEEN_MOB_EG;
        for (int i = 0; i < 8; i++) {
            p[13 + i] = PASSED_PAWN_MG[i];
            p[21 + i] = PASSED_PAWN_EG[i];
        }
        p[29] = KING_PROXIMITY_WEIGHT;
        p[30] = ISOLATED_PAWN_PENALTY_MG;
        p[31] = ISOLATED_PAWN_PENALTY_EG;
        System.arraycopy(PAWN_PST,         0, p,  32, 64);
        System.arraycopy(KNIGHT_PST,       0, p,  96, 64);
        System.arraycopy(BISHOP_PST,       0, p, 160, 64);
        System.arraycopy(ROOK_PST,         0, p, 224, 64);
        System.arraycopy(QUEEN_PST,        0, p, 288, 64);
        System.arraycopy(KING_PST,         0, p, 352, 64);
        System.arraycopy(KING_ENDGAME_PST, 0, p, 416, 64);
        p[480] = KS_SHIELD_MISSING;   p[481] = KS_SHIELD_ADVANCED;
        p[482] = KS_STORM_RANK3;      p[483] = KS_STORM_RANK4;       p[484] = KS_STORM_RANK5;
        p[485] = KS_FILE_NO_OWN_PAWN; p[486] = KS_FILE_NO_ENEMY_PAWN;
        p[487] = KS_ATTACK_WEIGHT_N;  p[488] = KS_ATTACK_WEIGHT_B;
        p[489] = KS_ATTACK_WEIGHT_R;  p[490] = KS_ATTACK_WEIGHT_Q;
        p[491] = KS_MIN_ATTACKERS;    p[492] = KS_DANGER_QUAD;
        p[493] = KS_DANGER_MAX;       p[494] = KS_NO_QUEEN_SCALE;
        return p;
    }
}
