import java.util.*;

/**
 * Unit tests for KingSafetyBot's king-safety term.
 *
 *  1. Equivalence: with every king-safety constant set to 0, KingSafetyBot must evaluate every
 *     test position exactly like SEEBot and search the exact same tree (same scores and node
 *     counts at fixed depth) -- proof that nothing but king safety changed.
 *  2. Orderings on hand-built position pairs that differ only in one king-safety feature
 *     (TestPositions.KS_*): the king-safety penalty must move the expected way by >= 5 cp (>= 0
 *     for the storm, whose tuned penalties are 0), and the full eval must order the pair the
 *     expected way.
 *  3. King safety is exactly 0 in pure king-and-pawn endgames, and 0 for an uncastled king in
 *     the start position and after 1.e4 e5 (the shield must not punish central pawn moves).
 *
 * Usage: java KingSafetyTest        (exits with code 1 on any failure)
 */
public class KingSafetyTest {

    private static final int MIN_EFFECT = 5; // cp -- "clearly matters", not rounding noise
    private static int failures = 0;

    private static final Board ksBoard = new Board();
    private static final KingSafetyBot ks = new KingSafetyBot(ksBoard);
    private static final Board seeBoard = new Board();
    private static final SEEBot see = new SEEBot(seeBoard);

    public static void main(String[] args) {
        int[] params = ks.getParams();
        System.out.println("King-safety constants under test: " + Arrays.toString(Arrays.copyOfRange(params, 480, KingSafetyBot.NUM_PARAMS)));

        // ---- 1. equivalence with king safety switched off
        int[] disabled = params.clone();
        for (int i = 480; i < KingSafetyBot.NUM_PARAMS; i++) disabled[i] = 0;
        ks.applyParams(disabled);
        List<String> fens = TestPositions.symmetrySet();
        int evalMismatches = 0;
        for (String fen : fens) {
            int[] e = evaluate(fen);
            if (e[0] != e[1]) {
                evalMismatches++;
                if (evalMismatches <= 5) System.out.println("  eval differs from SEEBot with KS off: " + e[0] + " vs " + e[1] + " on " + fen);
            }
        }
        check("KS off: eval == SEEBot on all " + fens.size() + " positions", evalMismatches == 0, evalMismatches + " mismatches");
        check("KS off: fixed-depth search tree == SEEBot (20 positions, depth 1-6)", sameSearchTrees(6), "");
        ks.applyParams(params);
        check("getParams/applyParams round trip", Arrays.equals(params, ks.getParams()), "");

        // ---- 2. orderings: {safe position, unsafe position, color whose king is exposed}
        System.out.println();
        System.out.printf("%-34s %8s %8s %10s %10s %10s%n", "pair (safe vs unsafe)", "KS safe", "KS unsf", "eval safe", "eval unsf", "KS effect");
        pair("intact shield vs g/h pushed",   TestPositions.KS_SHIELD_INTACT, TestPositions.KS_SHIELD_PUSHED, Board.WHITE, MIN_EFFECT, true);
        pair("Q+N away vs bearing on king",   TestPositions.KS_QN_AWAY,       TestPositions.KS_QN_ATTACK,     Board.BLACK, MIN_EFFECT, true);
        pair("closed vs open h-file",         TestPositions.KS_H_FILE_CLOSED, TestPositions.KS_H_FILE_OPEN,   Board.WHITE, MIN_EFFECT, true);
        // the storm penalties tuned to 0, so a storm only has to never make the king look SAFER
        pair("no storm vs g/h pawns storming", TestPositions.KS_NO_STORM,     TestPositions.KS_STORM,         Board.WHITE, 0, true);
        // the queen pair differs by a whole queen, so only the king-safety term is compared
        pair("N+B+R attack: no queen vs queen", TestPositions.KS_ATTACK_NOQUEEN, TestPositions.KS_ATTACK_QUEEN, Board.BLACK, MIN_EFFECT, false);

        // ---- 3. no king safety where it shouldn't apply
        System.out.println();
        zero("pure K+P endgame",                    TestPositions.KS_KP_ENDGAME, true);
        zero("K+P endgame, pawns 'storming' king",  TestPositions.KS_KP_STORMED, true);
        zero("start position",                      TestPositions.BENCH[0], false);
        zero("uncastled kings after 1.e4 e5",       TestPositions.KS_AFTER_E4_E5, false);

        System.out.println();
        System.out.println(failures == 0 ? "KING SAFETY TESTS: ALL PASSED" : "KING SAFETY TESTS: " + failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // {KingSafetyBot eval, SEEBot eval, white KS penalty, black KS penalty} -- evals are side to
    // move relative, and every KS_* test position has white to move
    private static int[] evaluate(String fen) {
        ksBoard.convertFENtoPosition(fen);
        seeBoard.convertFENtoPosition(fen);
        int ksEval = ks.evaluate();
        return new int[] {ksEval, see.evaluate(), ks.lastKingSafetyWhite, ks.lastKingSafetyBlack};
    }

    private static void pair(String name, String safeFen, String unsafeFen, int victim, int minEffect, boolean checkFullEval) {
        int[] safe = evaluate(safeFen);
        int[] unsafe = evaluate(unsafeFen);
        int ksIndex = (victim == Board.WHITE) ? 2 : 3;
        int penaltyIncrease = unsafe[ksIndex] - safe[ksIndex];

        // white-relative change in eval from safe -> unsafe that is due to king safety alone:
        // KingSafetyBot's change minus SEEBot's change. Negative = worse for white.
        int ksEffect = (unsafe[0] - safe[0]) - (unsafe[1] - safe[1]);
        int expectedSign = (victim == Board.WHITE) ? -1 : 1;

        System.out.printf("%-34s %8d %8d %10d %10d %+10d%n", name, safe[ksIndex], unsafe[ksIndex], safe[0], unsafe[0], ksEffect);
        check(name + ": exposed king's KS penalty rises by >= " + minEffect, penaltyIncrease >= minEffect, "rise " + penaltyIncrease);
        check(name + ": KS moves the eval the right way by >= " + minEffect, ksEffect * expectedSign >= minEffect, "effect " + ksEffect);
        if (checkFullEval) {
            boolean ordered = (victim == Board.WHITE) ? safe[0] > unsafe[0] : unsafe[0] > safe[0];
            check(name + ": full eval prefers the safer king", ordered, "safe " + safe[0] + ", unsafe " + unsafe[0]);
        }
    }

    private static void zero(String name, String fen, boolean mustMatchSEEBot) {
        int[] e = evaluate(fen);
        System.out.printf("%-40s KS white %d, KS black %d, eval %d (SEEBot %d)%n", name, e[2], e[3], e[0], e[1]);
        check(name + ": king safety is exactly 0", e[2] == 0 && e[3] == 0, "white " + e[2] + ", black " + e[3]);
        if (mustMatchSEEBot) check(name + ": eval identical to SEEBot", e[0] == e[1], e[0] + " vs " + e[1]);
    }

    // searches each bench position to depths 1..maxDepth with both engines (same TT/killer
    // history evolution), comparing the root score and cumulative node count at every depth
    private static boolean sameSearchTrees(int maxDepth) {
        boolean same = true;
        for (String fen : TestPositions.BENCH) {
            Board a = new Board(), b = new Board();
            a.convertFENtoPosition(fen);
            b.convertFENtoPosition(fen);
            KingSafetyBot ksBot = new KingSafetyBot(a);
            SEEBot seeBot = new SEEBot(b);
            for (int d = 1; d <= maxDepth; d++) {
                int s1 = ksBot.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
                int s2 = seeBot.search(d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
                if (s1 != s2 || ksBot.nodesSearched != seeBot.nodesSearched) {
                    System.out.println("  search differs at depth " + d + " (score " + s1 + " vs " + s2 + ", nodes "
                            + ksBot.nodesSearched + " vs " + seeBot.nodesSearched + ") on " + fen);
                    same = false;
                    break;
                }
            }
        }
        return same;
    }

    private static void check(String name, boolean ok, String detail) {
        if (!ok) failures++;
        System.out.println((ok ? "  ok    " : "  FAIL  ") + name + (ok || detail.isEmpty() ? "" : "  (" + detail + ")"));
    }
}
