package core;
public class Move {
    // flags
    public static final int QUIET_MOVE     = 0b0000 << 12;
    public static final int CAPTURE        = 0b0001 << 12;
    public static final int DOUBLE_PAWN    = 0b0010 << 12;
    public static final int EN_PASSANT    = 0b0011 << 12;
    public static final int SHORT_CASTLE  = 0b0100 << 12;
    public static final int LONG_CASTLE   = 0b0101 << 12;
    public static final int PROMOTION_QUIET = 0b1000 << 12;
    public static final int PROMOTION_CAPTURE = 0b1001 << 12;

    public static int encode(int start, int end, int flags) {
        return start | (end << 6) | flags;
    }

    public static int getStart(int move) { return move & 0b0000000000111111; }
    public static int getEnd(int move) { return (move >> 6) & 0b0000000000111111; }
    public static int getFlags(int move) { return move & 0b1111000000000000; }
    
    public static boolean isCapture(int move) { 
        return (getFlags(move) & CAPTURE) != 0; 
    }
}