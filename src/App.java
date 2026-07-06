import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class App {

    static class BoardPanel extends JComponent implements MouseListener, MouseMotionListener {
        private final int windowHeight = 800;
        private BufferedImage spriteSheet;
        private int displaySize = windowHeight / 8;

        private Board board;
        private volatile boolean isBotThinking = false;

        // drag state
        private int draggedPiece = -1;
        private int sourceIndex = -1;
        private int mouseX = 0;
        private int mouseY = 0;

        private ArrayList<Integer> currentLegalMoves = new ArrayList<>();

        public BoardPanel(Board board) {
            this.board = board;
            try {
                spriteSheet = ImageIO.read(new File("lib/pieces.png"));
            } catch (IOException e) {
                System.err.println("Failed to load piece sprites: " + e.getMessage());
            }

            addMouseListener(this);
            addMouseMotionListener(this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            int size = windowHeight / 8;
            Color lightSquare = new Color(181, 136, 99);
            Color darkSquare = new Color(240, 217, 181);

            for (int i = 0; i < 64; i++) {
                int boardRow = i / 8;
                int boardCol = i % 8;
                int displayRow = 7 - boardRow; // flip vertically so white is at bottom
                int displayCol = boardCol;

                g2.setColor((boardRow + boardCol) % 2 == 1 ? darkSquare : lightSquare);
                g2.fillRect(displayCol * size, displayRow * size, size, size);

                int piece = board.boardArray[i];
                if (piece != -1 && i != sourceIndex) {
                    renderPieceAtGrid(g2, piece, displayCol, displayRow);
                }
            }

            if (draggedPiece != -1) {
                renderPieceAtPixels(g2, draggedPiece, mouseX - (displaySize / 2), mouseY - (displaySize / 2));
            }

            // draw dots for legal moves
            for (int targetSq : currentLegalMoves) {
                int boardRow = targetSq / 8;
                int boardCol = targetSq % 8;
                int displayRow = 7 - boardRow;
                int displayCol = boardCol;

                g2.setColor(new Color(0, 0, 0, 50));
                int dotSize = size / 4;
                g2.fillOval(displayCol * size + (size / 2 - dotSize / 2), displayRow * size + (size / 2 - dotSize / 2), dotSize, dotSize);
            }
        }

        private void renderPieceAtGrid(Graphics2D g2, int pieceIdx, int col, int row) {
            int size = windowHeight / 8;
            renderPieceAtPixels(g2, pieceIdx, col * size, row * size);
        }

        private void renderPieceAtPixels(Graphics2D g2, int pieceIdx, int x, int y) {
            if (spriteSheet == null) return;
            int tileSize = 320;

            int sheetRow = (pieceIdx < 6) ? 0 : 1;
            int kind = pieceIdx % 6;
            int sheetCol;
            switch (kind) {
                case 5: sheetCol = 0; break; // king
                case 4: sheetCol = 1; break; // queen
                case 2: sheetCol = 2; break; // bishop
                case 1: sheetCol = 3; break; // knight
                case 3: sheetCol = 4; break; // rook
                default: sheetCol = 5; break; // pawn
            }

            Image sprite = spriteSheet.getSubimage(sheetCol * tileSize, sheetRow * tileSize, tileSize, tileSize);
            g2.drawImage(sprite, x, y, displaySize, displaySize, null);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (isBotThinking) return;

            int size = windowHeight / 8;
            int col = e.getX() / size;
            int row = e.getY() / size;
            int boardRow = 7 - row;
            int index = boardRow * 8 + col;

            if (index >= 0 && index < 64) {
                int p = board.boardArray[index];
                if (p != -1) {
                    draggedPiece = p;
                    sourceIndex = index;
                    mouseX = e.getX();
                    mouseY = e.getY();

                    currentLegalMoves.clear();
                    int[] moves = new int[MoveGenerator.MAX_MOVES];
                    int n = MoveGenerator.generateLegalMoves(board, moves);
                    for (int i = 0; i < n; i++) {
                        int mv = moves[i];
                        if (Move.getStart(mv) == sourceIndex) currentLegalMoves.add(Move.getEnd(mv));
                    }

                    repaint();
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (isBotThinking) return;
            if (draggedPiece != -1) {
                mouseX = e.getX();
                mouseY = e.getY();
                repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (draggedPiece != -1) {
                int size = windowHeight / 8;
                int col = e.getX() / size;
                int row = e.getY() / size;
                int boardRow = 7 - row;
                int targetIndex = boardRow * 8 + col;

                if (targetIndex >= 0 && targetIndex < 64) {
                    int[] moves = new int[MoveGenerator.MAX_MOVES];
                    int n = MoveGenerator.generateLegalMoves(board, moves);

                    int chosen = -1;
                    for (int i = 0; i < n; i++) {
                        int mv = moves[i];
                        if (Move.getStart(mv) == sourceIndex && Move.getEnd(mv) == targetIndex) {
                            if (chosen == -1) chosen = mv;
                            // prefer queen promotions when multiple promotion options exist
                            if (Move.isPromotion(mv)) {
                                int flags = Move.getFlags(mv);
                                if (flags == Move.PROMOTION_QUEEN || flags == Move.PROMO_CAP_QUEEN) { chosen = mv; break; }
                            }
                        }
                    }

                    if (chosen != -1) {
                        board.makeMove(chosen);
                        repaint();

                        // if opponent is the engine, make bot move
                        new Thread(() -> {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ex) {}

                            if (board != null) {
                                isBotThinking = true;
                                Engine engine = new TurquoiseBot(board);
                                engine.loadOpeningBook("openings.pgn");
                                int botMove = engine.getBestMove(10000, 0);
                                if (botMove != -1) {
                                    board.makeMove(botMove);
                                }
                                isBotThinking = false;
                                SwingUtilities.invokeLater(() -> repaint());
                            }
                        }).start();
                    }
                }

                // reset drag state
                draggedPiece = -1;
                sourceIndex = -1;
                currentLegalMoves.clear();
                repaint();
            }
        }

        @Override public void mouseMoved(MouseEvent e) {}
        @Override public void mouseEntered(MouseEvent e) {}
        @Override public void mouseExited(MouseEvent e) {}
        @Override public void mouseClicked(MouseEvent e) {}

    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("BitboardRewrite - Play Bot");
            frame.setSize(1000, 850);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            Board board = new Board();
            board.convertFENtoPosition("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
            // board.convertFENtoPosition("3k4/Q7/8/8/8/3K4/8/8 w - - 0 1");

            BoardPanel panel = new BoardPanel(board);
            frame.getContentPane().add(panel);
            frame.setVisible(true);
        });
    }
}
