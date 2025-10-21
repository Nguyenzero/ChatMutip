import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ChatClientController {
    @FXML private TextField txtServerIP;
    @FXML private TextField txtPort;
    @FXML private TextField txtUsername;
    @FXML private TextField txtMessage;
    @FXML private TextArea txtChatArea;
    @FXML private ListView<String> listOnlineUsers;
    @FXML private Button btnConnect, btnDisconnect, btnSend;

    private ChatClient client;

    @FXML
    public void initialize() {
        client = new ChatClient();
        btnDisconnect.setDisable(true);
        btnSend.setDisable(true);
    }

    @FXML
    public void handleConnect() {
        try {
            String ip = txtServerIP.getText().trim();
            int port = Integer.parseInt(txtPort.getText().trim());
            String username = txtUsername.getText().trim();

            client.connect(ip, port, username,
                    msg -> Platform.runLater(() -> txtChatArea.appendText(msg + "\n")),
                    users -> Platform.runLater(() -> listOnlineUsers.getItems().setAll(users))
            );

            txtChatArea.appendText("✅ Đã kết nối tới " + ip + ":" + port + "\n");
            btnConnect.setDisable(true);
            btnDisconnect.setDisable(false);
            btnSend.setDisable(false);
        } catch (Exception e) {
            txtChatArea.appendText("❌ Lỗi kết nối: " + e.getMessage() + "\n");
        }
    }

    @FXML
    public void handleSend() {
        String message = txtMessage.getText().trim();
        if (!message.isEmpty()) {
            client.send(message);
            txtChatArea.appendText("[Bạn]: " + message + "\n");
            txtMessage.clear();
        }
    }

    @FXML
    public void handleDisconnect() {
        client.disconnect();
        txtChatArea.appendText("🔌 Đã ngắt kết nối.\n");
        btnConnect.setDisable(false);
        btnDisconnect.setDisable(true);
        btnSend.setDisable(true);
        listOnlineUsers.getItems().clear();
    }
}
