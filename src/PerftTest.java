import java.util.*;

/**
 * Perft regression test for Board + MoveGenerator.
 *
 * Perft counts every leaf of the legal-move tree to a fixed depth and compares it against the
 * published reference counts (chessprogramming.org/Perft_Results). One wrong count means move
 * generation or make/unmake is broken somewhere -- castling, en passant, promotions, pins and
 * checks are all exercised by these positions.
 *
 * It also runs a shallow integrity sweep: after every makeMove the incremental Zobrist hash must
 * match a from-scratch hash, and every unmakeMove must restore the board exactly. A hash bug
 * doesn't change perft counts but silently corrupts the TT and repetition detection.
 *
 * Usage: java PerftTest [maxDepth]     (default 5; exits with code 1 on any mismatch)
 */
public class PerftTest {

    private static final String[] NAMES = {
        "startpos", "kiwipete", "cpw-pos3", "cpw-pos4", "cpw-pos4-mirrored", "cpw-pos5", "cpw-pos6"
    };

    private static final String[] FENS = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
    };

    // EXPECTED[i][d - 1] = number of leaf nodes at depth d
    private static final long[][] EXPECTED = {
        {20, 400, 8902, 197281, 4865609, 119060324L},
        {48, 2039, 97862, 4085603, 193690690L},
        {14, 191, 2812, 43238, 674624, 11030083L},
        {6, 264, 9467, 422333, 15833292L},
        {6, 264, 9467, 422333, 15833292L},
        {44, 1486, 62379, 2103487, 89941194L},
        {46, 2079, 89890, 3894594, 164075551L},
    };

    private static final int INTEGRITY_DEPTH = 3;
    private static int integrityErrors = 0;

    public static void main(String[] args) {
        int maxDepth = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        int mismatches = 0;
        Board board = new Board();
        int[][] buffers = new int[Math.max(maxDepth, INTEGRITY_DEPTH) + 1][MoveGenerator.MAX_MOVES];

        System.out.printf("%-18s %5s %14s %14s %8s %10s%n", "position", "depth", "expected", "actual", "result", "Mnps");
        for (int p = 0; p < FENS.length; p++) {
            for (int d = 1; d <= Math.min(maxDepth, EXPECTED[p].length); d++) {
                board.convertFENtoPosition(FENS[p]);
                long start = System.nanoTime();
                long nodes = perft(board, d, buffers);
                double secs = Math.max(1e-9, (System.nanoTime() - start) / 1e9);
                boolean ok = nodes == EXPECTED[p][d - 1];
                System.out.printf("%-18s %5d %14d %14d %8s %10.1f%n", NAMES[p], d, EXPECTED[p][d - 1], nodes,
                        ok ? "ok" : "MISMATCH", nodes / secs / 1e6);
                if (!ok) {
                    mismatches++;
                    board.convertFENtoPosition(FENS[p]);
                    divide(board, d, buffers);
                    break; // deeper depths of this position will be wrong too
                }
            }
        }

        System.out.println();
        System.out.println("Integrity sweep (hash + make/unmake restore) to depth " + INTEGRITY_DEPTH + "...");
        for (int p = 0; p < FENS.length; p++) {
            board.convertFENtoPosition(FENS[p]);
            if (board.currentHash != fullHash(board)) reportIntegrity(NAMES[p], "hash after FEN load differs from full hash", 0);
            integrity(board, INTEGRITY_DEPTH, buffers, NAMES[p]);
        }
        System.out.println(integrityErrors == 0 ? "Integrity sweep: ok" : "Integrity sweep: " + integrityErrors + " ERRORS");

        System.out.println();
        System.out.println(mismatches == 0 && integrityErrors == 0 ? "PERFT: ALL PASSED"
                : "PERFT: FAILED (" + mismatches + " count mismatches, " + integrityErrors + " integrity errors)");
        System.exit(mismatches == 0 && integrityErrors == 0 ? 0 : 1);
    }

    private static long perft(Board b, int depth, int[][] buffers) {
        int[] moves = buffers[depth];
        int n = MoveGenerator.generateLegalMoves(b, moves);
        if (depth == 1) return n;
        long nodes = 0;
        for (int i = 0; i < n; i++) {
            b.makeMove(moves[i]);
            nodes += perft(b, depth - 1, buffers);
            b.unmakeMove(moves[i]);
        }
        return nodes;
    }

    // per-root-move breakdown, printed on a mismatch so the bad move can be compared with a
    // reference engine's "go perft" divide output
    private static void divide(Board b, int depth, int[][] buffers) {
        int[] moves = buffers[depth].clone();
        int n = MoveGenerator.generateLegalMoves(b, moves);
        System.out.println("  divide at depth " + depth + " (" + n + " root moves):");
        for (int i = 0; i < n; i++) {
            b.makeMove(moves[i]);
            long count = depth > 1 ? perft(b, depth - 1, buffers) : 1;
            b.unmakeMove(moves[i]);
            System.out.printf("    %s%s %d%n", b.squareToCoordinates(Move.getStart(moves[i])),
                    b.squareToCoordinates(Move.getEnd(moves[i])), count);
        }
    }

    private static void integrity(Board b, int depth, int[][] buffers, String name) {
        if (depth == 0) return;
        int[] moves = buffers[depth];
        int n = MoveGenerator.generateLegalMoves(b, moves);
        for (int i = 0; i < n; i++) {
            int m = moves[i];
            long[] bitboards = b.pieceBitboards.clone();
            int[] squares = b.boardArray.clone();
            long hash = b.currentHash;
            int castling = b.castlingRights, ep = b.enPassantSquare, turn = b.turn, halfmove = b.getHalfmoveClock();

            b.makeMove(m);
            if (b.currentHash != fullHash(b)) reportIntegrity(name, "incremental hash != full hash after " + moveStr(b, m), depth);
            String consistency = consistencyError(b);
            if (consistency != null) reportIntegrity(name, consistency + " after " + moveStr(b, m), depth);
            integrity(b, depth - 1, buffers, name);
            b.unmakeMove(m);

            if (!Arrays.equals(bitboards, b.pieceBitboards) || !Arrays.equals(squares, b.boardArray)
                    || hash != b.currentHash || castling != b.castlingRights || ep != b.enPassantSquare
                    || turn != b.turn || halfmove != b.getHalfmoveClock()) {
                reportIntegrity(name, "unmakeMove did not restore the position after " + moveStr(b, m), depth);
            }
        }
    }

    // recomputes the Zobrist hash from scratch without touching the board (calculateFullHash mutates)
    static long fullHash(Board b) {
        long h = 0L;
        for (int i = 0; i < 12; i++) {
            long pieces = b.pieceBitboards[i];
            while (pieces != 0) {
                h ^= Zobrist.pieceKeys[i][Long.numberOfTrailingZeros(pieces)];
                pieces &= pieces - 1;
            }
        }
        if (b.turn == Board.WHITE) h ^= Zobrist.sideToMove;
        h ^= Zobrist.castlingKeys[b.castlingRights];
        if (b.enPassantSquare != -1) h ^= Zobrist.enPassantKeys[b.enPassantSquare % 8];
        return h;
    }

    private static String consistencyError(Board b) {
        long all = 0L;
        for (int i = 0; i < 12; i++) {
            if ((all & b.pieceBitboards[i]) != 0) return "two piece types on one square";
            all |= b.pieceBitboards[i];
        }
        if (all != b.allPieces) return "allPieces out of sync with piece bitboards";
        for (int sq = 0; sq < 64; sq++) {
            if (b.boardArray[sq] != b.getPieceAt(sq)) return "boardArray out of sync on " + b.squareToCoordinates(sq);
        }
        if (Long.bitCount(b.pieceBitboards[Board.WK]) != 1 || Long.bitCount(b.pieceBitboards[Board.BK]) != 1) return "king count != 1";
        return null;
    }

    private static void reportIntegrity(String name, String msg, int depth) {
        integrityErrors++;
        if (integrityErrors <= 10) System.out.println("  INTEGRITY [" + name + ", remaining depth " + depth + "]: " + msg);
    }

    private static String moveStr(Board b, int m) {
        return b.squareToCoordinates(Move.getStart(m)) + b.squareToCoordinates(Move.getEnd(m));
    }
}
