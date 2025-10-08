package Server;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HostServer {
    private final int port;
    private final String password;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private final ExecutorService clientHandlerPool = Executors.newCachedThreadPool();

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HostServer(int port, String password) {
        this.port = port;
        this.password = password;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        log("HostServer is listening on port " + port);

        // Vòng lặp chính để chấp nhận các kết nối mới
        new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    log("New connection from: " + clientSocket.getRemoteSocketAddress());
                    // Giao cho một thread khác xử lý client này
                    clientHandlerPool.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        log("Error accepting client connection: " + e.getMessage());
                    } else {
                        log("Server socket closed.");
                    }
                }
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        String clientInfo = socket.getRemoteSocketAddress().toString();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            // Bước 1: Xác thực
            String authLine = reader.readLine();
            log("Authenticating client " + clientInfo + "...");
            if (authLine != null && authLine.startsWith("AUTH ") && authLine.substring(5).equals(password)) {
                writer.println("OK");
                log("Authentication successful for " + clientInfo);

                // Bước 2: Bắt đầu stream màn hình
                streamScreen(dos, socket);
            } else {
                writer.println("ERR");
                log("Authentication failed for " + clientInfo);
            }
        } catch (Exception e) {
            log("Connection with " + clientInfo + " ended: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            log("Closed connection with " + clientInfo);
        }
    }

    private void streamScreen(DataOutputStream dos, Socket socket) throws AWTException, IOException {
        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

        while (running && !socket.isClosed()) {
            try {
                BufferedImage screenCapture = robot.createScreenCapture(screenRect);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(screenCapture, "jpg", baos);
                byte[] imageData = baos.toByteArray();

                // Gửi dữ liệu theo giao thức: [độ dài (int)][dữ liệu ảnh (byte[])]
                dos.writeInt(imageData.length);
                dos.write(imageData);
                dos.flush();

                // Điều chỉnh FPS để giảm tải CPU và băng thông
                Thread.sleep(250); // ~4 FPS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                // Thường xảy ra khi client ngắt kết nối
                break;
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log("Error closing server socket: " + e.getMessage());
        }
        clientHandlerPool.shutdownNow(); // Ngắt tất cả các thread xử lý client
        log("HostServer stopped.");
    }

    private void log(String message) {
        System.out.println(LocalDateTime.now().format(dtf) + " - " + message);
    }
}