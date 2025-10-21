import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class ChatClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public void connect(String serverIP, int port, String username,
                        Consumer<String> onMessage, Consumer<String[]> onUserList) throws IOException {

        socket = new Socket(serverIP, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        out.println(username);
        out.println("null"); // bắt đầu ở Lobby

        Thread listener = new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    if (msg.startsWith("USERS:")) {
                        onUserList.accept(msg.substring(6).split(","));
                    } else {
                        onMessage.accept(msg);
                    }
                }
            } catch (IOException e) {
                onMessage.accept("⚠️ Mất kết nối với server.");
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    public void send(String msg) {
        if (out != null) out.println(msg);
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }
}
