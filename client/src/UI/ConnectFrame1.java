package UI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

// IMPORT HostServer từ module common/shared
import shared.HostServer;

public class ConnectFrame1 extends JFrame {
    private JTextField ipField;
    private JButton connectBtn;
    private HostServer hostServer; // Tham chiếu để quản lý server
    private JTextField yourIp;

    public ConnectFrame1() {
        super("Remote Desktop - Connect (LAN demo)");
        setSize(800, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(1,2));

        // left panel (host info)
        JPanel left = new JPanel(new GridBagLayout());
        left.setBackground(new Color(200,200,255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8,8,8,8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        left.add(new JLabel("Allow remote control (Host)"), gbc);
        gbc.gridy = 1; gbc.gridwidth = 1;
        left.add(new JLabel("Your IP"), gbc);
        yourIp = new JTextField(20);
        yourIp.setEditable(false);

        // Lấy IP thực bằng cách quét NetworkInterface
        String bestIp = findPreferredIPv4();
        yourIp.setText(bestIp);
        gbc.gridx = 1; left.add(yourIp, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        left.add(new JLabel("Password"), gbc);
        JTextField pwField = new JTextField("demo123");
        pwField.setEditable(false);
        gbc.gridx = 1; left.add(pwField, gbc);
        add(left);

        // right panel (client connect)
        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(new Color(200,200,255));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        right.add(new JLabel("Control remote device (Client)"), gbc);

        gbc.gridy = 1; gbc.gridwidth = 1;
        right.add(new JLabel("IP Address (Host)"), gbc);
        ipField = new JTextField(20);
        // Mặc định gợi ý IP hiện tại
        ipField.setText(bestIp != null ? bestIp : "");
        gbc.gridx = 1; right.add(ipField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        connectBtn = new JButton("Connect");
        right.add(connectBtn, gbc);

        add(right);

        // Tự động khởi động HostServer khi mở UI (đảm bảo server chỉ chạy khi UI mở)
        String pw = pwField.getText().trim();
        hostServer = new HostServer(5000, pw);

        new Thread(() -> {
            try {
                System.out.println(LocalDateTime.now() + " - Khởi động HostServer trên port 5000 với password: " + pw);
                hostServer.start();
            } catch (Exception e) {
                // Nếu là BindException thì port bị chiếm, báo cho user
                String err = e.getMessage() == null ? e.toString() : e.getMessage();
                System.err.println(LocalDateTime.now() + " - Lỗi khởi động server: " + err);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Không thể mở port 5000. Có thể port đã được chiếm bởi process khác.\n" +
                                "Hãy tắt process chiếm port hoặc đổi port trong cấu hình.", "Lỗi khởi động server", JOptionPane.ERROR_MESSAGE));
            }
        }, "HostServer-Starter").start();

        // Hành vi connect: thử connect với timeout, auth, rồi mở giao diện 2 nếu OK
        connectBtn.addActionListener(ev -> {
            String ip = ipField.getText().trim();
            if (ip.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nhập IP của host trong LAN");
                return;
            }
            String pwInput = JOptionPane.showInputDialog(this, "Nhập password của host:");
            if (pwInput == null) return; // user hủy

            // Disable nút + hiện cursor chờ
            connectBtn.setEnabled(false);
            Cursor oldCursor = getCursor();
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            // Kết nối trong background thread
            new Thread(() -> {
                Socket socket = new Socket();
                try {
                    // Thử connect với timeout 3s
                    socket.connect(new InetSocketAddress(ip, 5000), 3000);

                    // Gửi AUTH và đọc phản hồi (không đóng socket nếu OK)
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    writer.println("AUTH " + pwInput);
                    String resp = reader.readLine();
                    if ("OK".equals(resp)) {
                        // Trên thành công: mở RemoteControlFrame với socket đang mở
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, "Xác thực thành công. Mở RemoteControlFrame...");
                            this.dispose();
                            RemoteControlFrame1 rc = new RemoteControlFrame1(socket);
                            rc.setVisible(true);
                        });
                    } else {
                        // Auth fail: đóng socket và báo lỗi
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
                        setCursor(oldCursor);
                    });
                }
            }, "Client-Connector").start();
        });

        // Dừng server khi đóng frame (tùy chọn, để tránh server chạy ngầm)
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (hostServer != null) {
                    hostServer.stop();
                    System.out.println(LocalDateTime.now() + " - Dừng HostServer khi đóng UI");
                }
            }
        });

        setLocationRelativeTo(null);
    }

    /**
     * Quét các NetworkInterface, trả về IP IPv4 "ưu tiên" (không phải loopback, không virtual,
     * ưu tiên tên interface có "Wi-Fi"/"Wireless"/"Ethernet"). Nếu không tìm được, trả về localhost IP.
     */
    private String findPreferredIPv4() {
        try {
            List<InetAddress> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nets)) {
                try {
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                } catch (Exception ex) {
                    continue;
                }
                String name = ni.getDisplayName().toLowerCase();
                // Bỏ qua các adapter ảo, VPN thông dụng
                if (name.contains("vmware") || name.contains("virtual") || name.contains("vbox") ||
                        name.contains("hyper-v") || name.contains("tunnel") || name.contains("loopback") ||
                        name.contains("wireshark") || name.contains("docker") || name.contains("tap")) {
                    continue;
                }
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        candidates.add(addr);
                        // nếu tên giao diện có chữ Wi-Fi/Ethernet thì ưu tiên trả ngay
                        if (name.contains("wi-fi") || name.contains("wifi") || name.contains("wireless") || name.contains("ethernet")) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
            // Nếu có candidate nào, trả cái đầu (thường card chính)
            if (!candidates.isEmpty()) {
                return candidates.get(0).getHostAddress();
            }
            // fallback
            InetAddress local = InetAddress.getLocalHost();
            return local.getHostAddress();
        } catch (Exception e) {
            try {
                InetAddress local = InetAddress.getLocalHost();
                return local.getHostAddress();
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
