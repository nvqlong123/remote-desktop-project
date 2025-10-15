package client;

import shared.Protocol;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class RemoteControlFrame extends JFrame {
    private final Socket sock;
    private PrintWriter commandWriter;
    private final ScreenPanel screenPanel; // Thay thế JLabel bằng ScreenPanel tùy chỉnh

    // Các biến để tính toán tọa độ chuột chính xác
    private double scaleRatio = 1.0;
    private int scaledImageWidth, scaledImageHeight;
    private int offsetX = 0, offsetY = 0;

    private long lastMouseMoveTime = 0;
    private static final int MOUSE_MOVE_INTERVAL = 16; // ms

    // LỚP NỘI TÙY CHỈNH ĐỂ HIỂN THỊ MÀN HÌNH
    private class ScreenPanel extends JPanel {
        private BufferedImage screenImage;

        public void setScreenImage(BufferedImage image) {
            this.screenImage = image;
            this.repaint(); // Yêu cầu vẽ lại panel với ảnh mới
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); // Rất quan trọng: Xóa sạch nội dung cũ
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, getWidth(), getHeight()); // Vẽ nền đen

            if (screenImage != null) {
                // Tính toán kích thước và vị trí để căn giữa ảnh
                int panelWidth = getWidth();
                int panelHeight = getHeight();

                double scaleX = (double) panelWidth / screenImage.getWidth();
                double scaleY = (double) panelHeight / screenImage.getHeight();
                scaleRatio = Math.min(scaleX, scaleY);
                if (scaleRatio > 1.0) scaleRatio = 1.0; // Không phóng to ảnh

                scaledImageWidth = (int) (screenImage.getWidth() * scaleRatio);
                scaledImageHeight = (int) (screenImage.getHeight() * scaleRatio);

                // Tính toán khoảng trống (viền đen)
                offsetX = (panelWidth - scaledImageWidth) / 2;
                offsetY = (panelHeight - scaledImageHeight) / 2;

                // Sử dụng Graphics2D để có nhiều tùy chọn render hơn
                Graphics2D g2d = (Graphics2D) g;
                // Bật gợi ý để ảnh khi scale trông mượt hơn
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                // Vẽ ảnh đã scale vào đúng vị trí
                g2d.drawImage(screenImage, offsetX, offsetY, scaledImageWidth, scaledImageHeight, null);
            }
        }
    }

    public RemoteControlFrame(Socket socket) {
        super("Remote Control (Viewing Host Screen)");
        this.sock = socket;
        this.screenPanel = new ScreenPanel();
        setupUI();

        try {
            this.commandWriter = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            handleConnectionError("Failed to create command stream", e);
            return;
        }

        addEventHandlers();
        new Thread(this::readScreenStream).start();
    }

    private void setupUI() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setSize((int) (screenSize.width * 0.8), (int) (screenSize.height * 0.8));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Chỉ cần thêm screenPanel là đủ
        add(screenPanel, BorderLayout.CENTER);
        setLocationRelativeTo(null);
    }

    private void addEventHandlers() {
        screenPanel.setFocusable(true);

        screenPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { sendMouseCommand(Protocol.MOUSE_PRESS, e); }
            @Override
            public void mouseReleased(MouseEvent e) { sendMouseCommand(Protocol.MOUSE_RELEASE, e); }
        });

        screenPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                long now = System.currentTimeMillis();
                if (now - lastMouseMoveTime > MOUSE_MOVE_INTERVAL) {
                    sendMouseCommand(Protocol.MOUSE_MOVE, e);
                    lastMouseMoveTime = now;
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                long now = System.currentTimeMillis();
                if (now - lastMouseMoveTime > MOUSE_MOVE_INTERVAL) {
                    sendMouseCommand(Protocol.MOUSE_MOVE, e);
                    lastMouseMoveTime = now;
                }
            }
        });

        screenPanel.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { sendCommand(Protocol.KEY_PRESS + Protocol.SEPARATOR + e.getKeyCode()); }
            @Override public void keyReleased(KeyEvent e) { sendCommand(Protocol.KEY_RELEASE + Protocol.SEPARATOR + e.getKeyCode()); }
        });

        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowGainedFocus(WindowEvent e) { screenPanel.requestFocusInWindow(); }
        });
    }

    // Hàm gửi lệnh chuột đã được viết lại
    private void sendMouseCommand(String commandType, MouseEvent e) {
        if (scaleRatio == 0) return;

        // Trừ đi offset của viền đen để lấy tọa độ chính xác trên ảnh
        int mouseOnImageX = e.getX() - offsetX;
        int mouseOnImageY = e.getY() - offsetY;

        // Bỏ qua nếu click ra ngoài vùng ảnh (vào viền đen)
        if (mouseOnImageX < 0 || mouseOnImageX >= scaledImageWidth || mouseOnImageY < 0 || mouseOnImageY >= scaledImageHeight) {
            return;
        }int hostX = (int) (mouseOnImageX / scaleRatio);
        int hostY = (int) (mouseOnImageY / scaleRatio);

        String command;
        if (commandType.equals(Protocol.MOUSE_MOVE)) {
            command = commandType + Protocol.SEPARATOR + hostX + Protocol.SEPARATOR + hostY;
        } else { // MOUSE_PRESS, MOUSE_RELEASE
            command = commandType + Protocol.SEPARATOR + e.getButton();
            // Di chuyển chuột đến vị trí trước khi nhấn/thả để đảm bảo chính xác
            sendCommand(Protocol.MOUSE_MOVE + Protocol.SEPARATOR + hostX + Protocol.SEPARATOR + hostY);
        }
        sendCommand(command);
    }

    private void sendCommand(String command) {
        if (commandWriter != null) {
            commandWriter.println(command);
        }
    }

    private void readScreenStream() {
        try (DataInputStream dis = new DataInputStream(sock.getInputStream())) {
            while (!sock.isClosed()) {
                int len = dis.readInt();
                if (len <= 0) continue;
                byte[] data = new byte[len];
                dis.readFully(data);

                // Không cần SwingUtilities.invokeLater ở đây
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img != null) {
                    screenPanel.setScreenImage(img);
                }
            }
        } catch (IOException ex) {
            handleConnectionError("Stream ended", ex);
        }
    }

    private void handleConnectionError(String title, Exception ex) {
        if (!isDisplayable()) return;
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, title + ": " + ex.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
            dispose();
        });
    }

    @Override
    public void dispose() {
        super.dispose();
        try {
            if (sock != null && !sock.isClosed()) sock.close();
        } catch (IOException e) { /* Ignored */ }
        ConnectFrame.open();
    }
}