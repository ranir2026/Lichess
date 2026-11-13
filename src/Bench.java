import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Deterministic fixed-depth bench.
 *
 * The "signature" of an engine is the total number of nodes it searches with fixed-depth iterative
 * deepening (depths 1..depth) over the 20 positions in TestPositions.BENCH, using one fresh engine per
 * position. It does not depend on timing or on the machine, so an unchanged signature means an unchanged
 * search tree. BenchTest checks the frozen engines against hard-coded signatures.
 *
 * Usage: java Bench [depth] [engine...]      defaults: DEFAULT_DEPTH and all of ENGINES
 *
 * Run it without -D engine properties: TurquoiseBot's -Drfp.*, -Dqs.evasions and -Dcheck.extension
 * change its signature.
 */
public class Bench {

    public static final int DEFAULT_DEPTH = 8;

    public static final String[] ENGINES = {"SEEBot", "KingSafetyBot", "ReverseFutilityBot", "QuiescenceEvasionBot", "CheckExtensionBot", "TurquoiseBot"};

    /** Total nodes searched by fixed-depth iterative deepening 1..depth over TestPositions.BENCH. */
    public static long signature(String engineClass, int depth) throws ReflectiveOperationException {
        long total = 0;
        for (String fen : TestPositions.BENCH) {
            Board board = new Board();
            board.convertFENtoPosition(fen);
            try {
                Engine engine = (Engine) Class.forName(engineClass).getConstructor(Board.class).newInstance(board);
                Method search = engine.getClass().getMethod("search", int.class, int.class, int.class, int.class);
                for (int d = 1; d <= depth; d++) {
                    search.invoke(engine, d, -Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
                }
                total += engine.nodesSearched; // cumulative over all depths
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw new ReflectiveOperationException(cause);
            }
        }
        return total;
    }

    public static void main(String[] args) {
        int depth = DEFAULT_DEPTH;
        int first = 0;
        if (args.length > 0 && args[0].matches("\\d+")) {
            depth = Integer.parseInt(args[0]);
            first = 1;
        }
        String[] engines = args.length > first ? Arrays.copyOfRange(args, first, args.length) : ENGINES;

        try {
            for (String name : engines) {
                long t0 = System.nanoTime();
                long nodes = signature(name, depth);
                long ms = (System.nanoTime() - t0) / 1000000L;
                long knps = nodes / Math.max(1, ms); // nodes per millisecond = thousands of nodes per second
                System.out.println("Bench " + name + " depth " + depth + ": " + nodes + " nodes  " + ms + " ms  " + knps + " knps");
            }
        } catch (Exception e) {
            System.err.println("Bench failed: " + e);
            System.exit(1);
        }
    }
}
