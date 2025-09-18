package UI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class RemoteDesktopApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ConnectFrame connectFrame = new ConnectFrame();
            connectFrame.setVisible(true);
        });
    }
}



