package server;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ScreenStreamer implements Runnable {
    private final Socket clientSocket;
    private final ClientHandler clientHandler;
    private final Robot robot;
    private final Rectangle screenRect;

    public ScreenStreamer(Socket socket, ClientHandler handler) {
        this.clientSocket = socket;
        this.clientHandler = handler;
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Failed to create Robot for streaming", e);
        }
        this.screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    @Override
    public void run() {
        try (DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
            ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
            jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            jpgWriteParam.setCompressionQuality(HostServer.JPEG_QUALITY);

            while (clientHandler.isRunning() && !clientSocket.isClosed()) {
                long startTime = System.currentTimeMillis();
                try {
                    BufferedImage screenCapture = robot.createScreenCapture(screenRect);

                    BufferedImage imageToSend;
                    if (HostServer.SCALE_FACTOR < 1.0) {
                        int newWidth = (int) (screenCapture.getWidth() * HostServer.SCALE_FACTOR);
                        int newHeight = (int) (screenCapture.getHeight() * HostServer.SCALE_FACTOR);
                        imageToSend = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                        Graphics2D g = imageToSend.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.drawImage(screenCapture, 0, 0, newWidth, newHeight, null);
                        g.dispose();
                    } else {
                        imageToSend = screenCapture;
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try(ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                        jpgWriter.setOutput(ios);
                        jpgWriter.write(null, new IIOImage(imageToSend, null, null), jpgWriteParam);
                    }
                    byte[] imageData = baos.toByteArray();

                    dos.writeInt(imageData.length);
                    dos.write(imageData);
                    dos.flush();

                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long sleepTime = HostServer.FRAME_INTERVAL_MS - elapsedTime;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    break;
                }
            }
            jpgWriter.dispose();
        } catch (IOException e) {
            // Stream closed, which is expected
        }
    }
}