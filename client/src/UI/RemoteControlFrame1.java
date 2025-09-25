package UI;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.InputStream;
import java.net.Socket;

public class RemoteControlFrame1 extends JFrame {
    private final Socket sock;
    private final JLabel screenLabel;

    public RemoteControlFrame1(Socket socket) {
        super("Remote Control (Viewing Host Screen)");
        this.sock = socket;

        // Tính toán kích thước cửa sổ theo tỷ lệ 16:9 (1920x1080)
        // Sử dụng 80% kích thước màn hình để vừa với desktop
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int windowWidth = (int) (screenSize.width * 0.8);
        int windowHeight = (int) (windowWidth * 9.0 / 16.0); // Tỷ lệ 16:9

        // Đảm bảo không vượt quá chiều cao màn hình
        if (windowHeight > screenSize.height * 0.8) {
            windowHeight = (int) (screenSize.height * 0.8);
            windowWidth = (int) (windowHeight * 16.0 / 9.0);
        }

        setSize(windowWidth, windowHeight);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Tạo Menu Bar giống như giao diện mẫu
        createMenuBar();

        // Tạo panel chứa màn hình với padding để giống thật
        JPanel screenContainer = new JPanel(new BorderLayout());
        screenContainer.setBackground(Color.DARK_GRAY);
        screenContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        screenLabel = new JLabel("Receiving stream...", SwingConstants.CENTER);
        screenLabel.setOpaque(true);
        screenLabel.setBackground(Color.BLACK);
        screenLabel.setForeground(Color.WHITE);
        screenLabel.setFont(new Font("Arial", Font.PLAIN, 16));

        // Tạo scroll pane với tỷ lệ 16:9
        JScrollPane scrollPane = new JScrollPane(screenLabel);
        scrollPane.setPreferredSize(new Dimension(windowWidth - 40, windowHeight - 120)); // Trừ menu và taskbar
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        screenContainer.add(scrollPane, BorderLayout.CENTER);
        add(screenContainer, BorderLayout.CENTER);

        // Taskbar với kích thước cân đối
        JPanel taskbar = new JPanel(new BorderLayout());
        taskbar.setBackground(new Color(200, 200, 255));
        taskbar.setPreferredSize(new Dimension(windowWidth, 45));
        taskbar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // Panel bên trái taskbar
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftPanel.setOpaque(false);

        JButton startButton = new JButton("Start");
        startButton.setPreferredSize(new Dimension(70, 30));
        leftPanel.add(startButton);

        JLabel connectionLabel = new JLabel("Connected to: " + sock.getInetAddress().getHostAddress());
        connectionLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        leftPanel.add(connectionLabel);

        // Panel bên phải taskbar
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.setOpaque(false);

        JLabel timeLabel = new JLabel(java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy h:mm a")));
        timeLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        rightPanel.add(timeLabel);

        taskbar.add(leftPanel, BorderLayout.WEST);
        taskbar.add(rightPanel, BorderLayout.EAST);
        add(taskbar, BorderLayout.SOUTH);

        // start reader thread
        new Thread(this::readLoop).start();

        setLocationRelativeTo(null);
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Menu Actions
        JMenu actionsMenu = new JMenu("Actions");

        JMenuItem remoteReboot = new JMenuItem("Remote reboot");
        JMenuItem transferFile = new JMenuItem("Transfer File");
        JMenuItem chat = new JMenuItem("Chat");
        JMenuItem endSession = new JMenuItem("End Session");

        // Xử lý sự kiện cho các menu item
        remoteReboot.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to reboot the remote computer?",
                    "Confirm Reboot",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                // TODO: Gửi lệnh reboot qua socket
                JOptionPane.showMessageDialog(this, "Remote reboot command sent");
            }
        });

        transferFile.addActionListener(e -> {
            // TODO: Mở cửa sổ transfer file
            JOptionPane.showMessageDialog(this, "Transfer File window opened");
        });

        chat.addActionListener(e -> {
            // TODO: Mở cửa sổ chat
            JOptionPane.showMessageDialog(this, "Chat window opened");
        });

        endSession.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to end the remote session?",
                    "End Session",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                dispose();
            }
        });

        actionsMenu.add(remoteReboot);
        actionsMenu.add(transferFile);
        actionsMenu.add(chat);
        actionsMenu.addSeparator();
        actionsMenu.add(endSession);

        // Menu View
        JMenu viewMenu = new JMenu("View");

        JMenuItem fullScreen = new JMenuItem("Full Screen");
        JMenuItem fitToWindow = new JMenuItem("Fit to Window");
        JMenuItem actualSize = new JMenuItem("Actual Size");

        fullScreen.addActionListener(e -> {
            // TODO: Chuyển sang chế độ full screen
            JOptionPane.showMessageDialog(this, "Full screen mode");
        });

        fitToWindow.addActionListener(e -> {
            // Scale lại image để fit vào cửa sổ hiện tại
            if (screenLabel.getIcon() instanceof ImageIcon) {
                // Force refresh scaling
                SwingUtilities.invokeLater(() -> screenLabel.repaint());
                JOptionPane.showMessageDialog(this, "Scaled to fit window");
            }
        });

        actualSize.addActionListener(e -> {
            // Hiển thị kích thước gốc của image
            if (screenLabel.getIcon() instanceof ImageIcon) {
                // TODO: Hiển thị image ở kích thước thực không scale
                JOptionPane.showMessageDialog(this, "Showing actual size (1920x1080)");
            }
        });

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
                // read length + bytes
                int len = dis.readInt();
                if (len <= 0) continue;
                byte[] data = new byte[len];
                dis.readFully(data);
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(data));
                if (img != null) {
                    // Scale image để giữ tỷ lệ 16:9 khi hiển thị
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

    // Phương thức scale image để fit với container và giữ tỷ lệ
    private ImageIcon scaleImageToFit(BufferedImage originalImage) {
        try {
            // Tìm JScrollPane trong component tree
            JScrollPane scrollPane = findScrollPane(getContentPane());
            if (scrollPane == null) {
                return new ImageIcon(originalImage);
            }

            Dimension containerSize = scrollPane.getViewport().getSize();

            if (containerSize.width <= 0 || containerSize.height <= 0) {
                // Fallback to label size if viewport size is not available
                containerSize = screenLabel.getSize();
                if (containerSize.width <= 0 || containerSize.height <= 0) {
                    return new ImageIcon(originalImage);
                }
            }

            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            // Tính toán tỷ lệ scale để fit vào container
            double scaleX = (double) containerSize.width / originalWidth;
            double scaleY = (double) containerSize.height / originalHeight;
            double scale = Math.min(scaleX, scaleY); // Giữ tỷ lệ gốc

            // Không scale up nếu image nhỏ hơn container
            scale = Math.min(scale, 1.0);

            int newWidth = (int) (originalWidth * scale);
            int newHeight = (int) (originalHeight * scale);

            // Scale image mượt mà
            Image scaledImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
            return new ImageIcon(scaledImage);

        } catch (Exception e) {
            System.err.println("Error scaling image: " + e.getMessage());
            return new ImageIcon(originalImage);
        }
    }

    // Helper method để tìm JScrollPane trong component tree
    private JScrollPane findScrollPane(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JScrollPane) {
                return (JScrollPane) comp;
            } else if (comp instanceof Container) {
                JScrollPane found = findScrollPane((Container) comp);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        super.dispose();
        try { sock.close(); } catch (Exception e) {}
    }
}