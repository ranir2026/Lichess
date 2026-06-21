package bitboardRewrite;

public class Bitboards {
    public static long setBit(long board, int square) {
        return board | (1L << square);
    }

    public static long clearBit(long board, int square) {
        return board & ~(1L << square);
    }

    public static boolean getBit(long board, int square) {
        return (board & (1L << square)) != 0;
    }

    public static int countBits(long b) {
        return Long.bitCount(b);
    }

    public static void printBitboard(long board) {
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                int square = rank * 8 + file;

                if ((board & (1L << square)) != 0)
                    System.out.print("1 ");
                else
                    System.out.print(". ");
            }

            System.out.println();
        }

        System.out.println();
    }
}
