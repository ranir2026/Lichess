package core;
import javax.swing.*;

import engines.FirstEngine;
import pieces.Piece;

import java.awt.event.MouseEvent;
import java.awt.*;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

class ShapeDrawing extends JComponent implements MouseListener, MouseMotionListener {
    private final int windowHeight = 800;
    private BufferedImage spriteSheet;
    private int displaySize = windowHeight / 8;
    
    private Board game;
    private FirstEngine bot;
    private boolean isBotThinking = false;

    // drag n drop functinos
    private Piece draggedPiece = null;
    private int sourceIndex = -1;
    private int mouseX = 0;
    private int mouseY = 0;

    private java.util.ArrayList<Integer> currentLegalMoves = new java.util.ArrayList<>();

    public ShapeDrawing(Board game, FirstEngine bot) {
        this.game = game;
        this.bot = bot;
        try {
            spriteSheet = ImageIO.read(new File("lib/pieces.png"));
        } catch (IOException e) {
            System.err.println("dsgergsges");
        }

        addMouseListener(this);
        addMouseMotionListener(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        
        int size = windowHeight / 8;
        Color darkSquare = new Color(181, 136, 99);
        Color lightSquare = new Color(240, 217, 181);

        Piece[] pieces = game.getGameBoard();

        for (int i = 0; i < 64; i++) {
            int row = i / 8;
            int col = i % 8;

            g2.setColor((row + col) % 2 == 1 ? darkSquare : lightSquare);
            g2.fillRect(col * size, row * size, size, size);

            // draw piece
            Piece p = pieces[i];
            if (p != null && i != sourceIndex) {
                renderPieceAtGrid(g2, p, col, row);
            }
        }

        if (draggedPiece != null) {
            renderPieceAtPixels(g2, draggedPiece, mouseX - (displaySize/2), mouseY - (displaySize/2));
        }

        // draw dots to represent legal moves
        for (int targetSq : currentLegalMoves) {
            int row = targetSq / 8;
            int col = targetSq % 8;
            
            g2.setColor(new Color(0, 0, 0, 50)); 
            
            int dotSize = size / 4;
            g2.fillOval(col * size + (size/2 - dotSize/2), row * size + (size/2 - dotSize/2), dotSize, dotSize);
        }
    }

    private void renderPieceAtGrid(Graphics2D g2, Piece p, int col, int row) {
        int size = windowHeight / 8;
        renderPieceAtPixels(g2, p, col * size, row * size);
    }

    private void renderPieceAtPixels(Graphics2D g2, Piece p, int x, int y) {
        int tileSize = 320; // i got this number by dividing the dimensions of the picture

        int sheetRow = p.getColor() ? 0 : 1; // white pieces on first row, black pieces on second row of picture
        int sheetCol = 0;

        switch (p.getSymbol().toLowerCase()) {
            case "k": sheetCol = 0; break;
            case "q": sheetCol = 1; break;
            case "b": sheetCol = 2; break;
            case "n": sheetCol = 3; break;
            case "r": sheetCol = 4; break;
            case "p": sheetCol = 5; break;
        }

        Image sprite = spriteSheet.getSubimage(
            sheetCol * tileSize, 
            sheetRow * tileSize, 
            tileSize, 
            tileSize
        );

        // offset by half of display size to center image on cursor
        g2.drawImage(sprite, x, y, displaySize, displaySize, null);
    }

    

    @Override
    public void mousePressed(MouseEvent e) {
        if (isBotThinking) return; // to prevent funky stuff from happening while bot is thinking

        int size = windowHeight / 8;
        int col = e.getX() / size;
        int row = e.getY() / size;
        int index = row * 8 + col;

        if (index >= 0 && index < 64) {
            Piece p = game.getGameBoard()[index];
            if (p != null) {
                draggedPiece = p;
                sourceIndex = index;
                mouseX = e.getX();
                mouseY = e.getY();

                currentLegalMoves.clear();
                java.util.ArrayList<Integer> allMoves = game.getLegalMoves(p.getColor());
                for (int move : allMoves) {
                    if (Move.getStart(move) == sourceIndex) {
                        currentLegalMoves.add(Move.getEnd(move)); // Store only the destination
                    }
                }

                repaint();
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (isBotThinking) return; // to prevent funky stuff from happening while bot is thinking

        if (draggedPiece != null) {
            mouseX = e.getX();
            mouseY = e.getY();
            repaint(); // redraws as the mouse moves
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (draggedPiece != null) {
            int size = windowHeight / 8;
            int col = e.getX() / size;
            int row = e.getY() / size;
            int targetIndex = row * 8 + col;

            // ensure ur dropping it on the board
            if (targetIndex >= 0 && targetIndex < 64) {
                // get legal moves for current player
                java.util.ArrayList<Integer> legalMoves = game.getLegalMoves(game.getStateTracker().getTurn());
                
                for (int move : legalMoves) {
                    // check it's legal
                    if (Move.getStart(move) == sourceIndex && Move.getEnd(move) == targetIndex) {
                        game.makeMove(move);

                        if (game.getStateTracker().getTurn() == bot.getColor()) {
                            makeBotMove(bot);
                        }

                        break;
                    }
                }
            }

            // reset drag state + refresh ui
            draggedPiece = null;
            sourceIndex = -1;
            currentLegalMoves.clear();
            repaint(); 
        }
    }

    @Override public void mouseMoved(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}    
    @Override public void mouseClicked(MouseEvent e) {}

    private void makeBotMove(FirstEngine bot) {
        // We use a separate thread so the bot can think without freezing the GUI
        isBotThinking = true;
        new Thread(() -> {
            try {
                Thread.sleep(100); 
                

                int botMove = bot.getBestMove(3 * 60 * 1000, 0);
                game.makeMove(botMove);
                isBotThinking = false;
                
                SwingUtilities.invokeLater(() -> repaint()); // threads is gemini
                
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("KhafaChess");
        frame.setSize(1000, 850);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        Board game = new Board("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
        // r2qkb1r/ppp1pppp/2n5/3n4/Q2P2b1/8/PP2PPPP/RNB1KBNR
        FirstEngine bot = new FirstEngine(game, false);
        System.out.println(Long.toHexString(game.calculateManualHash()));
        System.out.println(Long.toHexString(game.getCurrentHash()));
        
        frame.getContentPane().add(new ShapeDrawing(game, bot));
        frame.setVisible(true);
    }
}