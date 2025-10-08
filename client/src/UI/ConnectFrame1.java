package UI;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class ConnectFrame1 extends JFrame {
    private JTextField ipField;
    private JButton connectBtn;
    // private HostServer hostServer; // <<<<<<< ĐÃ XÓA: Không còn tham chiếu đến server

    public ConnectFrame1() {
        super("Remote Desktop - Connect (LAN demo)");
        setSize(800, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(1,2));

        // --- Panel bên trái (host info) ---
        // GIỮ NGUYÊN GIAO DIỆN, nhưng nó chỉ còn mang tính thông tin tham khảo
        JPanel left = new JPanel(new GridBagLayout());
        left.setBackground(new Color(200,200,255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8,8,8,8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        // Thêm một label để làm rõ vai trò của panel này
        JLabel leftTitle = new JLabel("Your Local Info (For Reference)");
        leftTitle.setFont(new Font("Arial", Font.BOLD, 14));
        left.add(leftTitle, gbc);

        gbc.gridy = 1; gbc.gridwidth = 1;
        left.add(new JLabel("Your IP"), gbc);
        JTextField yourIp = new JTextField(20);
        yourIp.setEditable(false);

        String bestIp = findPreferredIPv4();
        yourIp.setText(bestIp);
        gbc.gridx = 1; left.add(yourIp, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        left.add(new JLabel("Default Password"), gbc);
        JTextField pwField = new JTextField("demo123");
        pwField.setEditable(false);
        gbc.gridx = 1; left.add(pwField, gbc);
        add(left);

        // --- Panel bên phải (client connect) ---
        // Phần này giữ nguyên logic kết nối
        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(new Color(200,200,255));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel rightTitle = new JLabel("Control Remote Device (Client)");
        rightTitle.setFont(new Font("Arial", Font.BOLD, 14));
        right.add(rightTitle, gbc);

        gbc.gridy = 1; gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.WEST;
        right.add(new JLabel("IP Address (Host)"), gbc);
        ipField = new JTextField(20);
        ipField.setText(bestIp != null ? bestIp : ""); // Gợi ý IP hiện tại
        gbc.gridx = 1; right.add(ipField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        connectBtn = new JButton("Connect");
        right.add(connectBtn, gbc);

        add(right);

        // <<<<<<< ĐÃ XÓA: Toàn bộ block code tự động khởi động HostServer
        /*
        String pw = pwField.getText().trim();
        hostServer = new HostServer(5000, pw);
        new Thread(() -> { ... hostServer.start(); ... }).start();
        */

        // Hành vi connect: giữ nguyên, vì logic này của client đã đúng
        connectBtn.addActionListener(ev -> {
            String ip = ipField.getText().trim();
            if (ip.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nhập IP của host trong LAN");
                return;
            }
            String pwInput = JOptionPane.showInputDialog(this, "Nhập password của host:");
            if (pwInput == null || pwInput.trim().isEmpty()) return; // user hủy hoặc không nhập gì

            connectBtn.setEnabled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            new Thread(() -> {
                Socket socket = new Socket();
                try {
                    socket.connect(new InetSocketAddress(ip, 5000), 3000); // 3s timeout

                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    writer.println("AUTH " + pwInput);
                    String resp = reader.readLine();
                    if ("OK".equals(resp)) {
                        SwingUtilities.invokeLater(() -> {
                            this.dispose();
                            RemoteControlFrame1 rc = new RemoteControlFrame1(socket);
                            rc.setVisible(true);
                        });
                    } else {
                        try { socket.close(); } catch (Exception ignored) {}
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this, "Xác thực thất bại. Kiểm tra password."));
                    }
                } catch (Exception ex) {
                    try { socket.close(); } catch (Exception ignored) {}
                    String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "Không thể kết nối tới host: " + msg));
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        connectBtn.setEnabled(true);
                        setCursor(Cursor.getDefaultCursor());
                    });
                }
            }, "Client-Connector").start();
        });

        // <<<<<<< ĐÃ XÓA: Không cần dừng server khi đóng frame nữa
        /*
        addWindowListener(new WindowAdapter() { ... });
        */

        setLocationRelativeTo(null);
    }

    // Giữ nguyên hàm tìm IP vì nó vẫn hữu ích
    private String findPreferredIPv4() {
        try {
            List<InetAddress> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nets)) {
                try {
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                } catch (Exception ex) { continue; }
                String name = ni.getDisplayName().toLowerCase();
                if (name.contains("vmware") || name.contains("virtual") || name.contains("vbox") ||
                        name.contains("hyper-v") || name.contains("tunnel") || name.contains("loopback") ||
                        name.contains("wireshark") || name.contains("docker") || name.contains("tap")) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        candidates.add(addr);
                        if (name.contains("wi-fi") || name.contains("wifi") || name.contains("wireless") || name.contains("ethernet")) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
            if (!candidates.isEmpty()) {
                return candidates.get(0).getHostAddress();
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "unknown";
            }
        }
    }

    public static void open() {
        SwingUtilities.invokeLater(() -> {
            ConnectFrame1 f = new ConnectFrame1();
            f.setVisible(true);
        });
    }
}