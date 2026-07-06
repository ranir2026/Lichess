import java.io.*;
import java.util.*;

/**
 * OpeningBook.java
 *
 * Loads one or more PGN files and builds a hash map from Zobrist position key
 * → candidate moves (stored as your packed int format).  At query time,
 * getBestBookMove() looks up the current board hash and picks a move weighted
 * by frequency (positions that appeared more often in the PGN get picked more).
 *
 * HOW TO USE
 * ----------
 * 1. Drop this file into your bitboardRewrite package.
 * 2. In TurquoiseBot (or wherever you call getBestMove), add a field:
 *
 *      private OpeningBook openingBook = new OpeningBook();
 *
 *    Then, at the very top of getBestMove(), before the search loop:
 *
 *      int bookMove = openingBook.getBestBookMove(board);
 *      if (bookMove != -1) return bookMove;
 *
 * 3. Load PGN files once (e.g. in main / UciInterface "ucinewgame"):
 *
 *      openingBook.loadPGN(new File("openings.pgn"));
 *
 *    Any number of PGN files can be loaded; entries accumulate.
 *
 * SQUARE INDEXING
 * ---------------
 * Your engine uses: square = rank * 8 + file,  a1=0, h1=7, a8=56, h8=63.
 * Moves are packed with Move.encode(from, to, flags).
 * This file follows that convention throughout.
 *
 * PGN SUPPORT
 * -----------
 * - Standard SAN notation (Nf3, exd5, O-O, O-O-O, e8=Q, a1=R, ...)
 * - Disambiguation by file, rank, or both (Rdf8, R1e2, Qd1f3)
 * - Captures (x), check (+), checkmate (#) — all stripped before parsing
 * - Comments ({...} and ;...) stripped
 * - NAG tokens ($1 etc.) skipped
 * - Multi-game PGN files fully supported
 * - Games that are cut off mid-way (all positions seen are still recorded)
 *
 * DEPTH LIMIT
 * -----------
 * Only the first MAX_BOOK_PLY half-moves of each game are added to the book.
 * Positions beyond that are almost certainly out of opening theory.
 */
public class OpeningBook {

    // Only index positions up to this many half-moves into a game.
    private static final int MAX_BOOK_PLY = 30;

    // book: Zobrist hash  →  list of (encoded move, weight) pairs.
    // Weight = number of times this move was seen from this position.
    private final HashMap<Long, int[]>  bookMoves   = new HashMap<>();
    private final HashMap<Long, int[]>  bookWeights = new HashMap<>();

    private final Random rng = new Random();
    private int totalPositions = 0;
    private int totalEntries   = 0;

    // ------------------------------------------------------------------
    //  Public API
    // ------------------------------------------------------------------

    /**
     * Load (and accumulate) all games from a PGN file into the book.
     * Safe to call multiple times with different files.
     */
    public void loadPGN(File file) throws IOException {
        List<String> moveTexts = extractGameMoveTexts(file);
        System.err.println("[Book] Parsing " + moveTexts.size() + " games from " + file.getName());

        for (String movesText : moveTexts) {
            processSingleGame(movesText);
        }

        System.err.println("[Book] Loaded. Positions: " + totalPositions
                         + "  Total entries: " + totalEntries);
    }

    /**
     * Convenience overload — accepts a path string.
     */
    public void loadPGN(String path) throws IOException {
        loadPGN(new File(path));
    }

    /**
     * If the current board position is in the book, return a weighted-random
     * candidate move (int-encoded, ready to pass to board.makeMove()).
     * Returns -1 if the position is not in the book.
     */
    public int getBestBookMove(Board board) {
        long hash = board.getCurrentHash();
        int[] moves   = bookMoves.get(hash);
        int[] weights = bookWeights.get(hash);
        if (moves == null || moves.length == 0) return -1;

        // weighted random: pick proportional to frequency
        int total = 0;
        for (int w : weights) total += w;
        int r = rng.nextInt(total);
        int cum = 0;
        for (int i = 0; i < moves.length; i++) {
            cum += weights[i];
            if (r < cum) return moves[i];
        }
        return moves[moves.length - 1]; // fallback (shouldn't reach)
    }

    /** How many distinct positions are in the book. */
    public int size() { return totalPositions; }

    // ------------------------------------------------------------------
    //  PGN parsing
    // ------------------------------------------------------------------

    /**
     * Read a PGN file and split it into per-game move strings (the part
     * after all the tag pairs, up to the result token).
     */
    private List<String> extractGameMoveTexts(File file) throws IOException {
        List<String> games = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inTags = false;
        boolean pastTags = false;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("[")) {
                    // tag pair line
                    if (pastTags && current.length() > 0) {
                        // new game starting — flush the previous one
                        games.add(current.toString());
                        current.setLength(0);
                    }
                    inTags  = true;
                    pastTags = false;
                } else {
                    if (inTags && !line.isEmpty()) {
                        pastTags = true;
                        inTags  = false;
                    }
                    if (pastTags) {
                        current.append(' ').append(line);
                    }
                }
            }
        }

        if (current.length() > 0) games.add(current.toString());
        return games;
    }

    /**
     * Given the move-text section of one PGN game, replay it on a fresh board
     * and record every position → move pair up to MAX_BOOK_PLY.
     */
    private void processSingleGame(String movesText) {
        // strip comments, result tokens, NAG, move numbers
        String cleaned = cleanMoveText(movesText);
        String[] tokens = cleaned.trim().split("\\s+");

        Board board = new Board();
        board.convertFENtoPosition(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        int ply = 0;
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (ply >= MAX_BOOK_PLY) break;

            int move = sanToMove(board, token);
            if (move == -1) {
                // unrecognised token — skip (result strings already removed)
                continue;
            }

            // record this position → move
            long hash = board.getCurrentHash();
            addEntry(hash, move);
            ply++;

            board.makeMove(move);
        }
    }

    /** Strip PGN noise: comments, result, NAG, move numbers, annotations. */
    private String cleanMoveText(String text) {
        // remove {...} comments
        text = text.replaceAll("\\{[^}]*\\}", " ");
        // remove ; ... end-of-line comments
        text = text.replaceAll(";[^\n]*", " ");
        // remove NAG tokens like $1
        text = text.replaceAll("\\$\\d+", " ");
        // remove move numbers like "1." or "1..." or "12."
        text = text.replaceAll("\\d+\\.+", " ");
        // remove result tokens
        text = text.replaceAll("1-0|0-1|1/2-1/2|\\*", " ");
        // collapse whitespace
        text = text.replaceAll("\\s+", " ");
        return text.trim();
    }

    /** Record one hash→move pair, incrementing its weight if already present. */
    private void addEntry(long hash, int move) {
        int[] moves   = bookMoves.get(hash);
        int[] weights = bookWeights.get(hash);

        if (moves == null) {
            bookMoves.put(hash,   new int[]{move});
            bookWeights.put(hash, new int[]{1});
            totalPositions++;
            totalEntries++;
            return;
        }

        // check if this exact move is already stored
        for (int i = 0; i < moves.length; i++) {
            if (moves[i] == move) {
                weights[i]++;
                totalEntries++;
                return;
            }
        }

        // new move for this position — grow arrays
        int n = moves.length;
        int[] newMoves   = Arrays.copyOf(moves,   n + 1);
        int[] newWeights = Arrays.copyOf(weights, n + 1);
        newMoves[n]   = move;
        newWeights[n] = 1;
        bookMoves.put(hash,   newMoves);
        bookWeights.put(hash, newWeights);
        totalEntries++;
    }

    // ------------------------------------------------------------------
    //  SAN → internal move conversion
    // ------------------------------------------------------------------

    /**
     * Convert a SAN token (e.g. "Nf3", "exd5", "O-O", "e8=Q") to your
     * packed int move format, given the current board state.
     * Returns -1 if the move cannot be matched.
     *
     * Square indexing reminder: file a=0..h=7, rank 1=0..8=7
     *   square = rank * 8 + file
     */
    private int sanToMove(Board board, String san) {
        // strip check / checkmate / annotation symbols
        san = san.replaceAll("[+#!?]", "").trim();
        if (san.isEmpty()) return -1;

        // castling
        if (san.equals("O-O-O") || san.equals("0-0-0")) {
            return findCastleMove(board, false);
        }
        if (san.equals("O-O") || san.equals("0-0")) {
            return findCastleMove(board, true);
        }

        // generate all legal moves to search against
        int[] legalMoves = new int[MoveGenerator.MAX_MOVES];
        int count = MoveGenerator.generateLegalMoves(board, legalMoves);

        // detect piece type (uppercase = piece, lowercase start = pawn)
        char first = san.charAt(0);
        int pieceType; // 0=P,1=N,2=B,3=R,4=Q,5=K  (matches Board.WP%6 etc.)

        String rest; // remainder after stripping piece letter
        if (Character.isUpperCase(first) && first != 'O') {
            pieceType = sanPieceLetter(first);
            rest = san.substring(1);
        } else {
            pieceType = 0; // pawn
            rest = san;
        }

        // parse promotion (e.g. "=Q" or just "Q" at the end)
        int promoPiece = -1; // 1=N,2=B,3=R,4=Q  (piece type index % 6)
        int eqIdx = rest.indexOf('=');
        if (eqIdx != -1) {
            promoPiece = sanPieceLetter(rest.charAt(eqIdx + 1));
            rest = rest.substring(0, eqIdx);
        } else if (rest.length() > 0 && Character.isUpperCase(rest.charAt(rest.length() - 1))
                   && pieceType == 0 && rest.length() >= 2) {
            // handle "e8Q" style (no equals sign)
            char last = rest.charAt(rest.length() - 1);
            if (last == 'N' || last == 'B' || last == 'R' || last == 'Q') {
                promoPiece = sanPieceLetter(last);
                rest = rest.substring(0, rest.length() - 1);
            }
        }

        // strip 'x' capture indicator
        rest = rest.replace("x", "");

        // now rest is one of:
        //   "e4"       → destination only (no disambiguation)
        //   "de4"      → file disambiguation + destination
        //   "1e4"      → rank disambiguation + destination  (rare for pawns)
        //   "d1f3"     → full from-square disambiguation
        // The destination square is always the last two characters.

        if (rest.length() < 2) return -1;

        String destStr = rest.substring(rest.length() - 2);
        String disambig = rest.substring(0, rest.length() - 2);

        int destFile = destStr.charAt(0) - 'a';
        int destRank = destStr.charAt(1) - '1';
        if (destFile < 0 || destFile > 7 || destRank < 0 || destRank > 7) return -1;
        int toSq = destRank * 8 + destFile;

        // disambiguation fields (-1 = not specified)
        int fromFile = -1, fromRank = -1, fromSq = -1;
        if (disambig.length() == 2) {
            // full square given
            fromFile = disambig.charAt(0) - 'a';
            fromRank = disambig.charAt(1) - '1';
            fromSq   = fromRank * 8 + fromFile;
        } else if (disambig.length() == 1) {
            char d = disambig.charAt(0);
            if (Character.isDigit(d)) fromRank = d - '1';
            else                      fromFile = d - 'a';
        }

        // match against legal moves
        for (int i = 0; i < count; i++) {
            int mv = legalMoves[i];
            int from = Move.getStart(mv);
            int to   = Move.getEnd(mv);

            if (to != toSq) continue;

            // piece type check: moving piece must match
            int movingPiece = board.boardArray[from];
            if (movingPiece == -1) continue;
            if (movingPiece % 6 != pieceType) continue;

            // disambiguation checks
            if (fromSq   != -1 && from != fromSq)         continue;
            if (fromFile  != -1 && (from % 8) != fromFile) continue;
            if (fromRank  != -1 && (from / 8) != fromRank) continue;

            // promotion check
            if (Move.isPromotion(mv)) {
                if (promoPiece == -1) {
                    // no promotion specified in SAN — default to queen
                    int flags = Move.getFlags(mv);
                    if (flags != Move.PROMOTION_QUEEN && flags != Move.PROMO_CAP_QUEEN) continue;
                } else {
                    int flags = Move.getFlags(mv);
                    int fp = promoFlagPiece(flags);
                    if (fp != promoPiece) continue;
                }
            } else if (promoPiece != -1) {
                continue; // SAN demands promotion but this move isn't one
            }

            return mv;
        }

        return -1; // no match
    }

    /** Map SAN piece letter to piece-type index (same as Board.WP % 6 etc.). */
    private int sanPieceLetter(char c) {
        switch (c) {
            case 'N': return 1;
            case 'B': return 2;
            case 'R': return 3;
            case 'Q': return 4;
            case 'K': return 5;
            default:  return 0; // P or unknown
        }
    }

    /**
     * Extract the promoted piece type (1-4) from a promotion move flag.
     * Returns -1 for non-promotion flags.
     */
    private int promoFlagPiece(int flags) {
        if (flags == Move.PROMOTION_KNIGHT || flags == Move.PROMO_CAP_KNIGHT) return 1;
        if (flags == Move.PROMOTION_BISHOP || flags == Move.PROMO_CAP_BISHOP) return 2;
        if (flags == Move.PROMOTION_ROOK   || flags == Move.PROMO_CAP_ROOK)   return 3;
        if (flags == Move.PROMOTION_QUEEN  || flags == Move.PROMO_CAP_QUEEN)  return 4;
        return -1;
    }

    /** Find a short-castle (kingside=true) or long-castle move in legal moves. */
    private int findCastleMove(Board board, boolean kingside) {
        int[] moves = new int[MoveGenerator.MAX_MOVES];
        int count = MoveGenerator.generateLegalMoves(board, moves);
        int wanted = kingside ? Move.SHORT_CASTLE : Move.LONG_CASTLE;
        for (int i = 0; i < count; i++) {
            if (Move.getFlags(moves[i]) == wanted) return moves[i];
        }
        return -1;
    }
}