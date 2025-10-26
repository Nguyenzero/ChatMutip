import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 1111;
    private static Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static Map<String, Set<String>> groups = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            String localIP = getLocalIPv4Address();
            if (localIP == null) {
                System.out.println("⚠️ Không tìm thấy địa chỉ IPv4 Wi-Fi, dùng localhost (127.0.0.1)");
                localIP = "127.0.0.1";
            }

            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("🌐 Server đang chạy tại: " + localIP + ":" + PORT);
            System.out.println("✅ Đang lắng nghe kết nối từ client...");

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 🔹 Lấy địa chỉ IPv4 Wi-Fi
    private static String getLocalIPv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                if (!ni.getDisplayName().toLowerCase().contains("wi")) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    // 🔹 Gửi tin chung
    static synchronized void broadcast(String message, String excludeUser) {
        for (ClientHandler c : clients.values()) {
            if (!c.username.equals(excludeUser)) {
                c.sendMessage(message);
            }
        }
    }

    // 🔹 Gửi tin riêng
    static synchronized void sendPrivate(String toUser, String message) {
        ClientHandler c = clients.get(toUser);
        if (c != null) c.sendMessage(message);
    }

    // 🔹 Gửi tin nhóm — chỉ các thành viên trong nhóm nhận được
    static synchronized void sendGroup(String sender, String group, String message) {
        Set<String> members = groups.get(group);

        // Nếu nhóm chưa tồn tại hoặc rỗng
        if (members == null || members.isEmpty()) {
            ClientHandler c = clients.get(sender);
            if (c != null)
                c.sendMessage("⚠️ Nhóm '" + group + "' hiện chưa có thành viên nào.");
            return;
        }

        // Gửi tin cho các thành viên trong nhóm
        for (String user : members) {
            ClientHandler c = clients.get(user);
            if (c != null) {
                c.sendMessage("👥 [" + sender + " gửi tới nhóm " + group + "]: " + message);
            }
        }

        // Người gửi (dù không ở trong nhóm) vẫn thấy tin mình gửi
        ClientHandler senderClient = clients.get(sender);
        if (senderClient != null) {
            senderClient.sendMessage("📨 [Bạn gửi tới nhóm " + group + "]: " + message);
        }
    }

    static synchronized void joinGroup(String user, String group) {
        groups.computeIfAbsent(group, g -> new HashSet<>()).add(user);
    }

    static synchronized void leaveGroup(String user, String group) {
        if (groups.containsKey(group)) {
            groups.get(group).remove(user);
        }
    }

    static synchronized void removeUser(String username) {
        clients.remove(username);
        for (Set<String> s : groups.values()) s.remove(username);
    }

    static synchronized String getOnlineList() {
        return String.join(",", clients.keySet());
    }

    // ==========================
    // 🔹 Lớp xử lý từng Client
    // ==========================
    static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        String username;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

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
                System.out.println("👤 " + username + " đã kết nối từ " + socket.getInetAddress());
                broadcast("🔵 " + username + " đã tham gia!", username);
                updateUserList();

                String msg;
                while ((msg = in.readLine()) != null) {
                    handleMessage(msg);
                }
            } catch (IOException e) {
                System.out.println("⚠️ " + username + " ngắt kết nối.");
            } finally {
                removeUser(username);
                broadcast("🔴 " + username + " đã rời phòng.", username);
                updateUserList();
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void handleMessage(String msg) {
            try {
                String[] parts = msg.split("\\|", 3);
                String mode = parts[0];
                String target = parts[1];
                String text = parts.length > 2 ? parts[2] : "";

                switch (mode) {
                    case "ALL" -> broadcast("💬 [" + username + "]: " + text, username);
                    case "PRIVATE" -> sendPrivate(target, "💌 [Từ " + username + "]: " + text);
                    case "GROUP" -> sendGroup(username, target, text);
                    case "JOIN" -> {
                        joinGroup(username, target);
                        sendMessage("📢 Bạn đã tham gia nhóm: " + target);
                    }
                    case "LEAVE" -> {
                        leaveGroup(username, target);
                        sendMessage("📢 Bạn đã rời nhóm: " + target);
                    }
                    default -> sendMessage("⚠️ Lệnh không hợp lệ: " + mode);
                }
            } catch (Exception e) {
                sendMessage("⚠️ Lỗi định dạng tin nhắn: " + msg);
            }
        }

        private void updateUserList() {
            String userList = "USERS|" + getOnlineList();
            for (ClientHandler c : clients.values()) {
                c.sendMessage(userList);
            }
        }

        void sendMessage(String msg) {
            out.println(msg);
        }
    }
}
