/**
 * Fails if any frozen engine's bench signature (see Bench) changes at Bench.DEFAULT_DEPTH.
 *
 * Frozen engines must never change, but they share Board, MoveGenerator, Zobrist and
 * TranspositionTableEntry with the dev engine, so a change to shared code that alters a baseline shows up
 * here. If such a change is deliberate (e.g. a move-generator fix), update the constant below and say why
 * in the commit message.
 *
 * TurquoiseBot (the dev engine) has no constant. Its signature is computed twice in the same JVM and must
 * match, which checks that the search is deterministic.
 *
 * Usage: java BenchTest      exit 0 = all passed, exit 1 = at least one failure
 */
public class BenchTest {

    private static final String[] FROZEN   = {"SEEBot", "KingSafetyBot", "ReverseFutilityBot", "QuiescenceEvasionBot", "CheckExtensionBot"};
    private static final long[]   EXPECTED = {2497203L, 2574237L, 1748742L, 1871906L, 2246792L}; // at Bench.DEFAULT_DEPTH (8)

    private static int failures = 0;

    public static void main(String[] args) {
        int depth = Bench.DEFAULT_DEPTH;

        for (int i = 0; i < FROZEN.length; i++) {
            String name = FROZEN[i];
            try {
                long got = Bench.signature(name, depth);
                if (got == EXPECTED[i]) {
                    System.out.println("PASS " + name + ": " + got);
                } else {
                    fail(name + ": expected " + EXPECTED[i] + ", got " + got);
                }
            } catch (Exception e) {
                fail(name + ": error: " + e);
            }
        }

        try {
            long first  = Bench.signature("TurquoiseBot", depth);
            long second = Bench.signature("TurquoiseBot", depth);
            System.out.println("TurquoiseBot signature (dev engine, not checked against a constant): " + first);
            if (first != second) {
                fail("TurquoiseBot not deterministic: first run " + first + ", second run " + second);
            }
        } catch (Exception e) {
            fail("TurquoiseBot: error: " + e);
        }

        if (failures == 0) {
            System.out.println("BENCH TEST: ALL PASSED");
        } else {
            System.out.println("BENCH TEST: " + failures + " FAILED");
            System.exit(1);
        }
    }

    private static void fail(String message) {
        System.out.println("FAIL " + message);
        failures++;
    }
}
