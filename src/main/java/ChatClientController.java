    import javafx.application.Platform;
    import javafx.fxml.FXML;
    import javafx.scene.control.*;

    public class ChatClientController {
        @FXML private TextField txtServerIP, txtPort, txtUsername, txtMessage;
        @FXML private TextArea txtChatArea;
        @FXML private ListView<String> listOnlineUsers;
        @FXML private ChoiceBox<String> choiceGroup;
        @FXML private Label lblCurrentGroup;
        @FXML private Button btnConnect, btnDisconnect, btnSend, btnJoin, btnLeave;

        private ChatClient client;
        private boolean connected = false;
        private boolean joinedGroup = false;

        @FXML
        public void initialize() {
            client = new ChatClient();
            choiceGroup.getItems().addAll("group1", "group2", "group3");
            btnDisconnect.setDisable(true);
            btnSend.setDisable(true);
            btnJoin.setDisable(true);
            btnLeave.setDisable(true);
            lblCurrentGroup.setText("Phòng hiện tại: Chung (Lobby)");
        }

        @FXML
        public void handleConnect() {
            try {
                String ip = txtServerIP.getText().trim();
                int port = Integer.parseInt(txtPort.getText().trim());
                String username = txtUsername.getText().trim();

                if (username.isEmpty()) {
                    txtChatArea.appendText("⚠️ Vui lòng nhập tên trước khi kết nối.\n");
                    return;
                }

                client.connect(ip, port, username,
                        msg -> Platform.runLater(() -> txtChatArea.appendText(msg + "\n")),
                        users -> Platform.runLater(() -> listOnlineUsers.getItems().setAll(users))
                );

                connected = true;
                btnConnect.setDisable(true);
                btnDisconnect.setDisable(false);
                btnJoin.setDisable(false);
                btnSend.setDisable(false); // ✅ cho phép gửi khi chưa vào group
                txtChatArea.appendText("✅ Đã kết nối tới server. Bạn đang ở phòng chung (Lobby).\n");

            } catch (Exception e) {
                txtChatArea.appendText("❌ Lỗi kết nối: " + e.getMessage() + "\n");
            }
        }

        @FXML
        public void handleJoinGroup() {
            if (!connected) {
                txtChatArea.appendText("⚠️ Chưa kết nối tới server.\n");
                return;
            }

            String group = choiceGroup.getValue();
            if (group == null) {
                txtChatArea.appendText("⚠️ Chưa chọn group.\n");
                return;
            }

            // gửi lệnh net send join group
            client.send("net join " + group);
            joinedGroup = true;
            lblCurrentGroup.setText("Phòng hiện tại: " + group);
            txtChatArea.appendText("🏠 Đã tham gia phòng: " + group + "\n");
            btnJoin.setDisable(true);
            btnLeave.setDisable(false);
        }

        @FXML
        public void handleLeaveGroup() {
            if (!joinedGroup) return;
            client.send("net leave");
            joinedGroup = false;
            lblCurrentGroup.setText("Phòng hiện tại: Chung (Lobby)");
            txtChatArea.appendText("🚪 Đã rời khỏi phòng, quay lại phòng chung.\n");
            btnJoin.setDisable(false);
            btnLeave.setDisable(true);
        }

        @FXML
        public void handleSend() {
            if (!connected) {
                txtChatArea.appendText("⚠️ Chưa kết nối tới server.\n");
                return;
            }

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
            connected = false;
            joinedGroup = false;
            lblCurrentGroup.setText("Chưa vào phòng");
            btnConnect.setDisable(false);
            btnDisconnect.setDisable(true);
            btnJoin.setDisable(true);
            btnLeave.setDisable(true);
            btnSend.setDisable(true);
            txtChatArea.appendText("🔌 Đã ngắt kết nối.\n");
            listOnlineUsers.getItems().clear();
        }
    }
