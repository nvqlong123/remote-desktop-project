package server;

import shared.Protocol;

import java.awt.*;
import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Robot robot;
    private final HostServer server;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, HostServer server) {
        this.clientSocket = socket;
        this.server = server;
        try {
            this.robot = new Robot();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Robot", e);
        }
    }

    @Override
    public void run() {
        Thread streamerThread = null;
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            if (!authenticate(reader, writer)) {
                server.log("Authentication failed for " + clientSocket.getRemoteSocketAddress());
                return;
            }
            server.log("Authentication successful for " + clientSocket.getRemoteSocketAddress());

            ScreenStreamer streamer = new ScreenStreamer(clientSocket, this);
            streamerThread = new Thread(streamer, "ScreenStreamer-" + clientSocket.getPort());
            streamerThread.start();

            String command;
            while (running && (command = reader.readLine()) != null) {
                processCommand(command);
            }

        } catch (IOException e) {
            // Connection lost is normal when client closes
        } finally {
            running = false;
            if (streamerThread != null) {
                streamerThread.interrupt();
            }
            try {
                clientSocket.close();
            } catch (IOException e) {  }
            server.log("Closed connection with " + clientSocket.getRemoteSocketAddress());
        }
    }

    public boolean isRunning() {
        return running;
    }

    private boolean authenticate(BufferedReader reader, PrintWriter writer) throws IOException {
        String authLine = reader.readLine();
        if (authLine != null && authLine.startsWith("AUTH ") && server.checkPassword(authLine.substring(5))) {
            writer.println("OK");
            return true;
        } else {
            writer.println("ERR");
            return false;
        }
    }

    private void processCommand(String command) {
        try {
            String[] parts = command.split(Protocol.SEPARATOR);
            String cmdType = parts[0];

            switch (cmdType) {
                case Protocol.MOUSE_MOVE:
                    robot.mouseMove(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    break;
                case Protocol.MOUSE_PRESS:
                    robot.mousePress(InputEvent.getMaskForButton(Integer.parseInt(parts[1])));
                    break;
                case Protocol.MOUSE_RELEASE:
                    robot.mouseRelease(InputEvent.getMaskForButton(Integer.parseInt(parts[1])));
                    break;
                case Protocol.KEY_PRESS:
                    robot.keyPress(Integer.parseInt(parts[1]));
                    break;
                case Protocol.KEY_RELEASE:
                    robot.keyRelease(Integer.parseInt(parts[1]));
                    break;
            }
        } catch (Exception e) {
            // Avoid flooding logs for minor command parse errors
        }
    }
}