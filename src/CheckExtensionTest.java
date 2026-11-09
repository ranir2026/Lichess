import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Checks the check extension in TurquoiseBot (TurquoiseBot.CHECK_EXTENSION).
 *
 * TurquoiseBot's RFP and QS settings are forced to QuiescenceEvasionBot v3.0's frozen values first
 * (RFP_MARGIN = 90, RFP_MAX_DEPTH = 6, QS_EVASIONS = true), so the comparison is like for like.
 *
 *  1. Check extension off (TurquoiseBot.CHECK_EXTENSION = false): TurquoiseBot must search exactly like
 *     QuiescenceEvasionBot v3.0 on every TestPositions.BENCH position at depths 1..7 -- same root score,
 *     same cumulative node count and same root best move.
 *  2. Check extension on: the node counts must differ from QuiescenceEvasionBot on at least one
 *     position, showing the extension is actually live.
 *  3. Mate at a lower depth: on the MATE_CANDIDATES positions, the extension must find a mate
 *     (score >= 50000) at a lower iterative-deepening depth than with it off on at least one.
 *  4. Long check sequences: a queen ending where both sides can give check many times must search to
 *     depth 10 with the extension on without an ArrayIndexOutOfBoundsException or other RuntimeException.
 *
 * Usage: java CheckExtensionTest   (exits with code 1 on any failure)
 */
public class CheckExtensionTest {

    private static final int MAX_DEPTH = 7;
    private static final int MATE_DEPTH_LIMIT = 12;
    private static final String[] MATE_CANDIDATES = {
        "8/8/8/4k3/8/8/1R6/R3K3 w - - 0 1",
        "8/8/3k4/8/8/8/6R1/4K2R w - - 0 1",
        "8/8/8/8/8/2k5/7R/K5R1 w - - 0 1",
    };
    private static final String QUEEN_ENDING = "6k1/8/8/8/8/8/5q2/1Q5K w - - 0 1";
    private static int failures = 0;

    public static void main(String[] args) {
        boolean startupCheckExt = TurquoiseBot.CHECK_EXTENSION;
        int startupRfpMargin = TurquoiseBot.RFP_MARGIN;
        int startupRfpMaxDepth = TurquoiseBot.RFP_MAX_DEPTH;
        boolean startupEvasions = TurquoiseBot.QS_EVASIONS;
        System.out.println("Check extension at startup: " + TurquoiseBot.CHECK_EXTENSION);

        // QuiescenceEvasionBot's frozen values, so the comparison with it is like for like
        TurquoiseBot.RFP_MARGIN = 90;
        TurquoiseBot.RFP_MAX_DEPTH = 6;
        TurquoiseBot.QS_EVASIONS = true;

        // ---- 1. check extension off: must equal QuiescenceEvasionBot v3.0 exactly
        System.out.println();
        TurquoiseBot.CHECK_EXTENSION = false;
        final String offName = "check extension off: score, nodes and best move == QuiescenceEvasionBot (20 positions, depth 1-7)";
        try {
            int mismatches = 0;
            for (String fen : TestPositions.BENCH) {
                if (!sameTree(fen)) mismatches++;
            }
            check(offName, mismatches == 0, mismatches + " positions differ");
        } catch (ReflectiveOperationException | RuntimeException e) {
            check(offName, false, "reflection/search error: " + e);
        }

        // ---- 2. check extension on: node counts must differ somewhere
        System.out.println();
        TurquoiseBot.CHECK_EXTENSION = true;
        final String onName = "check extension on: node count differs from QuiescenceEvasionBot on at least one position";
        try {
            int differing = 0;
            long totalT = 0, totalQ = 0;
            for (String fen : TestPositions.BENCH) {
                Board a = new Board(), b = new Board();
                a.convertFENtoPosition(fen);
                b.convertFENtoPosition(fen);
                TurquoiseBot t = new TurquoiseBot(a);
                QuiescenceEvasionBot q = new QuiescenceEvasionBot(b);
                for (int d = 1; d <= MAX_DEPTH; d++) {
                    t.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
                    q.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
                }
                System.out.println("  nodes TurquoiseBot " + t.nodesSearched + " vs QuiescenceEvasionBot " + q.nodesSearched + "  " + fen);
                if (t.nodesSearched != q.nodesSearched) differing++;
                totalT += t.nodesSearched;
                totalQ += q.nodesSearched;
            }
            System.out.println("  total nodes TurquoiseBot " + totalT + " vs QuiescenceEvasionBot " + totalQ + " (" + differing + " of " + TestPositions.BENCH.length + " positions differ)");
            check(onName, differing >= 1, "differing " + differing);
        } catch (RuntimeException e) {
            check(onName, false, "search error: " + e);
        }

        // ---- 3. mate found at a lower depth with the extension on than off
        System.out.println();
        final String mateName = "check extension on: finds a mate at a lower depth than off on at least one candidate";
        try {
            boolean anyLower = false;
            for (String fen : MATE_CANDIDATES) {
                TurquoiseBot.CHECK_EXTENSION = true;
                int on = firstMateDepth(fen);
                TurquoiseBot.CHECK_EXTENSION = false;
                int off = firstMateDepth(fen);
                System.out.println("  first mate depth: on=" + on + " off=" + off + "  " + fen);
                if (on < off) anyLower = true;
            }
            check(mateName, anyLower, "no candidate found a mate earlier with the extension on");
        } catch (RuntimeException e) {
            check(mateName, false, "search error: " + e);
        }

        // ---- 4. long check sequences must not overrun the per-ply arrays
        System.out.println();
        final String longName = "check extension on: queen ending with checks for both sides searches to depth 10 without error";
        try {
            TurquoiseBot.CHECK_EXTENSION = true;
            Board b = new Board();
            b.convertFENtoPosition(QUEEN_ENDING);
            TurquoiseBot t = new TurquoiseBot(b);
            int score = 0;
            for (int d = 1; d <= 10; d++) {
                score = t.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
            }
            System.out.println("  queen ending: final score " + score + ", nodesSearched " + t.nodesSearched);
            check(longName, true, "");
        } catch (RuntimeException e) {
            check(longName, false, "exception: " + e);
        }

        // ---- restore
        TurquoiseBot.CHECK_EXTENSION = startupCheckExt;
        TurquoiseBot.RFP_MARGIN = startupRfpMargin;
        TurquoiseBot.RFP_MAX_DEPTH = startupRfpMaxDepth;
        TurquoiseBot.QS_EVASIONS = startupEvasions;

        System.out.println();
        System.out.println(failures == 0 ? "CHECK EXTENSION TEST: ALL PASSED" : "CHECK EXTENSION TEST: " + failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // first iterative-deepening depth (1..MATE_DEPTH_LIMIT) whose root score is a mate score, or 13
    private static int firstMateDepth(String fen) {
        Board b = new Board();
        b.convertFENtoPosition(fen);
        TurquoiseBot t = new TurquoiseBot(b);
        for (int d = 1; d <= MATE_DEPTH_LIMIT; d++) {
            int score = t.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
            if (score >= 50000) return d;
        }
        return MATE_DEPTH_LIMIT + 1;
    }

    // searches one position to MAX_DEPTH with both engines, comparing score, cumulative nodes and
    // root best move after every depth. Prints the first mismatch and returns false.
    private static boolean sameTree(String fen) throws ReflectiveOperationException {
        Board a = new Board(), b = new Board();
        a.convertFENtoPosition(fen);
        b.convertFENtoPosition(fen);
        TurquoiseBot t = new TurquoiseBot(a);
        QuiescenceEvasionBot q = new QuiescenceEvasionBot(b);
        for (int d = 1; d <= MAX_DEPTH; d++) {
            int ts = t.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
            int qs = q.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
            long tn = t.nodesSearched;
            long qn = q.nodesSearched;
            int tm = rootBestMove(t, a);
            int qm = rootBestMove(q, b);
            if (ts != qs || tn != qn || tm != qm) {
                System.out.println("  differs at depth " + d + ": score " + ts + " vs " + qs
                        + ", nodes " + tn + " vs " + qn + ", best move " + tm + " vs " + qm + ", FEN " + fen);
                return false;
            }
        }
        return true;
    }

    // root best move from the bot's own transposition table, or -1 if the root entry is missing
    private static int rootBestMove(Object bot, Board board) throws ReflectiveOperationException {
        Field tableField = bot.getClass().getDeclaredField("transpositionTable");
        tableField.setAccessible(true);
        TranspositionTableEntry[] table = (TranspositionTableEntry[]) tableField.get(bot);

        Method getIndex = bot.getClass().getDeclaredMethod("getIndex", long.class);
        getIndex.setAccessible(true);

        long hash = board.getCurrentHash();
        int index = (Integer) getIndex.invoke(bot, hash);
        TranspositionTableEntry entry = table[index];
        return (entry != null && entry.key == hash) ? entry.bestMove : -1;
    }

    private static void check(String name, boolean ok, String detail) {
        if (!ok) failures++;
        System.out.println((ok ? "  ok    " : "  FAIL  ") + name + (ok || detail.isEmpty() ? "" : "  (" + detail + ")"));
    }
}
