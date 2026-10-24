import java.util.*;

/**
 * Speed benchmark: nodes/sec and depth reached at a fixed think time on the 20 fixed positions in
 * TestPositions.BENCH, plus raw evaluate() throughput on the symmetry-test position set.
 *
 * Engines run interleaved (position by position, engine order rotated each time) so CPU frequency
 * drift and background load hit every engine about equally -- this matters on a laptop.
 *
 * Usage: java SpeedBench [moveMs] [reps] [engine...]      defaults: 1000 3 SEEBot
 *
 * getBestMove(totalTimeLeft, inc) thinks for totalTimeLeft/40 + inc, capped at 1000 ms while
 * fewer than 8 plies have been played (always true right after a FEN load), so moveMs is requested
 * as getBestMove(moveMs * 40, 0) and must be <= 1000.
 */
public class SpeedBench {

    static Engine makeEngine(String name, Board b) {
        if (name.equals("SEEBot")) return new SEEBot(b);
        if (name.equals("TurquoiseBot")) return new TurquoiseBot(b);
        if (name.equals("KingSafetyBot")) return new KingSafetyBot(b);
        throw new IllegalArgumentException("unknown engine: " + name);
    }

    static int evaluate(Engine e) {
        if (e instanceof SEEBot) return ((SEEBot) e).evaluate();
        if (e instanceof TurquoiseBot) return ((TurquoiseBot) e).evaluate();
        if (e instanceof KingSafetyBot) return ((KingSafetyBot) e).evaluate();
        throw new IllegalArgumentException("no evaluate() for " + e.getName());
    }

    public static void main(String[] args) {
        int moveMs = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int reps = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        String[] engines = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[] {"SEEBot"};
        if (moveMs > 1000) throw new IllegalArgumentException("moveMs must be <= 1000 (opening time cap)");

        String[] fens = TestPositions.BENCH;
        int E = engines.length, F = fens.length;
        System.out.printf("SpeedBench: %d positions x %d reps at %d ms/move, engines %s%n%n", F, reps, moveMs, Arrays.toString(engines));

        // JIT warm-up so the first measured positions aren't run in the interpreter
        for (String name : engines) {
            for (int f = 0; f < 6; f++) runSearch(name, fens[f], 300);
        }

        long[][] nodes = new long[E][F];
        long[][] ms = new long[E][F];
        long[][] depth = new long[E][F];
        long[][] repNodes = new long[E][reps];
        long[][] repMs = new long[E][reps];

        for (int r = 0; r < reps; r++) {
            for (int f = 0; f < F; f++) {
                for (int k = 0; k < E; k++) {
                    int e = (k + r + f) % E;
                    long[] res = runSearch(engines[e], fens[f], moveMs);
                    nodes[e][f] += res[0]; ms[e][f] += res[1]; depth[e][f] += res[2];
                    repNodes[e][r] += res[0]; repMs[e][r] += res[1];
                }
            }
        }

        System.out.printf("%-4s", "pos");
        for (String name : engines) System.out.printf(" | %-14s %7s %9s", name, "depth", "kNPS");
        System.out.println();
        for (int f = 0; f < F; f++) {
            System.out.printf("%-4d", f + 1);
            for (int e = 0; e < E; e++) {
                System.out.printf(" | %-14s %7.2f %9.0f", "", (double) depth[e][f] / reps, nodes[e][f] / (double) Math.max(1, ms[e][f]));
            }
            System.out.println();
        }

        System.out.println();
        for (int e = 0; e < E; e++) {
            long totalNodes = 0, totalMs = 0, totalDepth = 0;
            for (int f = 0; f < F; f++) { totalNodes += nodes[e][f]; totalMs += ms[e][f]; totalDepth += depth[e][f]; }
            StringBuilder perRep = new StringBuilder();
            for (int r = 0; r < reps; r++) perRep.append(String.format("%s%.0f", r == 0 ? "" : ", ", repNodes[e][r] / (double) Math.max(1, repMs[e][r])));
            System.out.printf("%-14s NPS %,10.0f   avg depth %5.2f   (per-rep kNPS: %s)%n", engines[e],
                    totalNodes * 1000.0 / Math.max(1, totalMs), (double) totalDepth / (F * reps), perRep);
        }

        evalThroughput(engines);
    }

    // returns {nodes, elapsedMs, depthReached}; a fresh board + engine per search keeps runs independent
    private static long[] runSearch(String name, String fen, int moveMs) {
        Board b = new Board();
        b.convertFENtoPosition(fen);
        Engine engine = makeEngine(name, b);
        engine.getBestMove(moveMs * 40, 0);
        return new long[] {engine.lastNodesSearched, engine.lastElapsedMs, engine.lastDepthReached};
    }

    private static void evalThroughput(String[] engines) {
        List<String> fens = TestPositions.symmetrySet();
        List<long[]> snaps = new ArrayList<long[]>();
        Board loader = new Board();
        for (String fen : fens) { loader.convertFENtoPosition(fen); snaps.add(TestPositions.snapshot(loader)); }

        int E = engines.length, rounds = 7, iters = 3000;
        Board[] boards = new Board[E];
        Engine[] bots = new Engine[E];
        for (int e = 0; e < E; e++) { boards[e] = new Board(); bots[e] = makeEngine(engines[e], boards[e]); }

        double[][] nsPerEval = new double[E][rounds];
        long sink = 0;
        for (int round = -2; round < rounds; round++) { // two unrecorded warm-up rounds
            for (int k = 0; k < E; k++) {
                int e = (k + Math.max(0, round)) % E;
                long t0 = System.nanoTime();
                long count = 0;
                for (int it = 0; it < iters; it++) {
                    for (long[] s : snaps) { TestPositions.load(boards[e], s); sink += evaluate(bots[e]); count++; }
                }
                if (round >= 0) nsPerEval[e][round] = (System.nanoTime() - t0) / (double) count;
            }
        }

        System.out.println();
        System.out.println("evaluate() throughput over " + snaps.size() + " positions (median of " + rounds + " rounds, includes ~10 ns snapshot load):");
        for (int e = 0; e < E; e++) {
            double[] sorted = nsPerEval[e].clone();
            Arrays.sort(sorted);
            System.out.printf("%-14s %7.1f ns/eval  (min %.1f, max %.1f)%n", engines[e], sorted[rounds / 2], sorted[0], sorted[rounds - 1]);
        }
        if (sink == 42) System.out.println(); // keeps the JIT from discarding the eval loop
    }
}
