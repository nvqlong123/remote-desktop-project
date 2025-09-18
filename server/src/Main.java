

public class Main {
    public static void main(String[] args) throws Exception {
        int port = 5000;
        String password = "demo123"; // mặc định password
        if (args.length >= 1) password = args[0];
        HostServer server = new HostServer(port, password);
        System.out.println("Starting HostServer on port " + port + " with password: " + password);
        server.start();
    }
}
