package bitboardRewrite;

import java.io.*;

public class AttackTables {
    public static final long NOT_A_FILE = 0xfefefefefefefefeL; // 0s on the A-file, 1s everywhere else
    public static final long NOT_H_FILE = 0x7f7f7f7f7f7f7f7fL; // 0s on the H-file, 1s everywhere else
    public static final long NOT_AB_FILE = 0xfcfcfcfcfcfcfcfcL; // 0s on the A & B files, 1s everywhere else
    public static final long NOT_GH_FILE = 0x3f3f3f3f3f3f3f3fL; // 0s on the G&H files, 1s everywhere else

    public static long[] knightAttacks = new long[64];
    public static long[] kingAttacks = new long[64];
    public static long[][] pawnAttacks = new long[2][64]; // [color][square]

    public static long[] rookMasks = new long[64];
    public static long[] rookMagics = new long[64];
    public static int[] rookShifts = new int[64];
    public static long[][] rookAttacks = new long[64][];

    public static long[] bishopMasks = new long[64];
    public static long[] bishopMagics = new long[64];
    public static int[] bishopShifts = new int[64];
    public static long[][] bishopAttacks = new long[64][];

    public static final int WHITE = 0;
    public static final int BLACK = 1;

    // try to load the precomputed data from the attack files
    static {
        try {
            boolean loaded = loadPrecomputed();
            if (!loaded) {
                throw new IllegalStateException("Precomputed attack tables not found.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load precomputed attack tables", e);
        }
    }

    // generating magic bitboard tables from scratch can take a long time on startup
    // precomputing the data and saving it in a file makes it nearly instantaneous
    public static boolean loadPrecomputed() throws java.io.IOException {
        // 1) try classpath resource in same package
        java.io.InputStream ris = AttackTables.class.getResourceAsStream("/bitboardRewrite/precomputed_attacks.bin");
        if (ris == null) ris = AttackTables.class.getResourceAsStream("precomputed_attacks.bin");
        if (ris != null) {
            try (java.io.InputStream is = ris) {
                loadFromStream(is);
                return true;
            }
        }

        // 2) try working-dir file
        java.io.File f = new java.io.File("precomputed_attacks.bin");
        if (f.exists()) {
            try (java.io.InputStream is = new java.io.FileInputStream(f)) {
                loadFromStream(is);
                return true;
            }
        }

        // 3) try project source path (useful during development)
        java.io.File src = new java.io.File("src/bitboardRewrite/precomputed_attacks.bin");
        if (src.exists()) {
            try (java.io.InputStream is = new java.io.FileInputStream(src)) {
                loadFromStream(is);
                return true;
            }
        }

        return false;
    }

    private static void loadFromStream(java.io.InputStream inStream) throws java.io.IOException {
        // process the raw bytes from the file and convert them into 64-bit long arrays
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(inStream))) {
            String header = in.readUTF();
            if (!"ATTACKTABLESv1".equals(header)) throw new java.io.IOException("Invalid attack tables header");

            for (int i = 0; i < 64; i++) knightAttacks[i] = in.readLong();
            for (int i = 0; i < 64; i++) kingAttacks[i] = in.readLong();
            for (int c = 0; c < 2; c++) for (int i = 0; i < 64; i++) pawnAttacks[c][i] = in.readLong();

            // Ensure pawn attack masks are correct for the current implementation.
            // Some development workflows rely on the precomputed binary which may
            // have been generated with an older/incorrect masking order. Recompute
            // pawn attacks here from the local logic to avoid subtle wrap-around bugs.
            for (int c = 0; c < 2; c++) {
                for (int i = 0; i < 64; i++) {
                    pawnAttacks[c][i] = computePawnAttacks(i, c);
                }
            }

            for (int i=0; i<64; i++) kingAttacks[i] = computeKingAttacks(i);

            for (int i = 0; i < 64; i++) rookMasks[i] = in.readLong();
            for (int i = 0; i < 64; i++) rookMagics[i] = in.readLong();
            for (int i = 0; i < 64; i++) rookShifts[i] = in.readInt();

            for (int s = 0; s < 64; s++) {
                int len = in.readInt();
                rookAttacks[s] = new long[len];
                for (int j = 0; j < len; j++) rookAttacks[s][j] = in.readLong();
            }

            for (int i = 0; i < 64; i++) bishopMasks[i] = in.readLong();
            for (int i = 0; i < 64; i++) bishopMagics[i] = in.readLong();
            for (int i = 0; i < 64; i++) bishopShifts[i] = in.readInt();

            for (int s = 0; s < 64; s++) {
                int len = in.readInt();
                bishopAttacks[s] = new long[len];
                for (int j = 0; j < len; j++) bishopAttacks[s][j] = in.readLong();
            }
        }
    }

    public static long getRookAttacks(int square, long occupancy) {
        // isolate only the pieces that are relevant to this rook's rays
        // then, magic bitboards optimize sliding piece attack generaition
        // by using a bitwise AND to isolate only the blocking pieces on a
        // Rook or Bishop's line of fire.
        // Then, this is multiplied by a preconfigured magic number that
        // concentrates blocker bits into the highest positions of the
        // integer. A right shift moves these bits back down to serve as
        // a direct array index.
        occupancy &= rookMasks[square];
        occupancy *= rookMagics[square];
        occupancy >>>= rookShifts[square];

        int idx = (int)occupancy;
        long[] table = rookAttacks[square];
        int len = table.length;
        if (idx < 0 || idx >= len) {
            // attempt fast power-of-two mask if applicable
            int mask = 1;
            while (mask < len) mask <<= 1;
            if (mask == len) {
                idx = idx & (len - 1);
            } else {
                idx = Math.floorMod(idx, len);
            }
        }

        return table[idx];
    }

    public static long getBishopAttacks(int square, long occupancy) {
        occupancy &= bishopMasks[square];
        occupancy *= bishopMagics[square];
        occupancy >>>= bishopShifts[square];

        int idx = (int)occupancy;
        long[] btable = bishopAttacks[square];
        int blen = btable.length;
        if (idx < 0 || idx >= blen) {
            int mask = 1;
            while (mask < blen) mask <<= 1;
            if (mask == blen) {
                idx = idx & (blen - 1);
            } else {
                idx = Math.floorMod(idx, blen);
            }
        }

        return btable[idx];
    }

    public static long getQueenAttacks(int square, long occupancy) {
        // queens function as a rook + bishop in the same piece
        return getRookAttacks(square, occupancy)
            | getBishopAttacks(square, occupancy);
    }


    // private static long computeKnightAttacks(int square) {
    //     long attacks = 0L;
    //     long knight = 1L << square;

    //     if (((knight >>> 17) & NOT_H_FILE) != 0) attacks |= (knight >>> 17);
    //     if (((knight >>> 15) & NOT_A_FILE) != 0) attacks |= (knight >>> 15);
    //     if (((knight >>> 10) & NOT_GH_FILE) != 0) attacks |= (knight >>> 10);
    //     if (((knight >>> 6) & NOT_AB_FILE) != 0) attacks |= (knight >>> 6);
    //     if (((knight << 17) & NOT_A_FILE) != 0) attacks |= (knight << 17);
    //     if (((knight << 15) & NOT_H_FILE) != 0) attacks |= (knight << 15);
    //     if (((knight << 10) & NOT_AB_FILE) != 0) attacks |= (knight << 10);
    //     if (((knight << 6) & NOT_GH_FILE) != 0) attacks |= (knight << 6);

    //     return attacks;
    // }

    private static long computeKingAttacks(int square) {
        long attacks = 0L;
        long king = 1L << square;

        attacks |= (king >>> 8);
        attacks |= (king << 8);
        attacks |= (king << 1) & NOT_A_FILE;
        attacks |= (king >>> 1) & NOT_H_FILE;

        long kingNotA = king & NOT_A_FILE;
        long kingNotH = king & NOT_H_FILE;

        attacks |= (kingNotA >>> 9);
        attacks |= (kingNotH >>> 7);
        attacks |= (kingNotA << 7);
        attacks |= (kingNotH << 9);

        return attacks;
    }

    private static long computePawnAttacks(int square, int color) {
        long attacks = 0L;
        long pawn = 1L << square;

        // make sure you don't accidentally wrap around the board
        if (color == WHITE) {
            attacks |= (pawn << 7) & NOT_H_FILE;
            attacks |= (pawn << 9) & NOT_A_FILE;
        } else {
            attacks |= (pawn >>> 7) & NOT_A_FILE;
            attacks |= (pawn >>> 9) & NOT_H_FILE;
        }

        return attacks;
    }
    
}