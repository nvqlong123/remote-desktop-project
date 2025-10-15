package client;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class ConnectFrame extends JFrame {
    private JTextField ipField;
    private JButton connectBtn;

    public ConnectFrame() {
        super("Remote Desktop - Connect");
        setSize(800, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(1, 2));

        JPanel left = new JPanel(new GridBagLayout());
        left.setBackground(new Color(200, 200, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel leftTitle = new JLabel("Your Local Info (For Host Machine)");
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

        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(new Color(200, 200, 255));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel rightTitle = new JLabel("Control Another Device (Client)");
        rightTitle.setFont(new Font("Arial", Font.BOLD, 14));
        right.add(rightTitle, gbc);

        gbc.gridy = 1; gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.WEST;
        right.add(new JLabel("Host IP Address"), gbc);
        ipField = new JTextField(20);
        ipField.setText(bestIp != null ? bestIp : "");
        gbc.gridx = 1; right.add(ipField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        connectBtn = new JButton("Connect");
        right.add(connectBtn, gbc);
        add(right);

        connectBtn.addActionListener(ev -> connectToHost());

        setLocationRelativeTo(null);
    }

    private void connectToHost() {
        String ip = ipField.getText().trim();
        if (ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter the Host IP address.");
            return;
        }
        String pwInput = JOptionPane.showInputDialog(this, "Enter host password:");
        if (pwInput == null || pwInput.trim().isEmpty()) return;

        connectBtn.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new Thread(() -> {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(ip, 5000), 3000); // 3s timeout
                socket.setTcpNoDelay(true);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                writer.println("AUTH " + pwInput);
                String resp = reader.readLine();
                if ("OK".equals(resp)) {
                    SwingUtilities.invokeLater(() -> {
                        this.dispose();
                        new RemoteControlFrame(socket).setVisible(true);
                    });
                } else {
                    closeSocket(socket);
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "Authentication failed. Please check the password."));
                }
            } catch (Exception ex) {
                closeSocket(socket);
                String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Cannot connect to host: " + msg));
            } finally {
                SwingUtilities.invokeLater(() -> {
                    connectBtn.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                });
            }
        }, "Client-Connector").start();
    }

    private void closeSocket(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }

    private String findPreferredIPv4() {
        try {
            List<InetAddress> candidates = new ArrayList<>();
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nets)) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
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
        SwingUtilities.invokeLater(() -> new ConnectFrame().setVisible(true));
    }
}