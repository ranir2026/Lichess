package core;
import java.util.Random;

import pieces.Bishop;
import pieces.Knight;
import pieces.Pawn;
import pieces.Piece;
import pieces.Queen;
import pieces.Rook;

public class Zobrist {
    // [type][square]
    // 0-5 for White (P, N, B, R, Q, K), 6-11 for Black
    public static final long[][] table = new long[12][64];
    public static final long sideToMove;
    public static final long[] enPassantFile = new long[8];
    public static final long[] castlingRights = new long[16];

    static {
        Random rnd = new Random(12345);
        for (int i = 0; i < 12; i++) {
            for (int j = 0; j < 64; j++) {
                table[i][j] = rnd.nextLong();
            }
        }

        for (int i = 0; i < 8; i++) {
            enPassantFile[i] = rnd.nextLong();
        }

        for (int i = 0; i < 16; i++) {
            castlingRights[i] = rnd.nextLong();
        }

        sideToMove = rnd.nextLong();
    }

    public static int getPieceIndex(Piece p) {
        int offset = p.getColor() ? 0 : 6;
        if (p instanceof Pawn) return offset + 0;
        if (p instanceof Knight) return offset + 1;
        if (p instanceof Bishop) return offset + 2;
        if (p instanceof Rook) return offset + 3;
        if (p instanceof Queen) return offset + 4;
        return offset + 5; // King
    }
}
