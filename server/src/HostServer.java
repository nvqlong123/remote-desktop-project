
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class HostServer {
    private final int port;
    private final String password;
    private volatile boolean running = true;

    public HostServer(int port, String password) {
        this.port = port;
        this.password = password;
    }

    public void start() throws Exception {
        ServerSocket ss = new ServerSocket(port);
        System.out.println("HostServer listening on port " + port);
        while (running) {
            Socket client = ss.accept();
            System.out.println("Client connected: " + client.getInetAddress());
            new Thread(() -> handleClient(client)).start();
        }
        // ss.close(); // never reached in this demo
    }

    private void handleClient(Socket s) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter pw = new PrintWriter(s.getOutputStream(), true);
             OutputStream out = s.getOutputStream()) {

            // Expect "AUTH <password>"
            String authLine = br.readLine();
            System.out.println("Auth attempt: " + authLine);
            if (authLine == null || !authLine.startsWith("AUTH ")) {
                pw.println("ERR");
                s.close();
                return;
            }
            String got = authLine.substring(5).trim();
            if (!got.equals(password)) {
                pw.println("ERR");
                s.close();
                System.out.println("Auth failed from " + s.getInetAddress());
                return;
            }
            pw.println("OK");
            System.out.println("Auth OK, start streaming to " + s.getInetAddress());

            // Start streaming screenshots as JPEG frames
            DataOutputStream dos = new DataOutputStream(out);
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

            while (!s.isClosed()) {
                BufferedImage img = robot.createScreenCapture(screenRect);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                // write JPEG to baos
                ImageIO.write(img, "jpg", baos);
                byte[] data = baos.toByteArray();

                // protocol: [int length][bytes]
                dos.writeInt(data.length);
                dos.write(data);
                dos.flush();

                // throttle FPS
                try { Thread.sleep(400); } catch (InterruptedException ie) { /* ignore */ }
            }
        } catch (Exception ex) {
            System.out.println("Client handler ended: " + ex.getMessage());
        } finally {
            try { s.close(); } catch (Exception e) {}
        }
    }

    public void stop() {
        running = false;
    }
}
