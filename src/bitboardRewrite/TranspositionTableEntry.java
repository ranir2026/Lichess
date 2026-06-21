package bitboardRewrite;
public class TranspositionTableEntry {
    public long key;
    public double score;
    public int depth;
    public int type;
    public int bestMove;

    public static final int EXACT = 0;
    public static final int LOWER_BOUND = 1;
    public static final int UPPER_BOUND = 2;

    public TranspositionTableEntry(long key, double score, int depth, int type, int bestMove) {
        this.key = key;
        this.score = score;
        this.depth = depth;
        this.type = type;
        this.bestMove = bestMove;
    }
}