package engines;
import java.util.ArrayList;
import java.util.Arrays;
import core.*;
import pieces.*;

public class FirstEngine {
    public String name;
    
    // speed/efficiency instrumentation
    public long nodesSearched = 0;
    public long lastNodesSearched = 0;
    public int lastDepthReached = 0;
    public long lastElapsedMs = 0;
    public long lastNodesPerSecond = 0;

    // hard coded piece values
    private static final double PAWN = 100.0;
    private static final double KNIGHT = 320.0;
    private static final double BISHOP = 330.0;
    private static final double ROOK = 500.0;
    private static final double QUEEN = 900.0;

    // evaluation scores for checkmates and draws
    private static final double MATE_SCORE = 100000.0;
    private static final double DRAW_SCORE = 0.0;

    private Board game;
    private boolean color;

    // tables to handle late-move reductions, killer moves, and history heuristics
    private static final int[][] LMR_TABLE = new int[64][64];
    private int[][] killerMoves = new int[64][2];

    // history heuristics stuff
    private static final int MAX_HISTORY = 16384; // 2^14 ~~ arbitrary limit
    private int[][][] historyTable = new int[2][64][64];
    private int[][] counterMoves = new int[64][64];

    static {
        for (int depth = 1; depth < 64; depth++) {
            for (int moves = 1; moves < 64; moves++) {
                LMR_TABLE[depth][moves] = (int) (0.75 + Math.log(depth) * Math.log(moves) / 2.25);
            }
        }
    }

    // piece square tables -- will need to eventually tune to PeSTO values
    private static final double[] PAWN_PST = {
        0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5,  5, 10, 25, 25, 10,  5,  5,
        0,  0,  0, 20, 20,  0,  0,  0,
        5, -5,-10,  0,  0,-10, -5,  5,
        5, 10, 10,-20,-20, 10, 10,  5,
        0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final double[] KNIGHT_PST = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    };

    private static final double[] BISHOP_PST = {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    };

    private static final double[] ROOK_PST = {
          0,  0,  0,  0,  0,  0,  0,  0,
          5, 10, 10, 10, 10, 10, 10,  5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
          0,  0,  0,  5,  5,  0,  0,  0
    };

    private static final double[] QUEEN_PST = {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
          0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    };

    private static final double[] KING_PST = {
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20,
         20, 30, 10,  0,  0, 10, 30, 20
    };

    private static final double[] KING_ENDGAME_PST = {
        -50,-40,-30,-20,-20,-30,-40,-50,
        -30,-20,-10,  0,  0,-10,-20,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-30,  0,  0,  0,  0,-30,-30,
        -50,-30,-30,-30,-30,-30,-30,-50
    };

    // transpositoin table
    public static final int TABLE_SIZE = 1048576; // 2^20 ~ 32MB-64MB
    private TranspositionTableEntry[] transpositionTable = new TranspositionTableEntry[TABLE_SIZE];

    private int getIndex(long hash) {
        return (int) (Math.abs(hash) % TABLE_SIZE);
    }
    
    private int flip(int i) {
        return (7 - (i / 8)) * 8 + (i % 8);
    }

    public boolean getColor() { return this.color; }

    private volatile boolean stopSearch = false;
    private long searchStopTime = 0L;
    private boolean isTimeUp() {
        if (stopSearch) return true;
        if (searchStopTime > 0 && System.currentTimeMillis() >= searchStopTime) {
            stopSearch = true;
            return true;
        }
        return false;
    }
    
    private void updateHistory(int color, int from, int to, int bonus) {
        int current = historyTable[color][from][to];
        historyTable[color][from][to] = current + bonus 
            - (current * Math.abs(bonus) / MAX_HISTORY);
    }

    public static double countMaterial(Piece p) {
        if (p instanceof Pawn)   return PAWN;
        if (p instanceof Knight) return KNIGHT;
        if (p instanceof Bishop) return BISHOP;
        if (p instanceof Rook)   return ROOK;
        if (p instanceof Queen)  return QUEEN;
        if (p == null) return 0.0;
        return 0;
    }

    public FirstEngine(Board game, boolean color) {
        this.game = game;
        this.color = color;

        this.name = "FirstEngine";
    }
    
    public double Evaluate() {
        double whiteScore = 0.0;
        double blackScore = 0.0;
        int phase = 0; // to keep track of opening/middlegame/endgame
        
        // Calculate material and phase
        for (int i = 0; i < 64; i++) {
            Piece p = this.game.getGameBoard()[i];
            if (p == null) continue;
            
            if (p instanceof Queen) {
                phase += 4;
            }
            else if (p instanceof Rook) {
                phase += 2;
            }
            else if (!(p instanceof Pawn) && !(p instanceof King)) phase += 1;
        }

        phase = Math.min(phase, 24);

        for (int i = 0; i < 64; i++) {
            Piece p = this.game.getGameBoard()[i];
            if (p == null) continue;

            double material = countMaterial(p);
            double positional = 0.0;
            int idx = p.getColor() ? i : flip(i);

            if (p instanceof King) {
                double mg = KING_PST[idx];
                double eg = KING_ENDGAME_PST[idx];
                positional = ((mg * phase) + (eg * (24 - phase))) / 24;
            }
            else {
                if (p instanceof Knight) positional = KNIGHT_PST[idx];
                else if (p instanceof Bishop) positional = BISHOP_PST[idx];
                else if (p instanceof Pawn) positional = PAWN_PST[idx];
                else if (p instanceof Rook) positional = ROOK_PST[idx];
                else if (p instanceof Queen) positional = QUEEN_PST[idx];
            }

            if (p.getColor()) whiteScore += (material + positional);
            else blackScore += (material + positional);
        }

        // If it's white's turn, return (white - black)
        // If it's black's turn, return (black - white)
        int perspective = this.game.getStateTracker().getTurn() ? 1 : -1;
        return (whiteScore - blackScore) * perspective;
    }

    
    public int[] OrderMoves(ArrayList<Integer> moves, int ttMove, int ply) {
        // alpha-beta pruning and iterative deeepning is effective only
        // as much as move ordering is successful. If moves are ordered
        // in an optimal way, lots of branches can be pruned. These 
        // weightings have been tuned through the scientific approach
        // of pulling numbers straight out of my butt. The most optimal 
        // hierachy, however, is known to be: 1. transposition tables
        // 2. promotions 3. killers 4. countermoves 5. history

        long[] moveScores = new long[moves.size()];
        
        for (int i = 0; i < moves.size(); i++) {
            int move = moves.get(i);
            int flags = Move.getFlags(move);
            int score = 0;
            
            int startSq = Move.getStart(move);
            int endSq = Move.getEnd(move);
            
            Piece movingPiece = this.game.getGameBoard()[startSq];
            Piece targetPiece = this.game.getGameBoard()[endSq];
            
            if (move == ttMove) {
                score = 1000000; // if it's the TT move it has absolute priority
            }
            else if (targetPiece != null) {
                // Formula: (10 * VictimValue) - AttackerValue
                // This ensures Pawn takes Queen is ranked higher than Rook takes Queen
                score = (int)(10 * countMaterial(targetPiece) - countMaterial(movingPiece));
                score += 100000; // Offset to ensure captures are always above quiet moves
            }
            else if (flags == Move.EN_PASSANT) {
                score = (int)(10 * PAWN - PAWN);
                score += 100000;
            }
            else {
                if (ply < 64) { // killer moves get 3rd and 4th highest priority
                    if (move == killerMoves[ply][0]) {
                        score += 90000;
                    } else if (move == killerMoves[ply][1]) {
                        score += 80000;
                    }
                }
                
                // counter moves
                if (this.game.getStateTracker().getMoveRecord().size() > 0) {
                    int prevMove = this.game.getStateTracker().getMoveRecord().get(this.game.getStateTracker().getMoveRecord().size() - 1);
                    int prevTo = Move.getEnd(prevMove);
                    int prevFrom = Move.getStart(prevMove);

                    if (move == counterMoves[prevFrom][prevTo]) {
                        score += 70000;
                    }
                }
                
                // history heuristic
                if (!Move.isCapture(move)) {
                    // assign the history score to quiet moves
                    int colorIdx = this.game.getStateTracker().getTurn() ? 1 : 0;
                    score += historyTable[colorIdx][startSq][endSq] * 40000 / MAX_HISTORY;
                }
            }

            if ((Move.getFlags(move) & Move.PROMOTION_QUIET) != 0) {
                score += 300000;
            } else if ((Move.getFlags(move) & Move.PROMOTION_CAPTURE) != 0) {
                score += 300000;
            }

            moveScores[i] = ((long)score << 32) | (move & 0xFFFFFFFFL);
        }
        
        Arrays.sort(moveScores);
        
        int[] orderedMoves = new int[moves.size()];
        for (int i = 0; i < moveScores.length; i++) {
            orderedMoves[i] = (int)(moveScores[moveScores.length - 1 - i] & 0xFFFFFFFFL);
        }
        
        return orderedMoves;
    }
    
    public double SearchCaptures(double alpha, double beta, int ply) {
        nodesSearched++;
        if (isTimeUp()) return alpha;
        if (ply >= 63) {
            return Evaluate();
        }

        if (this.game.getStateTracker().isThreefoldRepetition(this.game.getCurrentHash())) {
            return DRAW_SCORE;
        }

        double evaluation = Evaluate();
        if (evaluation >= beta) {
            return beta;
        }
        alpha = Math.max(alpha, evaluation);
        
        ArrayList<Integer> moves = this.game.getCaptureMoves(this.game.getStateTracker().getTurn());
        int[] sortedMoves = OrderMoves(moves, -1, ply);
        
        for (int move : sortedMoves) {
            if (isTimeUp()) break;
            this.game.makeMove(move);
            double score = -SearchCaptures(-beta, -alpha, ply + 1);
            this.game.unmakeMove(move);

            if (isTimeUp()) break;
            
            if (score >= beta) {
                return beta;
            }
            alpha = Math.max(alpha, score);
        }
        
        return alpha;
    }

    public double Search(int depth, double alpha, double beta, int ply, boolean allowNMP) {
        if (Thread.currentThread().isInterrupted()) {
            return 0;
        }
        if (isTimeUp()) return alpha;

        nodesSearched++;
        long hash = this.game.getCurrentHash();

        if (this.game.getStateTracker().isThreefoldRepetition(hash)) {
            return DRAW_SCORE;
        }

        // TT Lookup
        int ttIndex = getIndex(hash);
        TranspositionTableEntry entry = transpositionTable[ttIndex];
        if (entry != null && entry.key == hash && entry.depth >= depth) {
            if (entry.type == TranspositionTableEntry.EXACT) return entry.score;
            if (entry.type == TranspositionTableEntry.LOWER_BOUND) alpha = Math.max(alpha, entry.score);
            if (entry.type == TranspositionTableEntry.UPPER_BOUND) beta = Math.min(beta, entry.score);
            if (alpha >= beta) return entry.score;
        }
        int ttMove = (entry != null && entry.key == hash) ? entry.bestMove : -1;

        // quiescence
        if (depth == 0) {
            return SearchCaptures(alpha, beta, ply);
        }

        boolean isWhiteTurn = this.game.getStateTracker().getTurn();
        ArrayList<Integer> moves = this.game.getLegalMoves(isWhiteTurn); 

        // game over check
        if (moves.isEmpty()) {
            if (this.game.isInCheck(isWhiteTurn)) {
                return -MATE_SCORE + ply;
            } else {
                return DRAW_SCORE;
            }
        }

        // Null move pruning
        if (allowNMP && depth >= 3 && !this.game.isInCheck(isWhiteTurn) && this.game.getStateTracker().getNonPawnCount() > 2) {
            double staticEval = Evaluate();
            if (staticEval >= beta) {
                int R = (depth >= 10) ? depth/6 + 1 : depth/4 + 2;
                this.game.makeNullMove();
                double nullScore = -Search(depth - 1 - R, -beta, -beta + 1, ply + 1, false);
                this.game.unmakeNullMove();

                if (isTimeUp()) return alpha;

                if (depth >= 6 && nullScore >= beta) {
                    double verifyScore = Search(depth - 1, alpha, beta, ply, false);
                    if (isTimeUp()) return alpha;
                    if (verifyScore >= beta) return beta;
                }
            }
        }

        double originalAlpha = alpha;
        int bestMoveFound = -1;
        int[] orderedMoves = OrderMoves(moves, ttMove, ply);
        int movesSearched = 0;

        ArrayList<Integer> quietMovesSearched = new ArrayList<>();

        // Move Loop
        for (int move : orderedMoves) {
            this.game.makeMove(move);
            movesSearched++;

            if (isTimeUp()) {
                this.game.unmakeMove(move);
                break;
            }

            double score;

            boolean isCapture = Move.isCapture(move);
            boolean isPromotion = (Move.getFlags(move) & Move.PROMOTION_QUIET) != 0;
            boolean inCheck = this.game.isInCheck(this.game.getStateTracker().getTurn());

            if (depth >= 3 && movesSearched > 4 && !isCapture && !isPromotion && !inCheck) {
                int reduction = LMR_TABLE[depth][Math.min(movesSearched, 63)];
                int newDepth = Math.max(1, depth - 1 - reduction);
                score = -Search(newDepth, -(alpha + 1), -alpha, ply + 1, true);
                if (score > alpha) {
                    score = -Search(depth - 1, -beta, -alpha, ply + 1, true);
                }
            } else {
                score = -Search(depth - 1, -beta, -alpha, ply + 1, true);
            }

            this.game.unmakeMove(move);

            if (isTimeUp()) break;

            if (score >= beta) {
                // update the transposition table with the beta cutoff
                transpositionTable[ttIndex] = new TranspositionTableEntry(hash, beta, depth, TranspositionTableEntry.LOWER_BOUND, move);

                // history heuristic update -- ignore captures
                if (!Move.isCapture(move)) {
                    int clampedBonus = Math.min(depth * depth, MAX_HISTORY);
                    int colorIdx = isWhiteTurn ? 1 : 0;

                    // reward the move that caused cutoff
                    updateHistory(colorIdx, Move.getStart(move), Move.getEnd(move), clampedBonus);

                    // penalize all previous quiet moves
                    for (int prevMove : quietMovesSearched) {
                        if (prevMove == move) continue;
                        updateHistory(colorIdx, Move.getStart(prevMove), Move.getEnd(prevMove), -clampedBonus);
                    }
                }

                // save the move as a killer move
                if (ply < 64 && !Move.isCapture(move) && move != killerMoves[ply][0]) {
                    killerMoves[ply][1] = killerMoves[ply][0];
                    killerMoves[ply][0] = move;
                }

                // countermoves
                ArrayList<Integer> moveRecord = this.game.getStateTracker().getMoveRecord(); 
                int prevMove = moveRecord.get(moveRecord.size() - 1);
                if (prevMove != -1) {
                    int prevTo = Move.getEnd(prevMove);
                    int prevFrom = Move.getStart(prevMove);
                    counterMoves[prevFrom][prevTo] = move;
                }
                  
                return beta;
            }

            if (!isCapture) {
                quietMovesSearched.add(move);
            }

            if (score > alpha) {
                alpha = score;
                bestMoveFound = move;
            }
        }

        // store in transposition table
        int type = (alpha <= originalAlpha) ? TranspositionTableEntry.UPPER_BOUND : TranspositionTableEntry.EXACT;
        if (entry == null || depth >= entry.depth) { // don't overwrite the transposition table with a calculation at shallower depth
            transpositionTable[ttIndex] = new TranspositionTableEntry(hash, alpha, depth, type, bestMoveFound);
        }

        return alpha;
    }

    public int getBestMove(int TotalTimeLeft, int increment) {
        // hardcoded in that it should play 1. e4 e5 instead of putting its
        // knight out too early and causing it to get kicked around and losing
        // a lot of tempo in the opening... there's probably a better way around this,
        // but this seems like the simplest solution!
        if (!this.color && this.game.getStateTracker().getPly() == 1) {
            int lastMove = this.game.getStateTracker().getMoveRecord().get(0);
            if (Move.getStart(lastMove) == 52 && Move.getEnd(lastMove) == 36) {
                return Move.encode(12, 28, Move.DOUBLE_PAWN);
            }
        } else if (this.color && this.game.getStateTracker().getPly() == 0) {
            return Move.encode(52, 36, Move.DOUBLE_PAWN);
        }

        int maxDepth = 25;
        long startTime = System.currentTimeMillis();
        long timeLimit = (TotalTimeLeft/40) + increment;

        if ((this.game.getStateTracker().getPly() / 2) <= 4) timeLimit /= 3;

        stopSearch = false;
        searchStopTime = startTime + timeLimit - 10;
        if (searchStopTime <= startTime) searchStopTime = startTime + 1;
        
        int overallBestMove = -1;
        int completedDepth = 0;
        nodesSearched = 0;

        double previousScore = 0.0;
        double WINDOW = 50.0;

        for (int currentDepth = 1; currentDepth <= maxDepth; currentDepth++) {
            ArrayList<Integer> moves = this.game.getLegalMoves(this.game.getStateTracker().getTurn());
            if (moves.isEmpty()) break;

            double alpha = Double.NEGATIVE_INFINITY;
            double beta = Double.POSITIVE_INFINITY;

            if (currentDepth > 1) {
                alpha = previousScore - WINDOW;
                beta = previousScore + WINDOW;
            }

            while (true) {
                double originalAlpha = alpha;
                double originalBeta = beta;
                
                int bestMoveThisIteration = -1;
                double bestScoreThisIteration = Double.NEGATIVE_INFINITY;
                
                TranspositionTableEntry entry = transpositionTable[getIndex(this.game.getCurrentHash())];
                int ttMove = (entry != null && entry.key == this.game.getCurrentHash()) ? entry.bestMove : -1;
                int[] orderedMoves = OrderMoves(moves, ttMove, 0);
                
                for (int move : orderedMoves) {
                    this.game.makeMove(move);

                    if (isTimeUp()) {
                        this.game.unmakeMove(move);
                        break;
                    }

                    // Search the next level
                    double score = -Search(currentDepth - 1, -beta, -alpha, 1, true);
                    this.game.unmakeMove(move);

                    if (isTimeUp()) break;
    
                    if (score > bestScoreThisIteration) {
                        bestScoreThisIteration = score;
                        bestMoveThisIteration = move;
                    }
    
                    if (score > alpha) {
                        alpha = score;
                    }

                    if (alpha >= beta) {
                        break;
                    }
                }

                if (isTimeUp()) {
                    break;
                }

                if (bestScoreThisIteration <= originalAlpha) {
                    alpha = Double.NEGATIVE_INFINITY;
                    continue;
                } else if (bestScoreThisIteration >= originalBeta) {
                    beta = Double.POSITIVE_INFINITY;
                    continue;
                }

                previousScore = bestScoreThisIteration;
                if (bestMoveThisIteration != -1) {
                    overallBestMove = bestMoveThisIteration;
                    completedDepth = currentDepth;
                }

                break;
            }

            if (isTimeUp()) break;
            
            long timeElapsed = System.currentTimeMillis() - startTime;
            if (timeElapsed > timeLimit / 2) {
                // If we've used half our time, we likely won't finish the next depth
                break;
            }
        }

        long elapsedMs = Math.max(1, System.currentTimeMillis() - startTime);
        lastNodesSearched = nodesSearched;
        lastDepthReached = completedDepth;
        lastElapsedMs = elapsedMs;
        lastNodesPerSecond = (nodesSearched * 1000L) / elapsedMs;
        
        return overallBestMove;
    }

    public static long perft(Board board, int depth) {
        if (depth == 0) return 1L;
        long nodes = 0L;
        ArrayList<Integer> moves = board.getLegalMoves(board.getStateTracker().getTurn());
        for (int move : moves) {
            board.makeMove(move);
            nodes += perft(board, depth - 1);
            board.unmakeMove(move);
        }
        return nodes;
    }

    public static void main(String[] args) {
        int depth = 4;
        if (args.length > 0) {
            try { depth = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        Board board = new Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
        System.out.println("FirstEngine perft depth " + depth);
        long start = System.currentTimeMillis();
        long nodes = perft(board, depth);
        System.out.println("Nodes: " + nodes + " time=" + (System.currentTimeMillis() - start) + "ms");
    }

}