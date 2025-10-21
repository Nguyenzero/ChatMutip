import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345; // cổng server
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();
            System.out.println("===============================================");
            System.out.println("🔵 Server đang chạy tại:");
            System.out.println("➡️  IP: " + localIP);
            System.out.println("➡️  Cổng: " + PORT);
            System.out.println("===============================================");

            ServerSocket serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName(localIP));

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Gửi tin nhắn tới tất cả
    static void broadcast(String message, String excludeUser) {
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!entry.getKey().equals(excludeUser)) {
                entry.getValue().send(message);
            }
        }
    }

    // Cập nhật danh sách người dùng
    static void updateUserList() {
        String list = "USERS:" + String.join(",", clients.keySet());
        for (ClientHandler ch : clients.values()) ch.send(list);
    }

    static class ClientHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private String clientIP;

        ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientIP = socket.getInetAddress().getHostAddress(); // lấy IP client
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                username = in.readLine();

                if (username == null || username.isEmpty()) {
                    socket.close();
                    return;
                }

                clients.put(username, this);

                // ✅ In ra server console kèm IP
                System.out.println("🟢 " + username + " đã kết nối từ IP: " + clientIP);

                // ✅ Gửi thông báo đến các user khác
                broadcast("💬 " + username + " (" + clientIP + ") đã tham gia phòng chat.", username);
                updateUserList();

                String msg;
                while ((msg = in.readLine()) != null) {
                    System.out.println("[" + username + "@" + clientIP + "]: " + msg);
                    broadcast("[" + username + "@" + clientIP + "]: " + msg, username);
                }

            } catch (IOException e) {
                System.out.println("🔴 " + username + " (" + clientIP + ") đã ngắt kết nối.");
            } finally {
                try {
                    clients.remove(username);
                    broadcast("❌ " + username + " (" + clientIP + ") đã rời phòng chat.", username);
                    updateUserList();
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        void send(String msg) {
            if (out != null) out.println(msg);
        }
    }
}
