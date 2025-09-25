package Server;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class AdvancedHostServer {
    private final int port;
    private final String password;
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private List<Rectangle> excludeAreas = new ArrayList<>();

    public AdvancedHostServer(int port, String password) {
        this.port = port;
        this.password = password;
    }

    public void start() throws Exception {
        serverSocket = new ServerSocket(port);

        System.out.println("=== Advanced HostServer Started ===");
        System.out.println("Port: " + port);
        System.out.println("Password: " + password);
        System.out.println("Local IP: " + InetAddress.getLocalHost().getHostAddress());
        System.out.println("Localhost test: 127.0.0.1:" + port);
        System.out.println("===================================");

        while (running) {
            try {
                Socket client = serverSocket.accept();
                String clientIP = client.getInetAddress().getHostAddress();
                System.out.println("Client connected from: " + clientIP);

                new Thread(() -> handleClient(client)).start();
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting client: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket s) {
        String clientIP = s.getInetAddress().getHostAddress();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter pw = new PrintWriter(s.getOutputStream(), true);
             OutputStream out = s.getOutputStream()) {

            String authLine = br.readLine();
            System.out.println("Auth attempt from " + clientIP + ": " + authLine);

            if (authLine == null || !authLine.startsWith("AUTH ")) {
                pw.println("ERR Invalid auth format");
                return;
            }

            String receivedPassword = authLine.substring(5).trim();
            if (!receivedPassword.equals(password)) {
                pw.println("ERR Wrong password");
                return;
            }

            pw.println("OK");
            System.out.println("Auth successful for " + clientIP);

            startAdvancedScreenStreaming(s, out, clientIP);

        } catch (Exception ex) {
            System.out.println("Client " + clientIP + " disconnected: " + ex.getMessage());
        } finally {
            try { s.close(); } catch (Exception e) {}
        }
    }

    private void startAdvancedScreenStreaming(Socket socket, OutputStream out, String clientIP) throws Exception {
        DataOutputStream dos = new DataOutputStream(out);
        Robot robot = new Robot();

        boolean isLocalhost = isLocalhostConnection(clientIP);
        Rectangle captureRect = calculateCaptureArea(isLocalhost);

        System.out.println("Streaming mode: " + (isLocalhost ? "LOCALHOST TEST" : "REMOTE"));
        System.out.println("Capture area: " + captureRect);

        int frameCount = 0;
        long startTime = System.currentTimeMillis();

        while (!socket.isClosed() && running) {
            try {
                BufferedImage img = robot.createScreenCapture(captureRect);

                // Apply localhost-specific processing
                if (isLocalhost) {
                    img = processLocalhostImage(img);
                }

                byte[] data = compressImage(img);

                dos.writeInt(data.length);
                dos.write(data);
                dos.flush();

                frameCount++;
                if (frameCount % 30 == 0) {
                    logStats(clientIP, frameCount, startTime, data.length);
                }

                Thread.sleep(isLocalhost ? 600 : 400); // Slower for localhost to reduce load

            } catch (IOException e) {
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("Stream ended for " + clientIP + " - Total frames: " + frameCount);
    }

    private boolean isLocalhostConnection(String clientIP) {
        return clientIP.equals("127.0.0.1") ||
                clientIP.equals("0:0:0:0:0:0:0:1") ||
                clientIP.equals("localhost") ||
                clientIP.startsWith("192.168.") || // Local network
                clientIP.startsWith("10.") ||       // Local network
                clientIP.startsWith("172.");        // Local network
    }

    private Rectangle calculateCaptureArea(boolean isLocalhost) {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

        if (isLocalhost) {
            // Strategy 1: Capture specific area that excludes where RemoteControl window typically appears
            int width = screenSize.width;
            int height = (int)(screenSize.height * 0.6); // Top 60% of screen
            return new Rectangle(0, 0, width, height);
        } else {
            // Full screen for remote connections
            return new Rectangle(screenSize);
        }
    }

    private BufferedImage processLocalhostImage(BufferedImage img) {
        // Add visual indicator for localhost testing
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Add "TEST MODE" watermark
        g2d.setColor(new Color(255, 0, 0, 100)); // Semi-transparent red
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.drawString("LOCALHOST TEST MODE", 20, 30);

        // Add border to show capture area
        g2d.setColor(Color.RED);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRect(5, 5, img.getWidth()-10, img.getHeight()-10);

        g2d.dispose();
        return img;
    }

    private byte[] compressImage(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var writers = ImageIO.getImageWritersByFormatName("jpg");

        if (writers.hasNext()) {
            var writer = writers.next();
            var writeParam = writer.getDefaultWriteParam();
            writeParam.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(0.75f);

            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new javax.imageio.IIOImage(img, null, null), writeParam);
            writer.dispose();
        } else {
            ImageIO.write(img, "jpg", baos);
        }

        return baos.toByteArray();
    }

    private void logStats(String clientIP, int frameCount, long startTime, int dataSize) {
        long elapsed = System.currentTimeMillis() - startTime;
        double fps = frameCount * 1000.0 / elapsed;
        System.out.printf("[%s] Frame: %d, FPS: %.1f, Size: %d KB%n",
                clientIP, frameCount, fps, dataSize / 1024);
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {}
    }

    public static void main(String[] args) {
        System.out.println("=== LOCALHOST TESTING GUIDE ===");
        System.out.println("✓ Server captures only TOP 60% of screen");
        System.out.println("✓ Place test apps in TOP area");
        System.out.println("✓ Place Remote Control window in BOTTOM area");
        System.out.println("✓ Red border shows capture area");
        System.out.println("✓ 'TEST MODE' watermark indicates localhost");
        System.out.println("===============================\n");

        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8888;
        String password = args.length > 1 ? args[1] : "123456";

        AdvancedHostServer server = new AdvancedHostServer(port, password);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        try {
            server.start();
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}