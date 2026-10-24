import java.util.*;

/**
 * Colour-symmetry test for evaluate().
 *
 * For every position P, eval(P) must equal eval(flip(P)), where flip mirrors the board
 * top-to-bottom and swaps piece colours and the side to move (TestPositions.flipFEN). evaluate()
 * scores from the side to move's point of view, so both calls should see the exact same
 * situation. Any difference means some term treats white and black differently -- the classic
 * king-safety bug (a zone, shield or storm mask built for one colour and reused for the other).
 *
 * Usage: java EvalSymmetryTest        (exits with code 1 on any asymmetry)
 */
public class EvalSymmetryTest {

    interface Evaluator { int eval(); }
    interface EvaluatorFactory { Evaluator create(Board b); }

    public static void main(String[] args) {
        List<String> fens = TestPositions.symmetrySet();
        System.out.println("Positions: " + fens.size() + " (each also evaluated colour-flipped)");

        int failures = checkFlipHelper(fens);
        failures += check("SEEBot", fens, new EvaluatorFactory() {
            public Evaluator create(Board b) { final SEEBot bot = new SEEBot(b); return new Evaluator() { public int eval() { return bot.evaluate(); } }; }
        });
        failures += check("KingSafetyBot", fens, new EvaluatorFactory() {
            public Evaluator create(Board b) { final KingSafetyBot bot = new KingSafetyBot(b); return new Evaluator() { public int eval() { return bot.evaluate(); } }; }
        });

        System.out.println();
        System.out.println(failures == 0 ? "SYMMETRY: ALL PASSED" : "SYMMETRY: FAILED (" + failures + " failures)");
        System.exit(failures == 0 ? 0 : 1);
    }

    // sanity-check the test itself: flipping twice must give back the original FEN, and the board
    // must load the flipped FEN faithfully with every piece's colour swapped
    private static int checkFlipHelper(List<String> fens) {
        int failures = 0;
        Board a = new Board(), b = new Board();
        for (String fen : fens) {
            a.convertFENtoPosition(fen);
            String canonical = TestPositions.toFEN(a);
            String flipped = TestPositions.flipFEN(canonical);
            b.convertFENtoPosition(flipped);

            boolean ok = TestPositions.flipFEN(flipped).equals(canonical) && TestPositions.toFEN(b).equals(flipped);
            for (int sq = 0; sq < 64; sq++) {
                int piece = a.getPieceAt(sq);
                int mirrored = b.getPieceAt(sq ^ 56);
                ok &= (piece == -1) ? mirrored == -1 : mirrored == (piece + 6) % 12;
            }
            if (!ok) {
                failures++;
                if (failures <= 5) System.out.println("  flipFEN helper broken on: " + fen + "  ->  " + flipped);
            }
        }
        System.out.println("flipFEN helper check: " + (failures == 0 ? "ok" : failures + " FAILURES"));
        return failures;
    }

    static int check(String name, List<String> fens, EvaluatorFactory factory) {
        Board board = new Board();
        Evaluator evaluator = factory.create(board);
        int failures = 0;
        long sumAbs = 0;
        for (String fen : fens) {
            board.convertFENtoPosition(fen);
            int original = evaluator.eval();
            board.convertFENtoPosition(TestPositions.flipFEN(fen));
            int flipped = evaluator.eval();
            sumAbs += Math.abs(original);
            if (original != flipped) {
                failures++;
                if (failures <= 10) System.out.printf("  %s ASYMMETRIC: %d vs %d (diff %d) on %s%n",
                        name, original, flipped, original - flipped, fen);
            }
        }
        System.out.printf("%-14s %4d positions, %d asymmetric, mean |eval| %.0f cp -> %s%n",
                name, fens.size(), failures, (double) sumAbs / fens.size(), failures == 0 ? "ok" : "FAIL");
        return failures;
    }
}
