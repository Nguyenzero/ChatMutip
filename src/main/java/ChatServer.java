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

    // =======================================================
    // 🔸 1A. Gửi tin chung — chỉ người cùng phạm vi hoặc cùng nhóm
    // =======================================================
    static synchronized void broadcast(String message, String sender) {
        String senderGroup = getUserGroup(sender);

        for (ClientHandler c : clients.values()) {
            if (c.username.equals(sender)) continue;
            String targetGroup = getUserGroup(c.username);

            // Người ngoài nhóm chỉ thấy người ngoài nhóm
            if (senderGroup == null && targetGroup == null) {
                c.sendMessage(message);
            }
            // Người trong nhóm chỉ thấy người cùng nhóm
            else if (senderGroup != null && senderGroup.equals(targetGroup)) {
                c.sendMessage(message);
            }
        }
    }

    // =======================================================
    // 🔸 1B. Gửi tin toàn server — ai cũng nhận
    // =======================================================
    static synchronized void broadcastAll(String message, String sender) {
        for (ClientHandler c : clients.values()) {
            if (!c.username.equals(sender)) {
                c.sendMessage(message);
            }
        }
    }

    // =======================================================
    // 🔸 2. Gửi tin riêng
    // =======================================================
    static synchronized void sendPrivate(String toUser, String message) {
        ClientHandler c = clients.get(toUser);
        if (c != null) c.sendMessage(message);
    }

    // =======================================================
    // 🔸 3. Gửi tin nhóm — gửi tới mọi người trong nhóm được chọn
    //     (dù người gửi có ở trong nhóm hay không)
    // =======================================================
    static synchronized void sendGroup(String sender, String group, String message) {
        Set<String> members = groups.get(group);

        if (members == null || members.isEmpty()) {
            ClientHandler c = clients.get(sender);
            if (c != null)
                c.sendMessage("⚠️ Nhóm '" + group + "' chưa có thành viên nào.");
            return;
        }

        // ⚡ Không còn chặn người ngoài nhóm gửi tin nữa!
        for (String user : members) {
            ClientHandler c = clients.get(user);
            if (c != null) {
                if (user.equals(sender))
                    c.sendMessage("📨 [Bạn -> Nhóm " + group + "]: " + message);
                else
                    c.sendMessage("👥 [" + sender + " -> Nhóm " + group + "]: " + message);
            }
        }
    }

    // =======================================================
    // 🔸 4. Quản lý nhóm
    // =======================================================
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

    static synchronized String getUserGroup(String username) {
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            if (entry.getValue().contains(username))
                return entry.getKey();
        }
        return null;
    }

    // =======================================================
    // 🔸 5. Lớp xử lý client
    // =======================================================
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
                    case "ALL" -> {
                        // Nếu có tiền tố [GLOBAL] thì gửi toàn server
                        if (text.startsWith("[GLOBAL]")) {
                            String cleanMsg = text.substring(8).trim();
                            broadcastAll("🌍 [Toàn Server] [" + username + "]: " + cleanMsg, username);
                        } else {
                            broadcast("💬 [" + username + "]: " + text, username);
                        }
                    }
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
