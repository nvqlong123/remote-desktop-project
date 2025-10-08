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
     * Chất lượng ảnh JPEG ban đầu.
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

    /**
     * Ngưỡng thay đổi pixel cho diff (% tổng pixel).
     */
    private static final double DIFF_THRESHOLD = 0.05; // 5%

    /**
     * Ngưỡng latency để giảm quality (ms).
     */
    private static final long LATENCY_THRESHOLD = 200;

// ========================================================

    private final int port;
    private final String password;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private final ExecutorService clientHandlerPool = Executors.newCachedThreadPool();
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Biến cho delta compression và adaptive quality
    private BufferedImage prevFrame;
    private Rectangle dirtyRect = null;
    private float currentQuality = JPEG_QUALITY;

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
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            String authLine = reader.readLine();
            log("Authenticating client " + clientInfo + "...");
            if (authLine != null && authLine.startsWith("AUTH ") && authLine.substring(5).equals(password)) {
                writer.println("OK");
                log("Authentication successful for " + clientInfo);
                streamScreen(socket, dos, dis); // Truyền dos, dis cho stream
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
            prevFrame = null; // Reset prevFrame cho connection mới
        }
    }

    private void streamScreen(Socket socket, DataOutputStream dos, DataInputStream dis) throws AWTException, IOException {
        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

        // Thiết lập bộ nén JPEG
        ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
        jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpgWriteParam.setCompressionQuality(currentQuality);

        while (running && !socket.isClosed()) {
            long startTime = System.currentTimeMillis();
            try {
                // 1. Chụp màn hình
                BufferedImage screenCapture = robot.createScreenCapture(screenRect);

                // 2. Tính diff và crop dirty region (nếu có prevFrame)
                BufferedImage imageToSend = screenCapture; // Default full
                boolean isDiff = false;
                if (prevFrame != null) {
                    dirtyRect = findDirtyRegion(screenCapture, prevFrame);
                    if (dirtyRect != null && !dirtyRect.isEmpty()) {
                        // Crop chỉ vùng thay đổi
                        imageToSend = screenCapture.getSubimage(dirtyRect.x, dirtyRect.y,
                                dirtyRect.width, dirtyRect.height);
                        isDiff = true;
                    } else {
                        // Không thay đổi: Gửi empty frame
                        sendEmptyFrame(dos);
                        updatePrevFrame(screenCapture, screenRect); // Cập nhật prev mà không scale
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        long sleepTime = FRAME_INTERVAL_MS - elapsedTime;
                        if (sleepTime > 0) Thread.sleep(sleepTime);
                        continue;
                    }
                }

                // 3. Scale ảnh (sử dụng Graphics2D với better hints)
                if (SCALE_FACTOR < 1.0) {
                    int newWidth = (int) (imageToSend.getWidth() * SCALE_FACTOR);
                    int newHeight = (int) (imageToSend.getHeight() * SCALE_FACTOR);
                    BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = scaledImage.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.drawImage(imageToSend, 0, 0, newWidth, newHeight, null);
                    g.dispose();
                    imageToSend = scaledImage;
                }

                // 4. Nén ảnh với chất lượng hiện tại
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
                jpgWriter.setOutput(ios);
                jpgWriter.write(null, new IIOImage(imageToSend, null, null), jpgWriteParam);
                ios.close();
                byte[] imageData = baos.toByteArray();

                // 5. Gửi dữ liệu (kèm flag và tọa độ nếu diff)
                dos.writeInt(imageData.length);
                if (isDiff) {
                    dos.writeUTF("DIFF");
                    dos.writeInt(dirtyRect.x);
                    dos.writeInt(dirtyRect.y);
                    dos.writeInt(dirtyRect.width);
                    dos.writeInt(dirtyRect.height);
                } else {
                    dos.writeUTF("FULL");
                }
                dos.write(imageData);
                long sendTime = System.currentTimeMillis();
                dos.writeLong(sendTime); // Gửi timestamp cho RTT
                dos.flush();

                // 6. Đọc ACK từ client và adjust quality
                try {
                    long recvTime = dis.readLong(); // Client gửi back recvTime
                    long rtt = System.currentTimeMillis() - sendTime;
                    adjustQuality(rtt);
                } catch (Exception e) {
                    log("ACK read error: " + e.getMessage());
                }

                // 7. Cập nhật prevFrame (luôn full size, không scale để so sánh chính xác)
                updatePrevFrame(screenCapture, screenRect);

                // 8. Điều chỉnh FPS
                long elapsedTime = System.currentTimeMillis() - startTime;
                long sleepTime = FRAME_INTERVAL_MS - elapsedTime;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                log("Stream error: " + e.getMessage());
                break;
            } catch (Exception e) {
                log("Unexpected error in stream: " + e.getMessage());
            }
        }
        jpgWriter.dispose();
    }

    // Helper: Tìm vùng thay đổi (threshold 5% pixel khác biệt để tránh noise)
    private Rectangle findDirtyRegion(BufferedImage current, BufferedImage prev) {
        int w = Math.min(current.getWidth(), prev.getWidth());
        int h = Math.min(current.getHeight(), prev.getHeight());
        int minX = w, minY = h, maxX = 0, maxY = 0;
        int totalDiff = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int currRGB = current.getRGB(x, y);
                int prevRGB = prev.getRGB(x, y);
                if (currRGB != prevRGB) {
                    totalDiff++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        // Nếu thay đổi > threshold, trả về bounding box
        if (totalDiff > (w * h * DIFF_THRESHOLD)) {
            return new Rectangle(minX, minY, Math.max(1, maxX - minX + 1), Math.max(1, maxY - minY + 1));
        }
        return null;
    }

    // Helper: Gửi empty frame để tiết kiệm
    private void sendEmptyFrame(DataOutputStream dos) throws IOException {
        dos.writeInt(0); // Length 0
        dos.writeUTF("EMPTY");
        dos.flush();
    }

    // Helper: Cập nhật prevFrame (full size)
    private void updatePrevFrame(BufferedImage screenCapture, Rectangle screenRect) {
        prevFrame = new BufferedImage(screenRect.width, screenRect.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = prevFrame.createGraphics();
        g.drawImage(screenCapture, 0, 0, null);
        g.dispose();
    }

    // Helper: Điều chỉnh quality dựa trên RTT
    private void adjustQuality(long rtt) {
        if (rtt > LATENCY_THRESHOLD) {
            currentQuality = Math.max(0.3f, currentQuality - 0.1f); // Giảm dần
        } else if (rtt < 100) {
            currentQuality = Math.min(1.0f, currentQuality + 0.05f); // Tăng dần
        }
        // Cập nhật param (jpgWriteParam là instance, cần làm global nếu multi-client)
        // Lưu ý: Để multi-client, dùng per-client quality
        log("Adjusted quality to " + String.format("%.2f", currentQuality) + " (RTT: " + rtt + "ms)");
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
        clientHandlerPool.shutdownNow();
        log("HostServer stopped.");
    }

    private void log(String message) {
        System.out.println(LocalDateTime.now().format(dtf) + " - " + message);
    }
}