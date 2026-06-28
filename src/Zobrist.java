import java.util.Random;

public class Zobrist {
    public static final long[][] pieceKeys = new long[12][64];
    public static final long sideToMove;
    public static final long[] castlingKeys = new long[16];
    public static final long[] enPassantKeys = new long[8];

    static {
        Random rnd = new Random(12345);
        for (int i = 0; i < 12; i++) {
            for (int j = 0; j < 64; j++) pieceKeys[i][j] = rnd.nextLong();
        }
        for (int i = 0; i < 16; i++) castlingKeys[i] = rnd.nextLong();
        for (int i = 0; i < 8; i++) enPassantKeys[i] = rnd.nextLong();
        sideToMove = rnd.nextLong();
    }
}