package UI;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RemoteControlFrame1 extends JFrame {

    private final Socket sock;
    private final JLabel screenLabel;
    private BufferedImage currentFrame = null; // Lưu frame hiện tại cho diff

    public RemoteControlFrame1(Socket socket) {
        super("Remote Control (Viewing Host Screen)");
        this.sock = socket;

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int windowWidth = (int) (screenSize.width * 0.8);
        int windowHeight = (int) (windowWidth * 9.0 / 16.0);

        if (windowHeight > screenSize.height * 0.8) {
            windowHeight = (int) (screenSize.height * 0.8);
            windowWidth = (int) (windowHeight * 16.0 / 9.0);
        }

        setSize(windowWidth, windowHeight);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        createMenuBar();

        JPanel screenContainer = new JPanel(new BorderLayout());
        screenContainer.setBackground(Color.DARK_GRAY);
        screenContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        screenLabel = new JLabel("Receiving stream...", SwingConstants.CENTER);
        screenLabel.setOpaque(true);
        screenLabel.setBackground(Color.BLACK);
        screenLabel.setForeground(Color.WHITE);
        screenLabel.setFont(new Font("Arial", Font.PLAIN, 16));

        JScrollPane scrollPane = new JScrollPane(screenLabel);
        screenContainer.add(scrollPane, BorderLayout.CENTER);
        add(screenContainer, BorderLayout.CENTER);

        JPanel taskbar = new JPanel(new BorderLayout());
        taskbar.setBackground(new Color(200, 200, 255));
        taskbar.setPreferredSize(new Dimension(windowWidth, 45));
        taskbar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftPanel.setOpaque(false);
        JButton startButton = new JButton("Start");
        startButton.setPreferredSize(new Dimension(70, 30));
        leftPanel.add(startButton);
        JLabel connectionLabel = new JLabel("Connected to: " + sock.getInetAddress().getHostAddress());
        connectionLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        leftPanel.add(connectionLabel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.setOpaque(false);
        JLabel timeLabel = new JLabel(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("M/d/yyyy h:mm a")));
        timeLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        rightPanel.add(timeLabel);

        taskbar.add(leftPanel, BorderLayout.WEST);
        taskbar.add(rightPanel, BorderLayout.EAST);
        add(taskbar, BorderLayout.SOUTH);

        new Thread(this::readLoop).start();

        setLocationRelativeTo(null);
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu actionsMenu = new JMenu("Actions");
        JMenuItem remoteReboot = new JMenuItem("Remote reboot");
        JMenuItem transferFile = new JMenuItem("Transfer File");
        JMenuItem chat = new JMenuItem("Chat");
        JMenuItem endSession = new JMenuItem("End Session");

        endSession.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to end the remote session?",
                    "End Session", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                dispose();
            }
        });

        actionsMenu.add(remoteReboot);
        actionsMenu.add(transferFile);
        actionsMenu.add(chat);
        actionsMenu.addSeparator();
        actionsMenu.add(endSession);

        JMenu viewMenu = new JMenu("View");
        JMenuItem fullScreen = new JMenuItem("Full Screen");
        JMenuItem fitToWindow = new JMenuItem("Fit to Window");
        JMenuItem actualSize = new JMenuItem("Actual Size");
        viewMenu.add(fullScreen);
        viewMenu.add(fitToWindow);
        viewMenu.add(actualSize);

        menuBar.add(actionsMenu);
        menuBar.add(viewMenu);
        setJMenuBar(menuBar);
    }

    private void readLoop() {
        try (InputStream in = sock.getInputStream();
             DataInputStream dis = new DataInputStream(in)) {

            while (!sock.isClosed()) {
                int len = dis.readInt();
                if (len == 0) { // Empty frame
                    String frameType = dis.readUTF();
                    if ("EMPTY".equals(frameType)) {
                        // Không update, giữ frame cũ
                        continue;
                    }
                    // Nếu không phải EMPTY, treat as error or fallback
                  //  throw new IOException("Unexpected empty frame type: " + frameType);
                }

                String frameType = dis.readUTF();
                BufferedImage img = null;

                if ("FULL".equals(frameType)) {
                    // Đọc data cho FULL
                    byte[] data = new byte[len];
                    dis.readFully(data);
                    try {
                        img = ImageIO.read(new ByteArrayInputStream(data));
                        if (img == null) {
                           // throw new IOException("Failed to decode FULL image");
                        }
                        currentFrame = img;
                    } catch (Exception imgEx) {
                        System.err.println("ImageIO error for FULL: " + imgEx.getMessage());
                        continue; // Skip frame này, không crash
                    }
                } else if ("DIFF".equals(frameType)) {
                    // Đọc coords TRƯỚC data cho DIFF
                    int x = dis.readInt();
                    int y = dis.readInt();
                    int w = dis.readInt();
                    int h = dis.readInt();
                    // Sau đó đọc data
                    byte[] data = new byte[len];
                    dis.readFully(data);
                    try {
                        img = ImageIO.read(new ByteArrayInputStream(data));
                        if (img == null || currentFrame == null) {
                            if (currentFrame == null) {
                                // Fallback: Treat as FULL nếu chưa có currentFrame
                                currentFrame = img;
                            } else {
                               // throw new IOException("Failed to decode DIFF image");
                            }
                        } else {
                            // Composite diff vào currentFrame
                            Graphics2D g = currentFrame.createGraphics();
                            g.drawImage(img, x, y, x + w, y + h, 0, 0, img.getWidth(), img.getHeight(), null);
                            g.dispose();
                            img = currentFrame; // Dùng composite để display
                        }
                    } catch (Exception imgEx) {
                        System.err.println("ImageIO error for DIFF: " + imgEx.getMessage());
                        continue; // Skip frame này
                    }
                } else {
                    // Unknown type: Skip data
                    byte[] data = new byte[len];
                    dis.readFully(data);
                    System.err.println("Unknown frame type: " + frameType);
                    continue;
                }

                // Đọc timestamp và gửi ACK (chỉ cho non-empty)
                long sendTime;
                try {
                    sendTime = dis.readLong();
                } catch (Exception tsEx) {
                    System.err.println("Timestamp read error: " + tsEx.getMessage());
                    continue;
                }
                long recvTime = System.currentTimeMillis();
                new Thread(() -> {
                    try {
                        DataOutputStream dos = new DataOutputStream(sock.getOutputStream());
                        dos.writeLong(recvTime);
                        dos.flush();
                    } catch (Exception ignored) {
                        System.err.println("ACK send error: " + ignored.getMessage());
                    }
                }).start();

                // Update UI nếu có img
                if (img != null) {
                    ImageIcon icon = scaleImageToFit(img);
                    SwingUtilities.invokeLater(() -> {
                        screenLabel.setIcon(icon);
                        screenLabel.setText(null);
                    });
                }
            }
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Stream ended: " + ex.getMessage());
                dispose();
            });
        }
    }

    private ImageIcon scaleImageToFit(BufferedImage originalImage) {
        // Sử dụng kích thước của viewport trong scrollpane để scale cho chính xác
        Container parent = screenLabel.getParent();
        if (parent instanceof JViewport) {
            JViewport viewport = (JViewport) parent;
            Dimension containerSize = viewport.getSize();

            if (containerSize.width <= 0 || containerSize.height <= 0) {
                return new ImageIcon(originalImage);
            }

            double scaleX = (double) containerSize.width / originalImage.getWidth();
            double scaleY = (double) containerSize.height / originalImage.getHeight();
            double scale = Math.min(scaleX, scaleY);

            // Không phóng to ảnh nếu ảnh gốc nhỏ hơn container
            if (scale > 1.0) scale = 1.0;

            int newWidth = (int) (originalImage.getWidth() * scale);
            int newHeight = (int) (originalImage.getHeight() * scale);

            // Sử dụng Graphics2D cho scale (tương tự server, consistent)
            BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaledImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
            g.dispose();
            return new ImageIcon(scaledImage);
        }
        return new ImageIcon(originalImage); // Fallback
    }

    @Override
    public void dispose() {
        super.dispose();
        try { sock.close(); } catch (Exception e) {}
    }
}