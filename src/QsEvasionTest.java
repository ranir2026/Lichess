import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Checks the in-check quiescence change in TurquoiseBot (TurquoiseBot.QS_EVASIONS).
 *
 *  1. QS evasions off (TurquoiseBot.QS_EVASIONS = false): TurquoiseBot must search exactly like
 *     ReverseFutilityBot v2.9 on every TestPositions.BENCH position at depths 1..7 -- same root score,
 *     same cumulative node count and same root best move -- proof the switch restores the old search.
 *  2. QS evasions on: the node counts must differ from ReverseFutilityBot on at least one position,
 *     showing the new behaviour is actually live.
 *  3. Checkmate in quiescence (fool's mate, white to move and mated): on scores it as mate, off
 *     stands pat.
 *  4. Quiet evasion into a lost queen (black knight f3 forks Qd2 and checks Kg1): on sees the
 *     queen loss, off stands pat and thinks white is ahead.
 *
 * Usage: java QsEvasionTest   (exits with code 1 on any failure)
 */
public class QsEvasionTest {

    private static final int MAX_DEPTH = 7;
    private static final String FOOLS_MATE = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3";
    private static final String LOST_QUEEN = "6k1/ppp2ppp/8/8/8/5n2/PPPQ1P1P/6K1 w - - 0 1";
    private static int failures = 0;

    public static void main(String[] args) {
        boolean startupEvasions = TurquoiseBot.QS_EVASIONS;
        boolean startupCheckExt = TurquoiseBot.CHECK_EXTENSION;
        System.out.println("QS settings at startup: evasions=" + TurquoiseBot.QS_EVASIONS + ", check extension=" + TurquoiseBot.CHECK_EXTENSION);

        // ReverseFutilityBot's frozen RFP values, so the comparison with it is like for like
        TurquoiseBot.RFP_MARGIN = 90;
        TurquoiseBot.RFP_MAX_DEPTH = 6;
        // and no check extension, so the QS switch is the only difference from ReverseFutilityBot
        TurquoiseBot.CHECK_EXTENSION = false;

        // ---- 1. QS evasions off: must equal ReverseFutilityBot v2.9 exactly
        System.out.println();
        TurquoiseBot.QS_EVASIONS = false;
        final String offName = "QS evasions off: score, nodes and best move == ReverseFutilityBot (20 positions, depth 1-7)";
        try {
            int mismatches = 0;
            for (String fen : TestPositions.BENCH) {
                if (!sameTree(fen)) mismatches++;
            }
            check(offName, mismatches == 0, mismatches + " positions differ");
        } catch (ReflectiveOperationException | RuntimeException e) {
            check(offName, false, "reflection/search error: " + e);
        }

        // ---- 2. QS evasions on: node counts must differ somewhere
        System.out.println();
        TurquoiseBot.QS_EVASIONS = true;
        final String onName = "QS evasions on: node count differs from ReverseFutilityBot on at least one position";
        try {
            int differing = 0;
            long totalT = 0, totalR = 0;
            for (String fen : TestPositions.BENCH) {
                Board a = new Board(), b = new Board();
                a.convertFENtoPosition(fen);
                b.convertFENtoPosition(fen);
                TurquoiseBot t = new TurquoiseBot(a);
                ReverseFutilityBot r = new ReverseFutilityBot(b);
                for (int d = 1; d <= MAX_DEPTH; d++) {
                    t.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
                    r.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
                }
                System.out.println("  nodes TurquoiseBot " + t.nodesSearched + " vs ReverseFutilityBot " + r.nodesSearched + "  " + fen);
                if (t.nodesSearched != r.nodesSearched) differing++;
                totalT += t.nodesSearched;
                totalR += r.nodesSearched;
            }
            System.out.println("  total nodes TurquoiseBot " + totalT + " vs ReverseFutilityBot " + totalR + " (" + differing + " of " + TestPositions.BENCH.length + " positions differ)");
            check(onName, differing >= 1, "differing " + differing);
        } catch (RuntimeException e) {
            check(onName, false, "search error: " + e);
        }

        // ---- 3. mate at the horizon: fool's mate, white to move is checkmated
        System.out.println();
        final String mateOnName = "QS evasions on: checkmate in quiescence scores as mate (<= -50000)";
        final String mateOffName = "QS evasions off: old behaviour stands pat (> -50000)";
        try {
            TurquoiseBot.QS_EVASIONS = true;
            int mateOn = quiescenceOf(FOOLS_MATE);
            TurquoiseBot.QS_EVASIONS = false;
            int mateOff = quiescenceOf(FOOLS_MATE);
            System.out.println("  fool's mate quiescence: on=" + mateOn + " off=" + mateOff);
            check(mateOnName, mateOn <= -50000, "on=" + mateOn);
            check(mateOffName, mateOff > -50000, "off=" + mateOff);
        } catch (RuntimeException e) {
            check(mateOnName, false, "search error: " + e);
            check(mateOffName, false, "search error: " + e);
        }

        // ---- 4. quiet evasion into a lost queen: knight f3 checks Kg1 and forks Qd2
        System.out.println();
        final String queenOnName = "QS evasions on: sees the queen loss (on-value < 0)";
        final String queenOffName = "QS evasions off: stand pat thinks white is ahead (> 0)";
        try {
            TurquoiseBot.QS_EVASIONS = true;
            int queenOn = quiescenceOf(LOST_QUEEN);
            TurquoiseBot.QS_EVASIONS = false;
            int queenOff = quiescenceOf(LOST_QUEEN);
            System.out.println("  lost queen quiescence: on=" + queenOn + " off=" + queenOff);
            check(queenOffName, queenOff > 0, "off=" + queenOff);
            check(queenOnName, queenOn < 0, "on=" + queenOn);
        } catch (RuntimeException e) {
            check(queenOffName, false, "search error: " + e);
            check(queenOnName, false, "search error: " + e);
        }

        // ---- restore
        TurquoiseBot.QS_EVASIONS = startupEvasions;
        TurquoiseBot.CHECK_EXTENSION = startupCheckExt;

        System.out.println();
        System.out.println(failures == 0 ? "QS EVASION TEST: ALL PASSED" : "QS EVASION TEST: " + failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // one quiescenceSearch call at the root on a fresh board and bot
    private static int quiescenceOf(String fen) {
        Board b = new Board();
        b.convertFENtoPosition(fen);
        TurquoiseBot t = new TurquoiseBot(b);
        return t.quiescenceSearch(-Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
    }

    // searches one position to MAX_DEPTH with both engines, comparing score, cumulative nodes and
    // root best move after every depth. Prints the first mismatch and returns false.
    private static boolean sameTree(String fen) throws ReflectiveOperationException {
        Board a = new Board(), b = new Board();
        a.convertFENtoPosition(fen);
        b.convertFENtoPosition(fen);
        TurquoiseBot t = new TurquoiseBot(a);
        ReverseFutilityBot r = new ReverseFutilityBot(b);
        for (int d = 1; d <= MAX_DEPTH; d++) {
            int ts = t.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
            int rs = r.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
            long tn = t.nodesSearched;
            long rn = r.nodesSearched;
            int tm = rootBestMove(t, a);
            int rm = rootBestMove(r, b);
            if (ts != rs || tn != rn || tm != rm) {
                System.out.println("  differs at depth " + d + ": score " + ts + " vs " + rs
                        + ", nodes " + tn + " vs " + rn + ", best move " + tm + " vs " + rm + ", FEN " + fen);
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
