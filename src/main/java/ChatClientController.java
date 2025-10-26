import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.application.Platform;

public class ChatClientController {
    @FXML private TextField txtServerIP, txtPort, txtUsername, txtMessage;
    @FXML private TextArea txtChatArea;
    @FXML private ListView<String> listOnlineUsers;
    @FXML private ChoiceBox<String> choiceGroup, choiceTarget;
    @FXML private Label lblCurrentGroup, lblTarget;
    @FXML private RadioButton rdoChatAll, rdoChatPrivate, rdoChatGroup;
    @FXML private ToggleGroup chatModeGroup;



    private ChatClient client;
    private String currentGroup = "";

    @FXML
    public void initialize() {
        choiceGroup.getItems().addAll("Nhóm A", "Nhóm B", "Nhóm C");
        txtServerIP.setText("192.168.1.100");
        txtPort.setText("1111");

        // ❌ Không chọn radio nào khi khởi động
        rdoChatAll.setSelected(false);
        rdoChatPrivate.setSelected(false);
        rdoChatGroup.setSelected(false);

        choiceTarget.setVisible(false);
        lblTarget.setVisible(false);

        // ✅ Khởi tạo ToggleGroup trong controller
        chatModeGroup = new ToggleGroup();
        rdoChatAll.setToggleGroup(chatModeGroup);
        rdoChatPrivate.setToggleGroup(chatModeGroup);
        rdoChatGroup.setToggleGroup(chatModeGroup);

        // ✅ Cho phép bỏ chọn radio bằng cách click lại
        makeRadioToggleable(rdoChatAll);
        makeRadioToggleable(rdoChatPrivate);
        makeRadioToggleable(rdoChatGroup);
    }

    /**
     * Hàm này giúp RadioButton có thể "bỏ chọn" khi click lại.
     */
    private void makeRadioToggleable(RadioButton radio) {
        radio.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            if (radio.isSelected()) {
                // Nếu đang chọn -> bỏ chọn
                radio.setSelected(false);
                chatModeGroup.selectToggle(null);
                handleChatModeChange(); // ẩn các mục target khi bỏ chọn
                e.consume();
            }
        });
    }


    // 🔹 Kết nối tới server
    @FXML
    private void handleConnect() {
        try {
            client = new ChatClient(this);
            client.connect(
                    txtServerIP.getText(),
                    Integer.parseInt(txtPort.getText()),
                    txtUsername.getText()
            );
            txtChatArea.appendText("✅ Kết nối server thành công!\n");
        } catch (Exception e) {
            txtChatArea.appendText("❌ Kết nối thất bại!\n");
        }
    }

    @FXML
    private void handleDisconnect() {
        if (client != null) client.disconnect();
        txtChatArea.appendText("🔴 Đã ngắt kết nối.\n");
    }

    // 🔹 Gửi tin nhắn
    @FXML
    private void handleSend() {
        if (client == null) return;
        String msg = txtMessage.getText().trim();
        if (msg.isEmpty()) return;

        String mode, target = "";

        if (rdoChatPrivate.isSelected()) {
            mode = "PRIVATE";
            target = choiceTarget.getValue();
            if (target == null || target.isEmpty()) {
                txtChatArea.appendText("⚠️ Chưa chọn người nhận!\n");
                return;
            }
        } else if (rdoChatGroup.isSelected()) {
            mode = "GROUP";
            target = choiceTarget.getValue();
            if (target == null || target.isEmpty()) {
                txtChatArea.appendText("⚠️ Chưa chọn nhóm!\n");
                return;
            }
        } else {
            mode = "ALL";
        }

        client.send(mode, target, msg);
        txtMessage.clear();
    }

    // 🔹 Tham gia nhóm
    @FXML
    private void handleJoinGroup() {
        if (client == null) return;
        String group = choiceGroup.getValue();
        if (group == null) return;
        currentGroup = group;
        client.joinGroup(group);
        lblCurrentGroup.setText("Đang ở nhóm: " + group);
    }

    // 🔹 Rời nhóm
    @FXML
    private void handleLeaveGroup() {
        if (client == null || currentGroup.isEmpty()) return;
        client.leaveGroup(currentGroup);
        lblCurrentGroup.setText("Chưa vào phòng");
        currentGroup = "";
    }

    // 🔹 Khi đổi chế độ chat (Chung / Riêng / Nhóm)
    @FXML
    private void handleChatModeChange() {
        Toggle selected = chatModeGroup.getSelectedToggle();

        if (selected == null) {
            // Không chọn chế độ nào -> ẩn mục chọn người / nhóm
            lblTarget.setVisible(false);
            choiceTarget.setVisible(false);
            return;
        }

        if (rdoChatAll.isSelected()) {
            lblTarget.setVisible(false);
            choiceTarget.setVisible(false);
        } else if (rdoChatPrivate.isSelected()) {
            lblTarget.setVisible(true);
            choiceTarget.setVisible(true);
            lblTarget.setText("Người nhận:");
            if (client != null) {
                choiceTarget.getItems().setAll(client.getOnlineUsersExceptSelf());
            }
        } else if (rdoChatGroup.isSelected()) {
            lblTarget.setVisible(true);
            choiceTarget.setVisible(true);
            lblTarget.setText("Nhóm:");
            choiceTarget.getItems().setAll(choiceGroup.getItems());
        }
    }


    // 🔹 Cập nhật UI từ ChatClient
    public void appendMessage(String msg) {
        Platform.runLater(() -> txtChatArea.appendText(msg + "\n"));
    }

    public void updateOnlineUsers(java.util.List<String> users) {
        Platform.runLater(() -> {
            listOnlineUsers.getItems().setAll(users);
        });
    }
}
