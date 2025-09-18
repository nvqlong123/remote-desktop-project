package UI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class ConnectFrame extends JFrame {
    public ConnectFrame() {
        setTitle("Remote Desktop");
        setSize(800, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(1, 2));

        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBackground(new Color(200, 200, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel allowLabel = new JLabel("Allow remote control");
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        leftPanel.add(allowLabel, gbc);

        JLabel yourIdLabel = new JLabel("Your ID");
        gbc.gridy = 1; gbc.gridwidth = 1;
        leftPanel.add(yourIdLabel, gbc);

        JTextField yourIdField = new JTextField("524 514 476");
        yourIdField.setEditable(false);
        gbc.gridx = 1;
        leftPanel.add(yourIdField, gbc);

        JLabel passwordLabel = new JLabel("Password");
        gbc.gridx = 0; gbc.gridy = 2;
        leftPanel.add(passwordLabel, gbc);

        JTextField passwordField = new JTextField("6mehHx5f");
        passwordField.setEditable(false);
        gbc.gridx = 1;
        leftPanel.add(passwordField, gbc);

        add(leftPanel);

        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBackground(new Color(200, 200, 255));

        JLabel controlLabel = new JLabel("Control remote device");
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        rightPanel.add(controlLabel, gbc);

        JLabel participantIdLabel = new JLabel("Participant's ID");
        gbc.gridy = 1; gbc.gridwidth = 1;
        rightPanel.add(participantIdLabel, gbc);

        JTextField participantIdField = new JTextField(20);
        gbc.gridx = 1;
        rightPanel.add(participantIdField, gbc);

        JButton connectButton = new JButton("Connect");
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        rightPanel.add(connectButton, gbc);

        add(rightPanel);

        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
                RemoteControlFrame remoteFrame = new RemoteControlFrame();
                remoteFrame.setVisible(true);
            }
        });
    }
}

