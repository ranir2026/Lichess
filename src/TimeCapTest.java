/**
 * Checks the time cap in TurquoiseBot (TurquoiseBot.TIME_CAP, TurquoiseBot.MOVE_OVERHEAD_MS).
 *
 * The planned think time is timeLeft / 40 + increment (with the opening cap). With the cap on it is also
 * capped at max(1, timeLeft - MOVE_OVERHEAD_MS), so a clock below the increment can't flag.
 *
 *  1. Formula grid: on the start position (opening) and the start position after 8 plies, with the cap on
 *     the result is <= max(1, timeLeft - overhead) and equals the v3.1 formula whenever that formula is
 *     within the cap. With the cap off the result equals the v3.1 formula everywhere.
 *  2. "SuiteRunner clocks unchanged": constant clocks with no increment give exactly the v3.1 formula.
 *  3. The reported bug on the real clock: TurquoiseBot (cap on) returns a legal move in under 1500 ms at
 *     1500 ms left with a 2000 ms increment; CheckExtensionBot takes more than 1500 ms.
 *
 * Usage: java TimeCapTest   (exits with code 1 on any failure)
 */
public class TimeCapTest {

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final int[] TIME_LEFT = {0, 1, 10, 49, 50, 51, 60, 100, 500, 860, 1000, 1500, 2000, 3500, 10000, 60000, 180000};
    private static final int[] INCREMENTS = {0, 100, 1000, 2000};
    private static final int[] SUITE_CLOCKS = {500, 860, 1000, 3500};
    private static int failures = 0;

    public static void main(String[] args) {
        boolean startupTimeCap = TurquoiseBot.TIME_CAP;
        int startupOverhead = TurquoiseBot.MOVE_OVERHEAD_MS;
        System.out.println("Time cap at startup: " + startupTimeCap + ", overhead " + startupOverhead + " ms");
        System.out.println();

        try {
            Board opening = startBoard(0);
            Board after8 = startBoard(8);
            if (opening.getMoveCount() >= 8) check("opening board has fewer than 8 plies", false, "moveCount=" + opening.getMoveCount());
            if (after8.getMoveCount() < 8) check("8-ply board has at least 8 plies", false, "moveCount=" + after8.getMoveCount());

            // ---- 1. formula grid
            System.out.println("1. Formula grid");
            TurquoiseBot.MOVE_OVERHEAD_MS = 50;
            TurquoiseBot.TIME_CAP = true;
            int capChanged = 0;
            int capOnBadBound = 0, capOnBadEqual = 0, capOffBad = 0, gridCases = 0;
            for (int b = 0; b < 2; b++) {
                Board board = (b == 0) ? opening : after8;
                TurquoiseBot bot = new TurquoiseBot(board);
                for (int t : TIME_LEFT) {
                    for (int inc : INCREMENTS) {
                        gridCases++;
                        long v31 = v31Formula(board, t, inc);
                        long capped = bot.computeTimeLimit(t, inc);
                        long bound = Math.max(1, t - TurquoiseBot.MOVE_OVERHEAD_MS);
                        if (capped != v31) capChanged++;
                        if (capped > bound) capOnBadBound++;
                        if (v31 <= t - TurquoiseBot.MOVE_OVERHEAD_MS && capped != v31) capOnBadEqual++;
                        TurquoiseBot.TIME_CAP = false;
                        long off = bot.computeTimeLimit(t, inc);
                        TurquoiseBot.TIME_CAP = true;
                        if (off != v31) capOffBad++;
                    }
                }
            }
            System.out.println("  cases " + gridCases + " (2 boards x " + TIME_LEFT.length + " clocks x " + INCREMENTS.length + " increments)");
            System.out.println("  cases changed by the cap: " + capChanged);
            check("(a) cap on: result <= max(1, timeLeft - overhead)", capOnBadBound == 0, capOnBadBound + " cases over the bound");
            check("(b) cap on: result == v3.1 whenever v3.1 <= timeLeft - overhead", capOnBadEqual == 0, capOnBadEqual + " cases differ");
            TurquoiseBot.TIME_CAP = false;
            check("(c) cap off: result == v3.1 in every case", capOffBad == 0, capOffBad + " cases differ");
            TurquoiseBot.TIME_CAP = true;

            // ---- 2. SuiteRunner clocks unchanged
            System.out.println();
            System.out.println("2. SuiteRunner clocks unchanged");
            int suiteBad = 0;
            for (Board board : new Board[]{opening, after8}) {
                TurquoiseBot bot = new TurquoiseBot(board);
                for (int t : SUITE_CLOCKS) {
                    if (bot.computeTimeLimit(t, 0) != v31Formula(board, t, 0)) suiteBad++;
                }
            }
            check("SuiteRunner clocks unchanged", suiteBad == 0 && TurquoiseBot.TIME_CAP, suiteBad + " clocks differ");

            // ---- 3. the reported bug on the real clock
            System.out.println();
            System.out.println("3. Reported bug, real clock (1500 ms left, +2000 ms increment, 8 plies in)");
            Board tBoard = startBoard(8);
            TurquoiseBot.TIME_CAP = true;
            TurquoiseBot tb = new TurquoiseBot(tBoard);
            long t0 = System.nanoTime();
            int tMove = tb.getBestMove(1500, 2000);
            long tMs = (System.nanoTime() - t0) / 1000000L;
            boolean tLegal = isLegal(tBoard, tMove);
            System.out.println("  TurquoiseBot (cap on): " + tMs + " ms, move " + tMove + (tLegal ? " (legal)" : " (NOT legal)"));
            check("TurquoiseBot returns a legal move in under 1500 ms", tLegal && tMs < 1500, "legal=" + tLegal + ", " + tMs + " ms");

            Board cBoard = startBoard(8);
            CheckExtensionBot cb = new CheckExtensionBot(cBoard);
            long c0 = System.nanoTime();
            cb.getBestMove(1500, 2000);
            long cMs = (System.nanoTime() - c0) / 1000000L;
            System.out.println("  CheckExtensionBot v3.1: " + cMs + " ms");
            check("CheckExtensionBot takes more than 1500 ms", cMs > 1500, cMs + " ms");
        } catch (RuntimeException e) {
            check("no exception", false, e.toString());
        } finally {
            TurquoiseBot.TIME_CAP = startupTimeCap;
            TurquoiseBot.MOVE_OVERHEAD_MS = startupOverhead;
        }

        System.out.println();
        if (failures > 0) {
            System.out.println("TIME CAP TEST: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("TIME CAP TEST: ALL PASSED");
    }

    // v3.1 time formula, copied from CheckExtensionBot.getBestMove (CheckExtensionBot.java lines 1071-1077)
    private static long v31Formula(Board board, int totalTimeLeft, int increment) {
        long timeLimit = Math.max(1, (totalTimeLeft / 40) + increment);
        if (board.getMoveCount() < 8) {
            timeLimit = Math.min(timeLimit, 1000 + increment);
        }
        return timeLimit;
    }

    // start position with the given number of plies made (first legal move each time)
    private static Board startBoard(int plies) {
        Board board = new Board();
        board.convertFENtoPosition(START);
        for (int i = 0; i < plies; i++) {
            int[] moves = new int[MoveGenerator.MAX_MOVES];
            int n = MoveGenerator.generateLegalMoves(board, moves);
            if (n == 0) throw new IllegalStateException("no legal move at ply " + i);
            board.makeMove(moves[0]);
        }
        return board;
    }

    private static boolean isLegal(Board board, int move) {
        int[] moves = new int[MoveGenerator.MAX_MOVES];
        int n = MoveGenerator.generateLegalMoves(board, moves);
        for (int i = 0; i < n; i++) {
            if (moves[i] == move) return true;
        }
        return false;
    }

    private static void check(String name, boolean ok, String detail) {
        if (!ok) failures++;
        System.out.println((ok ? "  ok    " : "  FAIL  ") + name + (ok || detail.isEmpty() ? "" : "  (" + detail + ")"));
    }
}
