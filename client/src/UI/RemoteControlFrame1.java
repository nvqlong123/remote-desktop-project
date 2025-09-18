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
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        screenLabel = new JLabel("Receiving stream...", SwingConstants.CENTER);
        screenLabel.setOpaque(true);
        screenLabel.setBackground(Color.BLACK);
        add(new JScrollPane(screenLabel), BorderLayout.CENTER);

        JPanel taskbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        taskbar.setBackground(new Color(200,200,255));
        taskbar.setPreferredSize(new Dimension(1000, 40));
        taskbar.add(new JLabel("Connected to: " + sock.getInetAddress().getHostAddress()));
        add(taskbar, BorderLayout.SOUTH);

        // start reader thread
        new Thread(this::readLoop).start();

        setLocationRelativeTo(null);
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
                    ImageIcon icon = new ImageIcon(img);
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

    @Override
    public void dispose() {
        super.dispose();
        try { sock.close(); } catch (Exception e) {}
    }
}
