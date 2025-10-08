// Trong file: server/HostServer.java

package Server;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HostServer {
    // ================== CÁC THAM SỐ TỐI ƯU ==================
    /**
     * Chất lượng ảnh JPEG.
     * 1.0f = cao nhất, 0.75f = cân bằng, 0.5f = ưu tiên tốc độ.
     */
    private static final float JPEG_QUALITY = 0.75f;

    /**
     * Tỷ lệ co nhỏ ảnh trước khi gửi.
     * 1.0 = không co nhỏ, 0.8 = giảm còn 80% kích thước.
     * Giảm tỷ lệ này sẽ tăng FPS đáng kể.
     */
    private static final double SCALE_FACTOR = 0.8;

    /**
     * Thời gian nghỉ giữa các khung hình (ms).
     * 1000 / FPS = FRAME_INTERVAL_MS.
     * Ví dụ: 15 FPS -> 1000 / 15 ~= 66ms.
     */
    private static final int FRAME_INTERVAL_MS = 66; // Mục tiêu ~15 FPS
    // ========================================================


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
        log(String.format("Optimization settings: Quality=%.2f, Scale=%.2f, Target FPS=%d",
                JPEG_QUALITY, SCALE_FACTOR, 1000 / FRAME_INTERVAL_MS));

        new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    log("New connection from: " + clientSocket.getRemoteSocketAddress());
                    clientHandlerPool.submit(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        log("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        String clientInfo = socket.getRemoteSocketAddress().toString();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            String authLine = reader.readLine();
            log("Authenticating client " + clientInfo + "...");
            if (authLine != null && authLine.startsWith("AUTH ") && authLine.substring(5).equals(password)) {
                writer.println("OK");
                log("Authentication successful for " + clientInfo);
                streamScreen(socket); // Bắt đầu stream
            } else {
                writer.println("ERR");
                log("Authentication failed for " + clientInfo);
            }
        } catch (Exception e) {
            log("Connection with " + clientInfo + " ended: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) { /* Ignore */ }
            log("Closed connection with " + clientInfo);
        }
    }

    private void streamScreen(Socket socket) throws AWTException, IOException {
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

        // Thiết lập bộ nén JPEG
        ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
        jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpgWriteParam.setCompressionQuality(JPEG_QUALITY);

        while (running && !socket.isClosed()) {
            long startTime = System.currentTimeMillis();
            try {
                // 1. Chụp màn hình
                BufferedImage screenCapture = robot.createScreenCapture(screenRect);

                // 2. Co nhỏ ảnh (nếu cần)
                BufferedImage imageToSend;
                if (SCALE_FACTOR < 1.0) {
                    int newWidth = (int) (screenCapture.getWidth() * SCALE_FACTOR);
                    int newHeight = (int) (screenCapture.getHeight() * SCALE_FACTOR);
                    imageToSend = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = imageToSend.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(screenCapture, 0, 0, newWidth, newHeight, null);
                    g.dispose();
                } else {
                    imageToSend = screenCapture;
                }

                // 3. Nén ảnh với chất lượng đã chọn
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                jpgWriter.setOutput(ios);
                jpgWriter.write(null, new IIOImage(imageToSend, null, null), jpgWriteParam);
                ios.close();
                byte[] imageData = baos.toByteArray();

                // 4. Gửi dữ liệu
                dos.writeInt(imageData.length);
                dos.write(imageData);
                dos.flush();

                // 5. Điều chỉnh FPS
                long elapsedTime = System.currentTimeMillis() - startTime;
                long sleepTime = FRAME_INTERVAL_MS - elapsedTime;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                log("Stream error for " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
                break;
            }
        }
        jpgWriter.dispose();
    }

    // Các phương thức còn lại (stop, log, ...) giữ nguyên
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log("Error closing server socket: " + e.getMessage());
        }
        clientHandlerPool.shutdownNow();
        log("HostServer stopped.");
    }

    private void log(String message) {
        System.out.println(LocalDateTime.now().format(dtf) + " - " + message);
    }
}