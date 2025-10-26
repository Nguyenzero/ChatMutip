import java.io.*;
import java.net.*;
import java.util.*;
import javafx.application.Platform;

public class ChatClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private ChatClientController controller;
    private String username;
    private List<String> onlineUsers = new ArrayList<>();

    public ChatClient(ChatClientController controller) {
        this.controller = controller;
    }

    public void connect(String host, int port, String username) throws IOException {
        this.username = username;
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        out.println(username); // gửi tên người dùng đầu tiên

        new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    processMessage(msg);
                }
            } catch (IOException e) {
                controller.appendMessage("❌ Mất kết nối server.");
            }
        }).start();
    }

    private void processMessage(String msg) {
        if (msg.startsWith("USERS|")) {
            String[] users = msg.substring(6).split(",");
            onlineUsers = Arrays.asList(users);
            controller.updateOnlineUsers(onlineUsers);
        } else {
            controller.appendMessage(msg);
        }
    }

    public void send(String mode, String target, String message) {
        out.println(mode + "|" + target + "|" + message);
    }

    public void joinGroup(String group) {
        out.println("JOIN|" + group + "|");
    }

    public void leaveGroup(String group) {
        out.println("LEAVE|" + group + "|");
    }

    public void disconnect() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    // 🔹 Lấy danh sách người online (trừ bản thân)
    public List<String> getOnlineUsersExceptSelf() {
        List<String> filtered = new ArrayList<>(onlineUsers);
        filtered.remove(username);
        return filtered;
    }
}
