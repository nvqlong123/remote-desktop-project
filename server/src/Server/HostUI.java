package Server;

import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class HostUI extends JFrame {
    private JButton startButton;
    private JButton stopButton;
    private JTextArea logArea;
    private HostServer hostServer;

    public HostUI() {
        super("Remote Desktop - Host");
        setSize(450, 350);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Panel thông tin
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Host Information"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        infoPanel.add(new JLabel("Your IP Address:"), gbc);
        JTextField ipField = new JTextField(findPreferredIPv4(), 15);
        ipField.setEditable(false);
        gbc.gridx = 1; infoPanel.add(ipField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        infoPanel.add(new JLabel("Password:"), gbc);
        JTextField pwField = new JTextField("demo123", 15);
        pwField.setEditable(false);
        gbc.gridx = 1; infoPanel.add(pwField, gbc);

        // Panel điều khiển
        JPanel controlPanel = new JPanel(new FlowLayout());
        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);
        controlPanel.add(startButton);
        controlPanel.add(stopButton);

        // Vùng log
        logArea = new JTextArea("Server is stopped.\n");
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        // Thêm các panel vào frame
        add(infoPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);

        // Xử lý sự kiện
        startButton.addActionListener(e -> startServer(5000, pwField.getText()));
        stopButton.addActionListener(e -> stopServer());

        // Đảm bảo server dừng khi đóng cửa sổ
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (hostServer != null) {
                    hostServer.stop();
                }
            }
        });

        setLocationRelativeTo(null);
    }

    private void startServer(int port, String password) {
        log("Starting server...");
        hostServer = new HostServer(port, password);
        new Thread(() -> {
            try {
                hostServer.start();
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    log("Server started successfully on port " + port);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        log("Error starting server: " + ex.getMessage()));
            }
        }).start();
    }

    private void stopServer() {
        if (hostServer != null) {
            log("Stopping server...");
            hostServer.stop();
            hostServer = null;
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            log("Server stopped.");
        }
    }

    private void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // Hàm tìm IP, giữ nguyên từ code gốc vì nó hoạt động tốt
    private String findPreferredIPv4() {
        try {
            List<InetAddress> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nets)) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String name = ni.getDisplayName().toLowerCase();
                if (name.contains("vmware") || name.contains("virtual") || name.contains("docker")) continue;

                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        if (name.contains("wi-fi") || name.contains("wireless") || name.contains("ethernet")) {
                            return addr.getHostAddress(); // Ưu tiên trả về ngay
                        }
                        candidates.add(addr);
                    }
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.get(0).getHostAddress();
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static void open() {
        SwingUtilities.invokeLater(() -> new HostUI().setVisible(true));
    }
}