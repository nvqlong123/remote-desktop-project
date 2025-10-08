//package UI;
//
//import javax.swing.*;
//import java.awt.*;
//
//class RemoteControlFrame extends JFrame {
//    public RemoteControlFrame() {
//        setTitle("Remote Control");
//        setSize(1000, 700);
//        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        setLayout(new BorderLayout());
//
//        JMenuBar menuBar = new JMenuBar();
//        JMenu actionsMenu = new JMenu("Actions");
//
//        JMenuItem remoteReboot = new JMenuItem("Remote reboot");
//        JMenuItem transferFile = new JMenuItem("Transfer File");
//        JMenuItem chat = new JMenuItem("Chat");
//        JMenuItem endSession = new JMenuItem("End Session");
//
//        remoteReboot.addActionListener(e -> JOptionPane.showMessageDialog(this, "Remote reboot triggered"));
//        transferFile.addActionListener(e -> JOptionPane.showMessageDialog(this, "Transfer File opened"));
//        chat.addActionListener(e -> JOptionPane.showMessageDialog(this, "Chat window opened"));
//        endSession.addActionListener(e -> {
//            dispose();
//            System.exit(0);
//        });
//
//        actionsMenu.add(remoteReboot);
//        actionsMenu.add(transferFile);
//        actionsMenu.add(chat);
//        actionsMenu.add(endSession);
//
//        JMenu viewMenu = new JMenu("View");
//
//
//        menuBar.add(actionsMenu);
//        menuBar.add(viewMenu);
//        setJMenuBar(menuBar);
//
//
//
//        JLabel screenDisplay = new JLabel();
//        screenDisplay.setHorizontalAlignment(SwingConstants.CENTER);
//
//        screenDisplay.setText("Streamed Host Screen Here (Update continuously via socket/thread)");
//        screenDisplay.setBackground(Color.WHITE);
//        screenDisplay.setOpaque(true);
//        add(screenDisplay, BorderLayout.CENTER);
//
//
//
//
//
//        JPanel taskbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
//        taskbar.setBackground(new Color(200, 200, 255));
//        taskbar.setPreferredSize(new Dimension(1000, 40));
//        JButton startButton = new JButton("Start");
//        taskbar.add(startButton);
//        JLabel timeLabel = new JLabel("9/18/2025 9:50 AM");
//        taskbar.add(Box.createHorizontalGlue());
//        taskbar.add(timeLabel);
//        add(taskbar, BorderLayout.SOUTH);
//    }
//}
