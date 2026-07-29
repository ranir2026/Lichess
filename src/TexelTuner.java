import java.io.*;
import java.util.*;

/**
 * Texel Tuner for PVSBot  (Java 8 compatible)
 *
 * HOW IT WORKS
 * ============
 * Texel tuning minimizes the mean squared error (MSE) between:
 *   sigmoid(eval(pos) / K)  and  gameResult (1.0 = white win, 0.5 = draw, 0.0 = black loss)
 *
 * The optimizer used is coordinate descent: it nudges each parameter up and down by a step,
 * keeps the change if MSE improves, and repeats until a full pass over all params changes nothing.
 *
 * INTEGRATION STEPS
 * =================
 * 1. Add applyParams(int[] p) to PVSBot.java (printed to stdout on first run).
 *    Change all tunable constants from "static final" to plain "static" so applyParams can write them.
 *
 * 2. Fill in evaluatePosition() below with a real Board + PVSBot.evaluate() call.
 *
 * 3. Generate positions.txt — one "FEN result" per line, e.g.:
 *      rnbqkb1r/pppp1ppp/5n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 0 1 0.5
 *    Positions must be quiet (no captures pending). Result: 1.0=white win, 0.5=draw, 0.0=black win.
 *    Aim for 1M+ positions.
 *
 * 4. Run with --find-k first to find the optimal K, update the constant, then run normally.
 *
 * PARAMETER LAYOUT (matches PVSBot's eval)
 * =========================================
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
 * Total: 480 parameters
 */
public class TexelTuner {

    // -------------------------------------------------------------------------
    // Tuning hyperparameters
    // -------------------------------------------------------------------------
    static double K         = 1.4500000000000008; // sigmoid scaling constant — tune with --find-k
    static final int  STEP       = 1;    // coordinate-descent step size (centipawns)
    static final int  MAX_PASSES = 50;
    static final String DATASET_PATH = "positions.txt";

    static Board tunerBoard = new Board();
    static TurquoiseBot tunerBot = new TurquoiseBot(tunerBoard);

    // -------------------------------------------------------------------------
    // Initial parameter values — copied directly from PVSBot.java
    // -------------------------------------------------------------------------
    static int[] params = {
        // [0-4] material
        100, 320, 330, 500, 900,
        // [5-12] mobility MG/EG
        4, 3,   // knight
        4, 3,   // bishop
        2, 4,   // rook
        1, 2,   // queen
        // [13-20] PASSED_PAWN_MG
        0, 5, 8, 15, 25, 40, 60, 0,
        // [21-28] PASSED_PAWN_EG
        0, 10, 20, 35, 60, 100, 150, 0,
        // [29] king proximity weight
        5,
        // [30-31] isolated pawn penalties
        12, 18,
        // [32-95] PAWN_PST
         0,  0,  0,  0,  0,  0,  0,  0,
         5, 10, 10,-20,-20, 10, 10,  5,
         5, -5,-10,  0,  0,-10, -5,  5,
         0,  0,  0, 20, 20,  0,  0,  0,
         5,  5, 10, 25, 25, 10,  5,  5,
        10, 10, 20, 30, 30, 20, 10, 10,
        50, 50, 50, 50, 50, 50, 50, 50,
         0,  0,  0,  0,  0,  0,  0,  0,
        // [96-159] KNIGHT_PST
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50,
        // [160-223] BISHOP_PST
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -20,-10,-10,-10,-10,-10,-10,-20,
        // [224-287] ROOK_PST
          0,  0,  0,  5,  5,  0,  0,  0,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
          5, 10, 10, 10, 10, 10, 10,  5,
          0,  0,  0,  0,  0,  0,  0,  0,
        // [288-351] QUEEN_PST
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -10,  5,  5,  5,  5,  5,  0,-10,
          0,  0,  5,  5,  5,  5,  0, -5,
         -5,  0,  5,  5,  5,  5,  0, -5,
        -10,  0,  5,  5,  5,  5,  0,-10,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20,
        // [352-415] KING_PST
         20, 30, 10,  0,  0, 10, 30, 20,
         20, 20,  0,  0,  0,  0, 20, 20,
        -10,-20,-20,-20,-20,-20,-20,-10,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        // [416-479] KING_ENDGAME_PST
        -50,-40,-30,-20,-20,-30,-40,-50,
        -30,-20,-10,  0,  0,-10,-20,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-30,  0,  0,  0,  0,-30,-30,
        -50,-30,-30,-30,-30,-30,-30,-50,
    };

    // -------------------------------------------------------------------------
    // Dataset entry  (plain class — Java 8 compatible, no "record")
    // -------------------------------------------------------------------------
    static class Position {
        final String fen;
        final double result;
        Position(String fen, double result) {
            this.fen    = fen;
            this.result = result;
        }
    }

    // -------------------------------------------------------------------------
    // Sigmoid
    // -------------------------------------------------------------------------
    static double sigmoid(double score) {
        return 1.0 / (1.0 + Math.pow(10.0, -K * score / 400.0));
    }

    // -------------------------------------------------------------------------
    // MSE over dataset
    // -------------------------------------------------------------------------
    static double computeMSE(List<Position> dataset, int[] p) {
        double total = 0.0;
        for (Position pos : dataset) {
            double eval = evaluatePosition(pos.fen, p);
            double sig  = sigmoid(eval);
            double diff = sig - pos.result;
            total += diff * diff;
        }
        return total / dataset.size();
    }

    // -------------------------------------------------------------------------
    // EVALUATE POSITION — hook your Board + PVSBot.evaluate() here
    // -------------------------------------------------------------------------
    /**
     * Return the evaluation in centipawns from White's perspective.
     *
     * REPLACE THE BODY with a real call. Example:
     *
     *   Board b = new Board();
     *   b.loadFEN(fen);
     *   PVSBot bot = new PVSBot(b);
     *   bot.applyParams(p);
     *   int eval = bot.evaluate();
     *   // evaluate() is side-to-move relative; convert to White-relative:
     *   return b.turn == Board.WHITE ? eval : -eval;
     */
    static double evaluatePosition(String fen, int[] p) {
        tunerBoard.convertFENtoPosition(fen);
        tunerBot.applyParams(p);
        int eval = tunerBot.evaluate();
        return tunerBoard.turn == Board.WHITE ? eval : -eval;
    }

    // -------------------------------------------------------------------------
    // Dataset loader
    // -------------------------------------------------------------------------
    static List<Position> loadDataset(String path) throws IOException {
        List<Position> positions = new ArrayList<Position>();
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
                positions.add(new Position(fen, result));
            }
        } finally {
            br.close();
        }
        System.out.printf("Loaded %,d positions%n", positions.size());
        return positions;
    }

    // -------------------------------------------------------------------------
    // Coordinate descent
    // -------------------------------------------------------------------------
    static void tune(List<Position> dataset) {
        double bestMSE = computeMSE(dataset, params);
        System.out.printf("Initial MSE: %.8f%n", bestMSE);

        for (int pass = 1; pass <= MAX_PASSES; pass++) {
            boolean improved = false;
            int improvements = 0;

            for (int i = 0; i < params.length; i++) {
                // skip padding slots (passed pawn index 0 and 7 are unused)
                if (i == 13 || i == 20 || i == 21 || i == 28) continue;

                // try +STEP
                params[i] += STEP;
                double mseUp = computeMSE(dataset, params);
                if (mseUp < bestMSE) {
                    bestMSE = mseUp;
                    improved = true;
                    improvements++;
                    continue;
                }

                // try -STEP
                params[i] -= 2 * STEP;
                double mseDown = computeMSE(dataset, params);
                if (mseDown < bestMSE) {
                    bestMSE = mseDown;
                    improved = true;
                    improvements++;
                    continue;
                }

                // revert
                params[i] += STEP;
            }

            System.out.printf("Pass %2d | MSE: %.8f | improved %d params%n",
                pass, bestMSE, improvements);

            if (!improved) {
                System.out.println("Converged.");
                break;
            }
        }

        printTunedParams();
    }

    // -------------------------------------------------------------------------
    // Find optimal K  (run once before tuning)
    // -------------------------------------------------------------------------
    static void findOptimalK(List<Position> dataset) {
        System.out.println("Finding optimal K...");
        double bestK   = 0.5;
        double bestMSE = Double.MAX_VALUE;

        for (double k = 0.5; k <= 2.5; k += 0.01) {
            double mse = computeMSEWithK(dataset, params, k);
            System.out.printf("K=%.2f  MSE=%.8f%n", k, mse);
            if (mse < bestMSE) { bestMSE = mse; bestK = k; }
        }
        System.out.printf("Optimal K = %.2f  (MSE %.8f)%n", bestK, bestMSE);
        System.out.println("Set K = " + bestK + " at the top of TexelTuner.java before tuning.");
    }

    static double computeMSEWithK(List<Position> dataset, int[] p, double k) {
        double total = 0.0;
        for (Position pos : dataset) {
            double eval = evaluatePosition(pos.fen, p);
            double sig  = 1.0 / (1.0 + Math.pow(10.0, -k * eval / 400.0));
            double diff = sig - pos.result;
            total += diff * diff;
        }
        return total / dataset.size();
    }

    // -------------------------------------------------------------------------
    // Output — paste directly into PVSBot.java
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
        System.out.println("=======================================\n");
    }

    // -------------------------------------------------------------------------
    // applyParams() snippet — add this method to PVSBot.java
    // -------------------------------------------------------------------------
    static void printApplyParamsSnippet() {
        System.out.println("// ---- ADD THIS METHOD TO PVSBot.java ----");
        System.out.println("// Also change all tunable constants from 'static final' to 'static'.");
        System.out.println("public void applyParams(int[] p) {");
        System.out.println("    PAWN   = p[0]; KNIGHT = p[1]; BISHOP = p[2]; ROOK = p[3]; QUEEN = p[4];");
        System.out.println("    KNIGHT_MOB_MG = p[5];  KNIGHT_MOB_EG = p[6];");
        System.out.println("    BISHOP_MOB_MG = p[7];  BISHOP_MOB_EG = p[8];");
        System.out.println("    ROOK_MOB_MG   = p[9];  ROOK_MOB_EG   = p[10];");
        System.out.println("    QUEEN_MOB_MG  = p[11]; QUEEN_MOB_EG  = p[12];");
        System.out.println("    for (int i = 0; i < 8; i++) {");
        System.out.println("        PASSED_PAWN_MG[i] = p[13 + i];");
        System.out.println("        PASSED_PAWN_EG[i] = p[21 + i];");
        System.out.println("    }");
        System.out.println("    KING_PROXIMITY_WEIGHT    = p[29];");
        System.out.println("    ISOLATED_PAWN_PENALTY_MG = p[30];");
        System.out.println("    ISOLATED_PAWN_PENALTY_EG = p[31];");
        System.out.println("    System.arraycopy(p,  32, PAWN_PST,        0, 64);");
        System.out.println("    System.arraycopy(p,  96, KNIGHT_PST,      0, 64);");
        System.out.println("    System.arraycopy(p, 160, BISHOP_PST,      0, 64);");
        System.out.println("    System.arraycopy(p, 224, ROOK_PST,        0, 64);");
        System.out.println("    System.arraycopy(p, 288, QUEEN_PST,       0, 64);");
        System.out.println("    System.arraycopy(p, 352, KING_PST,        0, 64);");
        System.out.println("    System.arraycopy(p, 416, KING_ENDGAME_PST,0, 64);");
        System.out.println("}");
        System.out.println("// ---- END SNIPPET ----");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Main
    // -------------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        System.out.println("PVSBot Texel Tuner (Java 8)");
        System.out.println("===========================");
        System.out.printf("Parameters: %d%n%n", params.length);

        printApplyParamsSnippet();

        if (args.length > 0 && args[0].equals("--print-params")) {
            printTunedParams();
            return;
        }

        List<Position> dataset = loadDataset(DATASET_PATH);
        if (dataset.isEmpty()) {
            System.err.println("Dataset is empty. Populate positions.txt with quiet FEN positions.");
            return;
        }

        if (args.length > 0 && args[0].equals("--find-k")) {
            findOptimalK(dataset);
            return;
        }

        tune(dataset);
    }
}