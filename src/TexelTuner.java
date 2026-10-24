import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Texel Tuner for KingSafetyBot  (Java 8 compatible)
 *
 * HOW IT WORKS
 * ============
 * Texel tuning minimizes the mean squared error (MSE) between:
 *   sigmoid(eval(pos) / K)  and  gameResult (1.0 = white win, 0.5 = draw, 0.0 = black win)
 *
 * The optimizer used is coordinate descent: it nudges each parameter up and down by a step,
 * keeps the change if MSE improves, and repeats until a full pass over all params changes nothing.
 *
 * The dataset is split (seeded shuffle) into a training set the optimizer sees and a held-out
 * validation set it never sees. Only the validation MSE says whether a change is real or just
 * fitting noise in the training positions, so every run reports both.
 *
 * DATASET
 * =======
 * One "FEN result" per line, e.g.:
 *   rnbqkb1r/pppp1ppp/5n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 0 1 0.5
 * Positions must be quiet (no captures pending). Result: 1.0=white win, 0.5=draw, 0.0=black win.
 * FENs are parsed once into compact bitboard snapshots, not re-parsed on every MSE pass.
 *
 * USAGE
 * =====
 *   java TexelTuner [options]
 *     --data <path>        dataset (default positions.txt)
 *     --find-k             fit K on the training set for the starting params, then exit
 *     --k <value>          sigmoid scaling constant (default K below)
 *     --ks-only            tune only the king-safety params (480-494), everything else held fixed
 *     --ks-off             zero the king-safety params before starting (= SEEBot's evaluation)
 *     --ks-bounds          keep king-safety params >= 0 and KS_NO_QUEEN_SCALE <= 16 (design meaning)
 *     --bound <i>:<lo>:<hi> keep param i within [lo, hi] (repeatable)
 *     --params-in <file>   start from this param vector (one int per line) instead of the engine's
 *     --params-out <file>  write the tuned vector there (one int per line)
 *     --passes <n>         max coordinate-descent passes (default 50)
 *     --split <f>          training fraction (default 0.8);  --seed <n> split seed (default 20261022)
 *     --threads <n>        worker threads (default: all cores)
 *     --report             only print train/validation MSE for the starting params, then exit
 *
 * The starting params come from KingSafetyBot.getParams() -- the engine's actual constants --
 * so "holding a parameter fixed" really holds it at the value the engine plays with.
 *
 * PARAMETER LAYOUT (matches KingSafetyBot.applyParams / getParams)
 * ===============================================================
 * Index  0     : PAWN
 * Index  1     : KNIGHT
 * Index  2     : BISHOP
 * Index  3     : ROOK
 * Index  4     : QUEEN
 * Index  5- 6  : KNIGHT_MOB_MG, KNIGHT_MOB_EG
 * Index  7- 8  : BISHOP_MOB_MG, BISHOP_MOB_EG
 * Index  9-10  : ROOK_MOB_MG,   ROOK_MOB_EG
 * Index 11-12  : QUEEN_MOB_MG,  QUEEN_MOB_EG
 * Index 13-20  : PASSED_PAWN_MG[0..7]
 * Index 21-28  : PASSED_PAWN_EG[0..7]
 * Index 29     : KING_PROXIMITY_WEIGHT
 * Index 30-31  : ISOLATED_PAWN_PENALTY_MG, ISOLATED_PAWN_PENALTY_EG
 * Index 32- 95 : PAWN_PST[0..63]
 * Index 96-159 : KNIGHT_PST[0..63]
 * Index160-223 : BISHOP_PST[0..63]
 * Index224-287 : ROOK_PST[0..63]
 * Index288-351 : QUEEN_PST[0..63]
 * Index352-415 : KING_PST[0..63]
 * Index416-479 : KING_ENDGAME_PST[0..63]
 * Index480-481 : KS_SHIELD_MISSING, KS_SHIELD_ADVANCED
 * Index482-484 : KS_STORM_RANK3, KS_STORM_RANK4, KS_STORM_RANK5
 * Index485-486 : KS_FILE_NO_OWN_PAWN, KS_FILE_NO_ENEMY_PAWN
 * Index487-490 : KS_ATTACK_WEIGHT_N, _B, _R, _Q
 * Index491-494 : KS_MIN_ATTACKERS, KS_DANGER_QUAD, KS_DANGER_MAX, KS_NO_QUEEN_SCALE
 * Total: 495 parameters
 */
public class TexelTuner {

    // -------------------------------------------------------------------------
    // Tuning hyperparameters
    // -------------------------------------------------------------------------
    static double K         = 1.4500000000000008; // sigmoid scaling constant — tune with --find-k
    static final int  STEP       = 1;    // coordinate-descent step size (centipawns)
    static int    maxPasses = 50;
    static String datasetPath = "positions.txt";

    static final int KS_FIRST = 480;     // first king-safety index
    static final int SNAP     = 13;      // longs per stored position: 12 piece bitboards + side to move
    static final int CHUNKS   = 64;      // fixed work split -> the MSE sum is deterministic for any thread count

    // applyParams()/getParams() write and read KingSafetyBot's static constants, so one instance
    // is enough to set the values every worker's bot evaluates with
    static final KingSafetyBot PARAM_BOT = new KingSafetyBot(new Board());
    static int[] params = PARAM_BOT.getParams();

    // dataset
    static long[]   positions;  // SNAP longs per position
    static double[] results;
    static int[]    trainIdx, validIdx;

    static ExecutorService pool;

    // every pool thread gets its own board + bot (the eval constants are static, so they're shared)
    static final ThreadLocal<KingSafetyBot> WORKER_BOT = new ThreadLocal<KingSafetyBot>();
    static final ThreadLocal<Board>         WORKER_BOARD = new ThreadLocal<Board>();

    // -------------------------------------------------------------------------
    // Sigmoid
    // -------------------------------------------------------------------------
    static double sigmoid(double score, double k) {
        return 1.0 / (1.0 + Math.pow(10.0, -k * score / 400.0));
    }

    // -------------------------------------------------------------------------
    // Evaluation (White's perspective) of stored position i
    // -------------------------------------------------------------------------
    static int evaluateStored(int i) {
        Board b = WORKER_BOARD.get();
        KingSafetyBot bot = WORKER_BOT.get();
        if (b == null) {
            b = new Board();
            bot = new KingSafetyBot(b);
            WORKER_BOARD.set(b);
            WORKER_BOT.set(bot);
        }
        int base = i * SNAP;
        System.arraycopy(positions, base, b.pieceBitboards, 0, 12);
        b.turn = (int) positions[base + 12];
        b.updateOccupancies();
        int eval = bot.evaluate();
        // evaluate() is side-to-move relative; convert to White-relative
        return b.turn == Board.WHITE ? eval : -eval;
    }

    // evaluations of every position in idx under params p (p is applied once, up front)
    static int[] evaluateAll(final int[] idx, int[] p) {
        PARAM_BOT.applyParams(p);
        final int[] evals = new int[idx.length];
        List<Future<?>> jobs = new ArrayList<Future<?>>();
        for (int c = 0; c < CHUNKS; c++) {
            final int from = (int) ((long) idx.length * c / CHUNKS);
            final int to = (int) ((long) idx.length * (c + 1) / CHUNKS);
            jobs.add(pool.submit(new Runnable() {
                public void run() { for (int j = from; j < to; j++) evals[j] = evaluateStored(idx[j]); }
            }));
        }
        waitAll(jobs);
        return evals;
    }

    // -------------------------------------------------------------------------
    // MSE over a subset of the dataset
    // -------------------------------------------------------------------------
    static double computeMSE(final int[] idx, int[] p) {
        PARAM_BOT.applyParams(p);
        final double[] partial = new double[CHUNKS];
        final double k = K;
        List<Future<?>> jobs = new ArrayList<Future<?>>();
        for (int c = 0; c < CHUNKS; c++) {
            final int chunk = c;
            final int from = (int) ((long) idx.length * c / CHUNKS);
            final int to = (int) ((long) idx.length * (c + 1) / CHUNKS);
            jobs.add(pool.submit(new Runnable() {
                public void run() {
                    double sum = 0.0;
                    for (int j = from; j < to; j++) {
                        double diff = sigmoid(evaluateStored(idx[j]), k) - results[idx[j]];
                        sum += diff * diff;
                    }
                    partial[chunk] = sum;
                }
            }));
        }
        waitAll(jobs);
        double total = 0.0;
        for (double s : partial) total += s; // fixed order -> same result run to run
        return total / idx.length;
    }

    static void waitAll(List<Future<?>> jobs) {
        try {
            for (Future<?> f : jobs) f.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------------------------------------------------------------
    // Dataset loader + train/validation split
    // -------------------------------------------------------------------------
    static void loadDataset(String path, double trainFraction, long seed) throws IOException {
        List<long[]> snaps = new ArrayList<long[]>();
        List<Double> res = new ArrayList<Double>();
        Board b = new Board();
        BufferedReader br = new BufferedReader(new FileReader(path));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // Last token is the result; everything before it is the FEN
                int lastSpace = line.lastIndexOf(' ');
                if (lastSpace < 0) continue;
                String fen    = line.substring(0, lastSpace).trim();
                String resStr = line.substring(lastSpace + 1).trim();
                double result;
                try { result = Double.parseDouble(resStr); }
                catch (NumberFormatException e) { continue; }
                b.convertFENtoPosition(fen);
                long[] s = new long[SNAP];
                System.arraycopy(b.pieceBitboards, 0, s, 0, 12);
                s[12] = b.turn;
                snaps.add(s);
                res.add(result);
            }
        } finally {
            br.close();
        }

        int n = snaps.size();
        positions = new long[n * SNAP];
        results = new double[n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(snaps.get(i), 0, positions, i * SNAP, SNAP);
            results[i] = res.get(i);
        }

        // seeded shuffle, then the first trainFraction is training and the rest is validation
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Random rng = new Random(seed);
        for (int i = n - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = order[i]; order[i] = order[j]; order[j] = t;
        }
        int nTrain = (int) (n * trainFraction);
        trainIdx = Arrays.copyOfRange(order, 0, nTrain);
        validIdx = Arrays.copyOfRange(order, nTrain, n);
        Arrays.sort(trainIdx); // sequential memory access is faster; order doesn't matter for the sum
        Arrays.sort(validIdx);
        System.out.printf("Loaded %,d positions: %,d training / %,d validation (seed %d)%n", n, nTrain, n - nTrain, seed);
    }

    // -------------------------------------------------------------------------
    // Coordinate descent
    // -------------------------------------------------------------------------
    static int[] tunableIndices(boolean ksOnly) {
        List<Integer> list = new ArrayList<Integer>();
        for (int i = ksOnly ? KS_FIRST : 0; i < params.length; i++) {
            // skip padding slots (passed pawn index 0 and 7 are unused)
            if (i == 13 || i == 20 || i == 21 || i == 28) continue;
            // pawn PST ranks 1 and 8 are never read (no pawn can stand there), so tuning them is a no-op
            if ((i >= 32 && i < 40) || (i >= 88 && i < 96)) continue;
            list.add(i);
        }
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    // per-parameter bounds the optimizer may not step outside of (unbounded unless --ks-bounds)
    static int[] lowerBound, upperBound;

    // --ks-bounds: keep every king-safety constant in the meaning the design gives it -- they're all
    // penalties or attack weights (>= 0), and a queenless attack can't count more than one with a
    // queen (KS_NO_QUEEN_SCALE <= 16)
    static void setKingSafetyBounds() {
        for (int i = KS_FIRST; i < params.length; i++) lowerBound[i] = 0;
        upperBound[494] = 16;
    }

    static void tune(int[] tunable) {
        double bestMSE = computeMSE(trainIdx, params);
        System.out.printf("Initial MSE: %.8f  (validation %.8f)  tuning %d params%n",
                bestMSE, computeMSE(validIdx, params), tunable.length);

        for (int pass = 1; pass <= maxPasses; pass++) {
            boolean improved = false;
            int improvements = 0;

            for (int i : tunable) {
                // try +STEP
                params[i] += STEP;
                double mseUp = (params[i] <= upperBound[i]) ? computeMSE(trainIdx, params) : Double.MAX_VALUE;
                if (mseUp < bestMSE) {
                    bestMSE = mseUp;
                    improved = true;
                    improvements++;
                    continue;
                }

                // try -STEP
                params[i] -= 2 * STEP;
                double mseDown = (params[i] >= lowerBound[i]) ? computeMSE(trainIdx, params) : Double.MAX_VALUE;
                if (mseDown < bestMSE) {
                    bestMSE = mseDown;
                    improved = true;
                    improvements++;
                    continue;
                }

                // revert
                params[i] += STEP;
            }

            System.out.printf("Pass %2d | MSE: %.8f | validation: %.8f | improved %d params%n",
                pass, bestMSE, computeMSE(validIdx, params), improvements);

            if (!improved) {
                System.out.println("Converged.");
                break;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Find optimal K  (run once before tuning)
    // -------------------------------------------------------------------------
    static void findOptimalK() {
        System.out.println("Finding optimal K on the training set...");
        int[] evals = evaluateAll(trainIdx, params); // evals don't depend on K -- compute them once
        double bestK   = 0.5;
        double bestMSE = Double.MAX_VALUE;

        for (double k = 0.5; k <= 2.5; k += 0.01) {
            double total = 0.0;
            for (int j = 0; j < trainIdx.length; j++) {
                double diff = sigmoid(evals[j], k) - results[trainIdx[j]];
                total += diff * diff;
            }
            double mse = total / trainIdx.length;
            if (mse < bestMSE) { bestMSE = mse; bestK = k; }
        }
        System.out.printf("Optimal K = %.2f  (MSE %.8f)%n", bestK, bestMSE);
        System.out.println("Pass --k " + bestK + " (or set K at the top of TexelTuner.java) before tuning.");
    }

    // -------------------------------------------------------------------------
    // Paired comparison on the validation set: is B's MSE better than A's by more than noise?
    // -------------------------------------------------------------------------
    static void compareOnValidation(String nameA, int[] a, String nameB, int[] b) {
        int[] ea = evaluateAll(validIdx, a);
        int[] eb = evaluateAll(validIdx, b);
        int n = validIdx.length;
        double sumA = 0, sumB = 0, sumD = 0, sumD2 = 0;
        for (int j = 0; j < n; j++) {
            double r = results[validIdx[j]];
            double da = sigmoid(ea[j], K) - r, db = sigmoid(eb[j], K) - r;
            double d = db * db - da * da;
            sumA += da * da; sumB += db * db; sumD += d; sumD2 += d * d;
        }
        double meanD = sumD / n;
        double se = Math.sqrt(Math.max(0, sumD2 / n - meanD * meanD) / (n - 1));
        System.out.printf("  validation MSE  %-22s %.8f%n", nameA, sumA / n);
        System.out.printf("  validation MSE  %-22s %.8f%n", nameB, sumB / n);
        System.out.printf("  paired change (B - A): %+.8f  (%+.3f%%)  SE %.8f  z = %.1f%n",
                meanD, 100.0 * meanD / (sumA / n), se, se > 0 ? meanD / se : 0.0);
    }

    // -------------------------------------------------------------------------
    // Output — paste directly into KingSafetyBot.java
    // -------------------------------------------------------------------------
    static void printTunedParams() {
        System.out.println("\n========== TUNED PARAMETERS ==========");
        System.out.println("// Material");
        System.out.printf("private static int PAWN   = %d;%n", params[0]);
        System.out.printf("private static int KNIGHT = %d;%n", params[1]);
        System.out.printf("private static int BISHOP = %d;%n", params[2]);
        System.out.printf("private static int ROOK   = %d;%n", params[3]);
        System.out.printf("private static int QUEEN  = %d;%n", params[4]);

        System.out.println("\n// Mobility");
        System.out.printf("private static int KNIGHT_MOB_MG = %d;%n", params[5]);
        System.out.printf("private static int KNIGHT_MOB_EG = %d;%n", params[6]);
        System.out.printf("private static int BISHOP_MOB_MG = %d;%n", params[7]);
        System.out.printf("private static int BISHOP_MOB_EG = %d;%n", params[8]);
        System.out.printf("private static int ROOK_MOB_MG   = %d;%n", params[9]);
        System.out.printf("private static int ROOK_MOB_EG   = %d;%n", params[10]);
        System.out.printf("private static int QUEEN_MOB_MG  = %d;%n", params[11]);
        System.out.printf("private static int QUEEN_MOB_EG  = %d;%n", params[12]);

        System.out.println("\n// Passed pawn bonuses");
        System.out.print("private static int[] PASSED_PAWN_MG = {");
        for (int i = 13; i <= 20; i++) System.out.print(params[i] + (i < 20 ? ", " : ""));
        System.out.println("};");
        System.out.print("private static int[] PASSED_PAWN_EG = {");
        for (int i = 21; i <= 28; i++) System.out.print(params[i] + (i < 28 ? ", " : ""));
        System.out.println("};");

        System.out.printf("%nprivate static int KING_PROXIMITY_WEIGHT    = %d;%n", params[29]);
        System.out.printf("private static int ISOLATED_PAWN_PENALTY_MG = %d;%n", params[30]);
        System.out.printf("private static int ISOLATED_PAWN_PENALTY_EG = %d;%n", params[31]);

        String[] pstNames = {"PAWN_PST", "KNIGHT_PST", "BISHOP_PST",
                             "ROOK_PST", "QUEEN_PST", "KING_PST", "KING_ENDGAME_PST"};
        int base = 32;
        for (String name : pstNames) {
            System.out.printf("%nprivate static int[] %s = {%n", name);
            for (int row = 0; row < 8; row++) {
                System.out.print("    ");
                for (int col = 0; col < 8; col++) {
                    int idx = base + row * 8 + col;
                    System.out.printf("%4d", params[idx]);
                    if (col < 7) System.out.print(",");
                    else if (row < 7) System.out.print(",");
                }
                System.out.println();
            }
            System.out.println("};");
            base += 64;
        }

        String[] ksNames = {"KS_SHIELD_MISSING", "KS_SHIELD_ADVANCED", "KS_STORM_RANK3", "KS_STORM_RANK4",
                            "KS_STORM_RANK5", "KS_FILE_NO_OWN_PAWN", "KS_FILE_NO_ENEMY_PAWN",
                            "KS_ATTACK_WEIGHT_N", "KS_ATTACK_WEIGHT_B", "KS_ATTACK_WEIGHT_R", "KS_ATTACK_WEIGHT_Q",
                            "KS_MIN_ATTACKERS", "KS_DANGER_QUAD", "KS_DANGER_MAX", "KS_NO_QUEEN_SCALE"};
        System.out.println("\n// King safety");
        for (int i = 0; i < ksNames.length; i++) {
            System.out.printf("private static int %-21s = %d;%n", ksNames[i], params[KS_FIRST + i]);
        }
        System.out.println("=======================================\n");
    }

    static int[] readParams(String path) throws IOException {
        List<Integer> values = new ArrayList<Integer>();
        BufferedReader br = new BufferedReader(new FileReader(path));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) values.add(Integer.parseInt(line));
            }
        } finally {
            br.close();
        }
        if (values.size() != params.length) throw new IOException(path + ": expected " + params.length + " values, got " + values.size());
        int[] p = new int[values.size()];
        for (int i = 0; i < p.length; i++) p[i] = values.get(i);
        return p;
    }

    static void writeParams(String path, int[] p) throws IOException {
        PrintWriter pw = new PrintWriter(new FileWriter(path));
        try {
            for (int v : p) pw.println(v);
        } finally {
            pw.close();
        }
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        boolean findK = false, ksOnly = false, ksOff = false, report = false, ksBounds = false;
        String paramsIn = null, paramsOut = null;
        double trainFraction = 0.8;
        long seed = 20261022L;
        int threads = Runtime.getRuntime().availableProcessors();
        List<String> extraBounds = new ArrayList<String>();
        for (int a = 0; a < args.length; a++) {
            String arg = args[a];
            if (arg.equals("--find-k")) findK = true;
            else if (arg.equals("--ks-only")) ksOnly = true;
            else if (arg.equals("--ks-off")) ksOff = true;
            else if (arg.equals("--ks-bounds")) ksBounds = true;
            else if (arg.equals("--bound")) extraBounds.add(args[++a]);
            else if (arg.equals("--report")) report = true;
            else if (arg.equals("--print-params")) { printTunedParams(); return; }
            else if (arg.equals("--data")) datasetPath = args[++a];
            else if (arg.equals("--k")) K = Double.parseDouble(args[++a]);
            else if (arg.equals("--params-in")) paramsIn = args[++a];
            else if (arg.equals("--params-out")) paramsOut = args[++a];
            else if (arg.equals("--passes")) maxPasses = Integer.parseInt(args[++a]);
            else if (arg.equals("--split")) trainFraction = Double.parseDouble(args[++a]);
            else if (arg.equals("--seed")) seed = Long.parseLong(args[++a]);
            else if (arg.equals("--threads")) threads = Integer.parseInt(args[++a]);
            else throw new IllegalArgumentException("unknown option " + arg);
        }

        if (paramsIn != null) params = readParams(paramsIn);
        if (ksOff) for (int i = KS_FIRST; i < params.length; i++) params[i] = 0;
        lowerBound = new int[params.length];
        upperBound = new int[params.length];
        Arrays.fill(lowerBound, Integer.MIN_VALUE);
        Arrays.fill(upperBound, Integer.MAX_VALUE);
        if (ksBounds) setKingSafetyBounds();
        for (String bound : extraBounds) { // index:lo:hi
            String[] f = bound.split(":");
            int i = Integer.parseInt(f[0]);
            lowerBound[i] = Integer.parseInt(f[1]);
            upperBound[i] = Integer.parseInt(f[2]);
        }

        System.out.println("KingSafetyBot Texel Tuner (Java 8)");
        System.out.println("==================================");
        System.out.printf("Parameters: %d | K = %s | threads = %d | mode = %s%n%n", params.length, K, threads,
                findK ? "find-k" : report ? "report" : ksOnly ? "king-safety only" : "all parameters");

        pool = Executors.newFixedThreadPool(threads);
        try {
            loadDataset(datasetPath, trainFraction, seed);
            if (positions.length == 0) {
                System.err.println("Dataset is empty. Populate " + datasetPath + " with quiet FEN positions.");
                return;
            }

            if (findK) { findOptimalK(); return; }

            int[] start = params.clone();
            int[] ksOffStart = start.clone();
            for (int i = KS_FIRST; i < ksOffStart.length; i++) ksOffStart[i] = 0;

            if (report) {
                System.out.printf("train MSE %.8f | validation MSE %.8f%n", computeMSE(trainIdx, params), computeMSE(validIdx, params));
                compareOnValidation("king safety off", ksOffStart, "these params", start);
                return;
            }

            long t0 = System.currentTimeMillis();
            tune(tunableIndices(ksOnly));
            System.out.printf("%nTuning took %.1f min%n", (System.currentTimeMillis() - t0) / 60000.0);

            System.out.printf("train MSE: start %.8f -> tuned %.8f%n", computeMSE(trainIdx, start), computeMSE(trainIdx, params));
            System.out.println("Held-out validation (positions the tuner never saw):");
            compareOnValidation("start params", start, "tuned params", params);
            compareOnValidation("king safety off", ksOffStart, "tuned params", params);

            printTunedParams();
            if (paramsOut != null) {
                writeParams(paramsOut, params);
                System.out.println("Tuned vector written to " + paramsOut);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
