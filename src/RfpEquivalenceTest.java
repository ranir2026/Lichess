import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Checks reverse futility pruning in TurquoiseBot.
 *
 *  1. RFP off (TurquoiseBot.RFP_MARGIN = 100000), in-check quiescence off (QS_EVASIONS = false) and the
 *     check extension off (CHECK_EXTENSION = false):
 *     TurquoiseBot must search exactly like KingSafetyBot
 *     on every TestPositions.BENCH position at depths 1..7 -- same root score, same cumulative node
 *     count and same root best move -- proof that nothing else changed since v2.8.
 *  2. RFP on (startup margin), QS evasions and check extension still off: the node counts must differ from KingSafetyBot on at least one
 *     position, showing RFP is actually live.
 *
 * Usage: java RfpEquivalenceTest   (exits with code 1 on any failure)
 */
public class RfpEquivalenceTest {

    private static final int MAX_DEPTH = 7;
    private static int failures = 0;

    public static void main(String[] args) {
        int startupMargin = TurquoiseBot.RFP_MARGIN;
        boolean startupEvasions = TurquoiseBot.QS_EVASIONS;
        boolean startupCheckExt = TurquoiseBot.CHECK_EXTENSION;
        System.out.println("RFP settings at startup: margin=" + TurquoiseBot.RFP_MARGIN + " maxDepth=" + TurquoiseBot.RFP_MAX_DEPTH);

        // ---- 1. RFP off: must equal KingSafetyBot exactly
        System.out.println();
        TurquoiseBot.RFP_MARGIN = 100000;
        TurquoiseBot.QS_EVASIONS = false;
        TurquoiseBot.CHECK_EXTENSION = false;
        try {
            int mismatches = 0;
            for (String fen : TestPositions.BENCH) {
                if (!sameTree(fen)) mismatches++;
            }
            check("RFP, QS evasions and check extension off: score, nodes and best move == KingSafetyBot (20 positions, depth 1-7)", mismatches == 0, mismatches + " positions differ");
        } catch (ReflectiveOperationException | RuntimeException e) {
            check("RFP, QS evasions and check extension off: score, nodes and best move == KingSafetyBot (20 positions, depth 1-7)", false, "reflection/search error: " + e);
        }

        // ---- 2. RFP on: node counts must differ somewhere
        System.out.println();
        TurquoiseBot.RFP_MARGIN = startupMargin;
        TurquoiseBot.QS_EVASIONS = false; // QS evasions and the check extension stay off: RFP alone must make the trees differ
        TurquoiseBot.CHECK_EXTENSION = false;
        try {
            int differing = 0;
            long totalT = 0, totalK = 0;
            for (String fen : TestPositions.BENCH) {
                Board a = new Board(), b = new Board();
                a.convertFENtoPosition(fen);
                b.convertFENtoPosition(fen);
                TurquoiseBot t = new TurquoiseBot(a);
                KingSafetyBot k = new KingSafetyBot(b);
                for (int d = 1; d <= MAX_DEPTH; d++) {
                    t.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
                    k.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
                }
                System.out.println("  nodes TurquoiseBot " + t.nodesSearched + " vs KingSafetyBot " + k.nodesSearched + "  " + fen);
                if (t.nodesSearched != k.nodesSearched) differing++;
                totalT += t.nodesSearched;
                totalK += k.nodesSearched;
            }
            System.out.println("  total nodes TurquoiseBot " + totalT + " vs KingSafetyBot " + totalK + " (" + differing + " of " + TestPositions.BENCH.length + " positions differ)");
            check("RFP on (QS evasions and check extension off): node count differs from KingSafetyBot on at least one position (RFP is live)", differing >= 1, "differing " + differing);
        } catch (RuntimeException e) {
            check("RFP on (QS evasions and check extension off): node count differs from KingSafetyBot on at least one position (RFP is live)", false, "search error: " + e);
        }

        // restore
        TurquoiseBot.QS_EVASIONS = startupEvasions;
        TurquoiseBot.CHECK_EXTENSION = startupCheckExt;

        System.out.println();
        System.out.println(failures == 0 ? "RFP EQUIVALENCE: ALL PASSED" : "RFP EQUIVALENCE: " + failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // searches one position to depth MAX_DEPTH with both engines, comparing score, cumulative
    // nodes and root best move after every depth. Prints the first mismatch and returns false.
    private static boolean sameTree(String fen) throws ReflectiveOperationException {
        Board a = new Board(), b = new Board();
        a.convertFENtoPosition(fen);
        b.convertFENtoPosition(fen);
        TurquoiseBot t = new TurquoiseBot(a);
        KingSafetyBot k = new KingSafetyBot(b);
        for (int d = 1; d <= MAX_DEPTH; d++) {
            int ts = t.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
            int ks = k.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
            long tn = t.nodesSearched;
            long kn = k.nodesSearched;
            int tm = rootBestMove(t, a);
            int km = rootBestMove(k, b);
            if (ts != ks || tn != kn || tm != km) {
                System.out.println("  differs at depth " + d + ": score " + ts + " vs " + ks
                        + ", nodes " + tn + " vs " + kn + ", best move " + tm + " vs " + km + ", FEN " + fen);
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
