public class Move {
    // flags
    public static final int QUIET_MOVE        = 0b0000 << 12;
    public static final int DOUBLE_PAWN       = 0b0001 << 12;
    public static final int SHORT_CASTLE      = 0b0010 << 12;
    public static final int LONG_CASTLE       = 0b0011 << 12;
    public static final int CAPTURE           = 0b0100 << 12;
    public static final int EN_PASSANT        = 0b0101 << 12;
    
    public static final int PROMOTION_KNIGHT  = 0b1000 << 12;
    public static final int PROMOTION_BISHOP  = 0b1001 << 12;
    public static final int PROMOTION_ROOK    = 0b1010 << 12;
    public static final int PROMOTION_QUEEN   = 0b1011 << 12;

    public static final int PROMO_CAP_KNIGHT  = 0b1100 << 12;
    public static final int PROMO_CAP_BISHOP  = 0b1101 << 12;
    public static final int PROMO_CAP_ROOK    = 0b1110 << 12;
    public static final int PROMO_CAP_QUEEN   = 0b1111 << 12;

    public static int encode(int start, int end, int flags) {
        return start | (end << 6) | flags;
    }

    public static int getStart(int move) { return move & 0b0000000000111111; }
    public static int getEnd(int move) { return (move >> 6) & 0b0000000000111111; }
    public static int getFlags(int move) { return move & 0b1111000000000000; }
    
    public static boolean isCapture(int move) { 
        int flags = getFlags(move);
        return flags == CAPTURE || flags == EN_PASSANT || flags == PROMO_CAP_BISHOP || flags == PROMO_CAP_KNIGHT || flags == PROMO_CAP_QUEEN || flags == PROMO_CAP_ROOK; 
    }

    public static boolean isPromotion(int move) {
        int flags = getFlags(move);
        return flags == PROMOTION_BISHOP || flags == PROMOTION_KNIGHT || flags == PROMOTION_QUEEN || flags == PROMOTION_ROOK
            || flags == PROMO_CAP_BISHOP || flags == PROMO_CAP_KNIGHT || flags == PROMO_CAP_QUEEN || flags == PROMO_CAP_ROOK;
    }
}