package UI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ConnectFrame1 extends JFrame {
    private JTextField ipField;

    public ConnectFrame1() {
        super("Remote Desktop - Connect (LAN demo)");
        setSize(800, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(1,2));

        // left panel: show own info (optional)
        JPanel left = new JPanel(new GridBagLayout());
        left.setBackground(new Color(200,200,255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8,8,8,8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        left.add(new JLabel("Allow remote control (Host)"), gbc);
        gbc.gridy = 1; gbc.gridwidth = 1;
        left.add(new JLabel("Your IP (shown by OS)"), gbc);
        JTextField yourIp = new JTextField(20);
        yourIp.setEditable(false);
        try {
            yourIp.setText(java.net.InetAddress.getLocalHost().getHostAddress());
        } catch (Exception e) { yourIp.setText("unknown"); }
        gbc.gridx = 1; left.add(yourIp, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        left.add(new JLabel("Password"), gbc);
        JTextField pwField = new JTextField("demo123");
        pwField.setEditable(false);
        gbc.gridx = 1; left.add(pwField, gbc);

        add(left);

        // right panel: connect
        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(new Color(200,200,255));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        right.add(new JLabel("Control remote device (Client)"), gbc);

        gbc.gridy = 1; gbc.gridwidth = 1;
        right.add(new JLabel("IP Address (Host)"), gbc);
        ipField = new JTextField(20);
        gbc.gridx = 1; right.add(ipField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton connectBtn = new JButton("Connect");
        right.add(connectBtn, gbc);

        add(right);

        connectBtn.addActionListener((ActionEvent e) -> {
            String ip = ipField.getText().trim();
            if (ip.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Nhập IP của host trong LAN");
                return;
            }
            // ask for password to authenticate to host
            String pw = JOptionPane.showInputDialog(this, "Nhập password của host:");
            if (pw == null) return;

            // connect in background thread
            new Thread(() -> {
                try {
                    Socket s = new Socket(ip, 5000);
                    PrintWriter w = new PrintWriter(s.getOutputStream(), true);
                    BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    w.println("AUTH " + pw);
                    String resp = r.readLine();
                    if ("OK".equals(resp)) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this, "Xác thực thành công. Mở RemoteControlFrame...");
                            this.dispose();
                            RemoteControlFrame1 rc = new RemoteControlFrame1(s);
                            rc.setVisible(true);
                        });
                    } else {
                        s.close();
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this, "Xác thực thất bại"));
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "Lỗi kết nối: " + ex.getMessage()));
                }
            }).start();
        });

        setLocationRelativeTo(null);
    }

    public static void open() {
        SwingUtilities.invokeLater(() -> {
            ConnectFrame f = new ConnectFrame();
            f.setVisible(true);
        });
    }
}
