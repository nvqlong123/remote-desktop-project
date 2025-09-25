package shared;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;

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
        System.out.println(LocalDateTime.now() + " - HostServer đang lắng nghe trên port " + port);
        while (running) {
            Socket client = ss.accept();
            String clientInfo = client.getInetAddress().getHostAddress() + ":" + client.getPort();
            System.out.println(LocalDateTime.now() + " - Thiết bị kết nối: " + clientInfo + " (Kiểm tra xác thực...)");
            // Kiểm tra chặt: Log chi tiết IP và thời gian, chỉ chấp nhận nếu auth OK
            new Thread(() -> handleClient(client)).start();
        }
        // ss.close(); // never reached in this demo
    }

    private void handleClient(Socket s) {
        String clientInfo = s.getInetAddress().getHostAddress() + ":" + s.getPort();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter pw = new PrintWriter(s.getOutputStream(), true);
             OutputStream out = s.getOutputStream()) {

            String authLine = br.readLine();
            System.out.println(LocalDateTime.now() + " - Yêu cầu xác thực từ " + clientInfo + ": " + authLine);
            if (authLine == null || !authLine.startsWith("AUTH ")) {
                System.out.println(LocalDateTime.now() + " - Lỗi định dạng xác thực từ " + clientInfo + " -> Đóng kết nối");
                pw.println("ERR");
                s.close();
                return;
            }
            String got = authLine.substring(5).trim();
            if (!got.equals(password)) {
                System.out.println(LocalDateTime.now() + " - Xác thực thất bại từ " + clientInfo + " (password sai) -> Đóng kết nối");
                pw.println("ERR");
                s.close();
                return;
            }
            pw.println("OK");
            System.out.println(LocalDateTime.now() + " - Xác thực thành công từ " + clientInfo + ". Bắt đầu stream màn hình (FPS ~2.5)");

            // Start streaming screenshots as JPEG frames
            DataOutputStream dos = new DataOutputStream(out);
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

            while (!s.isClosed() && running) {
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
            System.out.println(LocalDateTime.now() + " - Kết nối từ " + clientInfo + " kết thúc bất ngờ: " + ex.getMessage());
        } finally {
            try { s.close(); } catch (Exception e) {}
            System.out.println(LocalDateTime.now() + " - Đóng kết nối với " + clientInfo);
        }
    }

    public void stop() {
        running = false;
        System.out.println(LocalDateTime.now() + " - Dừng HostServer");
    }
}
