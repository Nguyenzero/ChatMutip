import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 1111;
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Map<String, Set<ClientHandler>> groups = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("🔵 Server đang chạy tại: " + InetAddress.getLocalHost().getHostAddress() + ":" + PORT);
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ✅ Gửi đến tất cả người trong cùng phòng chờ (Lobby)
    static synchronized void broadcastLobby(String message, String excludeUser) {
        for (ClientHandler ch : clients.values()) {
            if ((ch.group == null || ch.group.equals("null") || ch.group.isEmpty()) && !ch.username.equals(excludeUser)) {
                ch.send(message);
            }
        }
    }

    // ✅ Gửi đến tất cả trong group
    static synchronized void sendToGroup(String group, String message, String excludeUser) {
        Set<ClientHandler> members = groups.get(group);
        if (members != null) {
            for (ClientHandler ch : members) {
                if (!ch.username.equals(excludeUser)) {
                    ch.send(message);
                }
            }
        }
    }

    // ✅ Gửi đến tất cả toàn server
    static synchronized void broadcastAll(String message, String excludeUser) {
        for (ClientHandler ch : clients.values()) {
            if (!ch.username.equals(excludeUser)) {
                ch.send(message);
            }
        }
    }

    static synchronized void updateUserList() {
        for (ClientHandler ch : clients.values()) {
            List<String> visibleUsers = new ArrayList<>();

            if (ch.group == null || ch.group.equals("null") || ch.group.isEmpty()) {
                // Người ở Lobby -> thấy ai cũng đang ở Lobby
                for (ClientHandler c : clients.values()) {
                    if (c != ch && (c.group == null || c.group.equals("null") || c.group.isEmpty())) {
                        visibleUsers.add(c.username);
                    }
                }
            } else {
                // Người trong group -> chỉ thấy người cùng group
                Set<ClientHandler> members = groups.get(ch.group);
                if (members != null) {
                    for (ClientHandler c : members) {
                        if (c != ch) visibleUsers.add(c.username);
                    }
                }
            }

            ch.send("USERS:" + String.join(",", visibleUsers));
        }
    }


    static class ClientHandler extends Thread {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private String group;
        private final String clientIP;

        ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientIP = socket.getInetAddress().getHostAddress();
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                username = in.readLine();
                group = in.readLine();

                if (username == null || username.isEmpty()) {
                    socket.close();
                    return;
                }

                clients.put(username, this);
                broadcastLobby("🟢 " + username + " (" + clientIP + ") đã kết nối đến server.", username);

                if (group != null && !group.equals("null") && !group.isEmpty()) {
                    joinGroup(group);
                } else {
                    System.out.println("🟢 " + username + " vào phòng chờ (Lobby)");
                    broadcastLobby("💬 " + username + " đã tham gia phòng chờ", username);
                }

                updateUserList();

                String msg;
                while ((msg = in.readLine()) != null) {
                    // Lệnh đặc biệt
                    if (msg.startsWith("net ")) {
                        handleCommand(msg);
                        continue;
                    }

                    // ✅ Chat bình thường
                    if (group == null || group.isEmpty() || group.equals("null")) {
                        broadcastLobby("[" + username + "@" + clientIP + "]: " + msg, username);
                    } else {
                        sendToGroup(group, "[" + username + "@" + clientIP + "]: " + msg, username);
                    }
                }

            } catch (IOException e) {
                System.out.println("🔴 " + username + " (" + clientIP + ") đã ngắt kết nối.");
            } finally {
                disconnectClient();
            }
        }

        // ✅ Xử lý lệnh net send / net join / net leave
        private void handleCommand(String cmd) {
            String[] parts = cmd.split(" ", 4);

            if (parts.length >= 2 && parts[1].equalsIgnoreCase("join")) {
                if (parts.length < 3) {
                    send("⚠️ Dùng: net join <group>");
                    return;
                }
                joinGroup(parts[2]);
                return;
            }

            if (parts.length >= 2 && parts[1].equalsIgnoreCase("leave")) {
                leaveGroup();
                return;
            }

            if (parts.length < 4 || !parts[1].equalsIgnoreCase("send")) {
                send("⚠️ Sai cú pháp. Dùng: net send {IP|group|*} message");
                return;
            }

            String target = parts[2];
            String message = parts[3];

            if (target.equals("*")) {
                broadcastAll("[GLOBAL][" + username + "@" + clientIP + "]: " + message, username);
            } else if (groups.containsKey(target)) {
                sendToGroup(target, "[GROUP][" + username + "@" + clientIP + "]: " + message, username);
            } else {
                // Gửi riêng theo IP
                for (ClientHandler ch : clients.values()) {
                    if (ch.clientIP.equals(target)) {
                        ch.send("[PRIVATE][" + username + "@" + clientIP + "]: " + message);
                        send("📤 Đã gửi riêng đến " + target);
                        return;
                    }
                }
                send("⚠️ Không tìm thấy người nhận: " + target);
            }
        }

        private void joinGroup(String newGroup) {
            // Rời group cũ nếu có
            if (group != null && groups.containsKey(group)) {
                groups.get(group).remove(this);
                sendToGroup(group, "❌ " + username + " đã rời nhóm " + group, username);
            }

            group = newGroup;
            groups.computeIfAbsent(group, g -> ConcurrentHashMap.newKeySet()).add(this);
            sendToGroup(group, "💬 " + username + " đã tham gia nhóm " + group, username);
            System.out.println("🟢 " + username + " vào nhóm " + group);
        }

        private void leaveGroup() {
            if (group != null && groups.containsKey(group)) {
                groups.get(group).remove(this);
                sendToGroup(group, "🚪 " + username + " đã rời nhóm " + group, username);
                broadcastLobby("💬 " + username + " quay lại phòng chờ", username);
            }
            group = null;
            send("🔙 Bạn đã quay lại phòng chờ (Lobby)");
        }

        private void disconnectClient() {
            try {
                clients.remove(username);
                if (group != null && groups.containsKey(group)) {
                    groups.get(group).remove(this);
                    sendToGroup(group, "❌ " + username + " rời nhóm " + group, username);
                } else {
                    broadcastLobby("❌ " + username + " rời phòng chờ.", username);
                }
                updateUserList();
                socket.close();
            } catch (IOException ignored) {}
        }

        void send(String msg) {
            if (out != null) out.println(msg);
        }
    }
}
