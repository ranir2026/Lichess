import java.util.*;

/**
 * Shared position sets and FEN helpers for the test/benchmark tools (PerftTest,
 * EvalSymmetryTest, KingSafetyTest, SpeedBench). Everything here is deterministic --
 * the random-walk positions come from a fixed seed -- so results are reproducible.
 */
public class TestPositions {

    // 20 fixed positions for SpeedBench: 10 openings/middlegames, 10 late middlegames/endgames
    public static final String[] BENCH = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
        "r1bq1rk1/2p1bppp/p1np1n2/1p2p3/4P3/1BP2N1P/PP1P1PP1/RNBQR1K1 b - - 0 9",
        "rnbqk2r/ppp1bppp/4pn2/3p2B1/2PP4/2N5/PP2PPPP/R2QKBNR w KQkq - 4 5",
        "rnbqkb1r/1p2pppp/p2p1n2/8/3NP3/2N5/PPP2PPP/R1BQKB1R w KQkq - 0 6",
        "r1bq1rk1/pppnn1bp/3p2p1/3Pp3/2P1Pp2/2N2P2/PP2BBPP/R2QNRK1 b - - 1 12",
        "r1bq1rk1/pp2ppbp/2np1np1/8/3NP3/2N1BP2/PPPQ2PP/2KR1B1R b - - 3 9",
        "r2q1rk1/pp2bppp/2n1bn2/3p4/3P4/2NBBN2/PP3PPP/R2Q1RK1 w - - 0 11",
        "r1b1k2r/ppppnppp/2n2q2/2b5/3NP3/2P1B3/PP3PPP/RN1QKB1R w KQkq - 0 7",
        "2r2rk1/pp3pp1/2n1p2p/q2pP3/3P1P2/P1PB4/5QPP/R4RK1 w - - 0 20",
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        "8/5pk1/6p1/7p/7P/6P1/r4PK1/1R6 w - - 0 40",
        "1K1k4/1P6/8/8/8/8/r7/2R5 w - - 0 1",
        "4k3/8/8/3PK3/8/8/7r/R7 w - - 0 1",
        "8/8/8/4k3/8/8/4P3/4K3 w - - 0 1",
        "8/k7/3p4/p2P1p2/P2P1P2/8/8/K7 w - - 0 1",
        "6k1/5p1p/6p1/8/8/1Q4P1/5PKP/3q4 w - - 0 45",
        "8/2b2k2/5p2/p1p2P2/P1P3K1/6N1/8/8 w - - 0 50",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
    };

    // extra hand-picked positions: perft edge cases, odd material, castled/uncastled kings
    public static final String[] HAND_PICKED = {
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1",
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
        "r1bq1rk1/pppp1ppp/2n2n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQ1RK1 b - - 0 6",
        "r1bq1rk1/ppp2pp1/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PP1/R1BQ1RK1 w - - 0 8",
        "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
        "2kr3r/ppp2ppp/2n5/3q4/3P4/2P5/P4PPP/R1BQ1RK1 w - - 0 15",
        "r4rk1/ppp2ppp/8/8/8/8/PPP2PPP/2KR3R w - - 0 20",
        "6k1/5ppp/8/8/8/8/5PPP/6K1 w - - 0 30",
        "6k1/8/6PP/8/8/8/8/6K1 b - - 0 40",
        "8/8/8/8/8/8/8/K6k w - - 0 1",
        "4k3/8/8/8/8/8/8/4K2R w K - 0 1",
        "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
        "1QQQ4/8/8/8/8/8/8/k3K3 b - - 0 1",
        "8/P7/8/8/8/8/7p/K6k w - - 0 1",
        "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
        "r1b2rk1/pp1nqppp/2pbpn2/3p4/2PP4/2NBPN2/PPQ2PPP/R1B2RK1 w - - 0 9",
        "r2qr1k1/1b1nbppp/p2p1n2/1p2p3/3PP3/2P2NNP/PPBB1PP1/R2QR1K1 w - - 0 15",
        "3r1rk1/pp3ppp/2n1b3/q1p5/2P1P3/P1Q1BP2/4B1PP/R4RK1 b - - 0 18",
        "5rk1/1R4pp/p7/3p4/3Pn3/2P1rP2/6PP/5RK1 w - - 0 30",
        "2rq1rk1/pb1nbppp/1p2pn2/2pp4/2PP4/1PN1PN2/PB1QBPPP/2R2RK1 b - - 0 12",
    };

    // hand-built king-safety positions used by KingSafetyTest (all white to move). Pairs differ
    // only in the king-safety feature being tested.
    public static final String KS_SHIELD_INTACT  = "r1bq1rk1/pppp1ppp/2n2n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQ1RK1 w - - 0 7";
    public static final String KS_SHIELD_PUSHED  = "r1bq1rk1/pppp1ppp/2n2n2/2b1p3/2B1P1PP/2NP1N2/PPP2P2/R1BQ1RK1 w - - 0 7";
    public static final String KS_QN_ATTACK      = "r1b2rk1/pppq1ppp/2n5/3p2NQ/3P4/8/PPP2PPP/R1B2RK1 w - - 0 12";
    public static final String KS_QN_AWAY        = "r1b2rk1/pppq1ppp/2n5/3p4/3P4/2N5/PPPQ1PPP/R1B2RK1 w - - 0 12";
    public static final String KS_H_FILE_CLOSED  = "2kr3r/pppq1ppp/8/8/8/8/PPPQ1PPP/R4RK1 w - - 0 20";
    public static final String KS_H_FILE_OPEN    = "2kr3r/pppq1pp1/8/8/8/8/PPPQ1PP1/R4RK1 w - - 0 20";
    public static final String KS_NO_STORM       = "2kr3r/pppq1ppp/2n5/8/8/2N5/PPPQ1PPP/2R2RK1 w - - 0 15";
    public static final String KS_STORM          = "2kr3r/pppq1p2/2n5/8/6pp/2N5/PPPQ1PPP/2R2RK1 w - - 0 15";
    public static final String KS_ATTACK_QUEEN   = "r1b2rk1/ppp2ppp/2n5/6N1/8/3B3R/PPP2PPP/Q5K1 w - - 0 15";
    public static final String KS_ATTACK_NOQUEEN = "r1b2rk1/ppp2ppp/2n5/6N1/8/3B3R/PPP2PPP/6K1 w - - 0 15";
    public static final String KS_KP_ENDGAME     = "8/5k2/8/3p4/3P4/8/5K2/8 w - - 0 1";
    public static final String KS_KP_STORMED     = "6k1/8/8/8/6pp/8/8/6K1 w - - 0 1";
    public static final String KS_AFTER_E4_E5    = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2";
    public static final String[] KING_SAFETY = {
        KS_SHIELD_INTACT, KS_SHIELD_PUSHED, KS_QN_ATTACK, KS_QN_AWAY, KS_H_FILE_CLOSED, KS_H_FILE_OPEN,
        KS_NO_STORM, KS_STORM, KS_ATTACK_QUEEN, KS_ATTACK_NOQUEEN, KS_KP_ENDGAME, KS_KP_STORMED, KS_AFTER_E4_E5,
    };

    public static List<String> symmetrySet() {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        set.addAll(Arrays.asList(BENCH));
        set.addAll(Arrays.asList(HAND_PICKED));
        set.addAll(Arrays.asList(KING_SAFETY));
        set.addAll(randomWalkPositions(20261022L, 300));
        return new ArrayList<String>(set);
    }

    /**
     * Plays seeded random legal moves out of the hand-picked/bench positions. Each walk has its
     * own length and capture bias, so the set spans openings, middlegames and bare endgames.
     */
    public static List<String> randomWalkPositions(long seed, int count) {
        Random rng = new Random(seed);
        List<String> seeds = new ArrayList<String>();
        seeds.addAll(Arrays.asList(BENCH));
        seeds.addAll(Arrays.asList(HAND_PICKED));

        LinkedHashSet<String> out = new LinkedHashSet<String>();
        Board b = new Board();
        int[] moves = new int[MoveGenerator.MAX_MOVES];
        int[] captures = new int[MoveGenerator.MAX_MOVES];
        int attempts = 0;
        while (out.size() < count && attempts < count * 10) {
            attempts++;
            b.convertFENtoPosition(seeds.get(rng.nextInt(seeds.size())));
            int plies = 1 + rng.nextInt(80);
            double captureBias = rng.nextDouble() * 0.7;
            for (int p = 0; p < plies; p++) {
                int n = MoveGenerator.generateLegalMoves(b, moves);
                if (n == 0 || b.isFiftyMoveRule()) break;
                int nc = 0;
                for (int i = 0; i < n; i++) if (Move.isCapture(moves[i])) captures[nc++] = moves[i];
                int m = (nc > 0 && rng.nextDouble() < captureBias) ? captures[rng.nextInt(nc)] : moves[rng.nextInt(n)];
                b.makeMove(m);
            }
            out.add(toFEN(b));
        }
        return new ArrayList<String>(out);
    }

    private static final String PIECE_CHARS = "PNBRQKpnbrqk";

    public static String toFEN(Board b) {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = b.getPieceAt(rank * 8 + file);
                if (piece == -1) { empty++; continue; }
                if (empty > 0) { sb.append(empty); empty = 0; }
                sb.append(PIECE_CHARS.charAt(piece));
            }
            if (empty > 0) sb.append(empty);
            if (rank > 0) sb.append('/');
        }
        sb.append(b.turn == Board.WHITE ? " w " : " b ");
        String castling = ((b.castlingRights & 1) != 0 ? "K" : "") + ((b.castlingRights & 2) != 0 ? "Q" : "")
                        + ((b.castlingRights & 4) != 0 ? "k" : "") + ((b.castlingRights & 8) != 0 ? "q" : "");
        sb.append(castling.isEmpty() ? "-" : castling);
        sb.append(' ').append(b.enPassantSquare == -1 ? "-" : b.squareToCoordinates(b.enPassantSquare));
        sb.append(' ').append(b.getHalfmoveClock()).append(" 1");
        return sb.toString();
    }

    /**
     * Colour-flips a FEN: mirrors the ranks (a1 <-> a8), swaps piece colours, side to move,
     * castling rights and the en-passant rank. A colour-symmetric eval must score the flipped
     * position identically from the side to move's point of view.
     */
    public static String flipFEN(String fen) {
        String[] parts = fen.trim().split("\\s+");
        String[] ranks = parts[0].split("/");
        StringBuilder board = new StringBuilder();
        for (int r = ranks.length - 1; r >= 0; r--) {
            board.append(swapCase(ranks[r]));
            if (r > 0) board.append('/');
        }
        String side = parts[1].equals("w") ? "b" : "w";

        String castling = "-";
        if (!parts[2].equals("-")) {
            String swapped = swapCase(parts[2]);
            castling = (swapped.contains("K") ? "K" : "") + (swapped.contains("Q") ? "Q" : "")
                     + (swapped.contains("k") ? "k" : "") + (swapped.contains("q") ? "q" : "");
        }

        String ep = "-";
        if (!parts[3].equals("-")) ep = "" + parts[3].charAt(0) + (char) ('1' + ('8' - parts[3].charAt(1)));

        String halfmove = parts.length > 4 ? parts[4] : "0";
        String fullmove = parts.length > 5 ? parts[5] : "1";
        return board + " " + side + " " + castling + " " + ep + " " + halfmove + " " + fullmove;
    }

    private static String swapCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append(Character.toLowerCase(c));
            else if (Character.isLowerCase(c)) sb.append(Character.toUpperCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }

    // compact position snapshot (12 piece bitboards + side to move). evaluate() only reads the
    // bitboards, occupancies and turn, so loading a snapshot is enough to evaluate -- far
    // cheaper than re-parsing a FEN (used by the eval micro-benchmark).
    public static long[] snapshot(Board b) {
        long[] s = new long[13];
        System.arraycopy(b.pieceBitboards, 0, s, 0, 12);
        s[12] = b.turn;
        return s;
    }

    public static void load(Board b, long[] s) {
        System.arraycopy(s, 0, b.pieceBitboards, 0, 12);
        b.turn = (int) s[12];
        b.updateOccupancies();
    }
}
