package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HostServer {
    public static final float JPEG_QUALITY = 0.75f;
    public static final double SCALE_FACTOR = 1.0;
    public static final int FRAME_INTERVAL_MS = 66;

    private final int port;
    private final String password;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private final ExecutorService clientHandlerPool = Executors.newCachedThreadPool();
    private final HostUI.LogAppender logAppender;
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HostServer(int port, String password, HostUI.LogAppender logAppender) {
        this.port = port;
        this.password = password;
        this.logAppender = logAppender;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        log("HostServer is listening on port " + port);
        log(String.format("Optimization: Quality=%.2f, Scale=%.2f, Target FPS=%d",
                JPEG_QUALITY, SCALE_FACTOR, 1000 / FRAME_INTERVAL_MS));

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                log("New connection from: " + clientSocket.getRemoteSocketAddress());
                clientHandlerPool.submit(new ClientHandler(clientSocket, this));
            } catch (IOException e) {
                if (running) {
                    log("Error accepting client connection: " + e.getMessage());
                }
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
        clientHandlerPool.shutdownNow();
        log("HostServer stopped.");
    }

    public boolean checkPassword(String clientPassword) {
        return this.password.equals(clientPassword);
    }

    public void log(String message) {
        String logMessage = LocalDateTime.now().format(dtf) + " - " + message;
        if (logAppender != null) {
            logAppender.log(logMessage);
        } else {
            System.out.println(logMessage);
        }
    }
}