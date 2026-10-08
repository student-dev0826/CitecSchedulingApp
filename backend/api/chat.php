<?php
// ==========================================
// CITEC Scheduling System - Dedicated Chat API
// ==========================================

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST, GET");
header("Access-Control-Allow-Headers: Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");

require_once __DIR__ . '/config/database.php';

$pdo = getDatabaseConnection();

// Ensure chat_messages table exists
try {
    $pdo->exec("CREATE TABLE IF NOT EXISTS chat_messages (
        message_id INT AUTO_INCREMENT PRIMARY KEY,
        sender_id VARCHAR(50) NOT NULL,
        sender_name VARCHAR(100) NOT NULL,
        sender_role VARCHAR(20) NOT NULL,
        recipient_id VARCHAR(50) NOT NULL,
        recipient_name VARCHAR(100) NOT NULL,
        message_text TEXT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
} catch (Exception $e) {
    // Table already exists or creation ignored
}

// Read raw POST body JSON or $_POST form-data
$input = file_get_contents("php://input");
$data = json_decode($input, true);
if (!$data || !is_array($data)) {
    $data = array_merge($_GET, $_POST);
}

$action = isset($data['action']) ? strtolower(trim($data['action'])) : 'fetch';

if ($action === 'send' || ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($data['message_text']))) {
    $sender_id      = isset($data['sender_id']) ? trim($data['sender_id']) : '';
    $sender_name    = isset($data['sender_name']) ? trim($data['sender_name']) : '';
    $sender_role    = isset($data['sender_role']) ? trim($data['sender_role']) : '';
    $recipient_id   = isset($data['recipient_id']) ? trim($data['recipient_id']) : '';
    $recipient_name = isset($data['recipient_name']) ? trim($data['recipient_name']) : '';
    $message_text   = isset($data['message_text']) ? trim($data['message_text']) : '';

    if (empty($sender_id) || empty($recipient_id) || empty($message_text)) {
        echo json_encode(["success" => false, "message" => "Sender, recipient, and message text are required."]);
        exit();
    }

    try {
        $stmt = $pdo->prepare("INSERT INTO chat_messages (sender_id, sender_name, sender_role, recipient_id, recipient_name, message_text)
                               VALUES (:sender_id, :sender_name, :sender_role, :recipient_id, :recipient_name, :message_text)");
        $success = $stmt->execute([
            ':sender_id'      => $sender_id,
            ':sender_name'    => $sender_name,
            ':sender_role'    => $sender_role,
            ':recipient_id'   => $recipient_id,
            ':recipient_name' => $recipient_name,
            ':message_text'   => $message_text
        ]);

        if ($success) {
            echo json_encode(["success" => true, "message" => "Message sent successfully."]);
        } else {
            echo json_encode(["success" => false, "message" => "Failed to send message."]);
        }
    } catch (PDOException $e) {
        echo json_encode(["success" => false, "message" => "Database error: " . $e->getMessage()]);
    }

} else {
    // Fetch Conversation
    $user1 = isset($data['user1']) ? trim($data['user1']) : (isset($_GET['user1']) ? trim($_GET['user1']) : '');
    $user2 = isset($data['user2']) ? trim($data['user2']) : (isset($_GET['user2']) ? trim($_GET['user2']) : '');

    if (empty($user1) || empty($user2)) {
        echo json_encode(["success" => false, "message" => "User parameters missing."]);
        exit();
    }

    try {
        $stmt = $pdo->prepare("SELECT message_id, sender_id, sender_name, sender_role, recipient_id, recipient_name, message_text, DATE_FORMAT(created_at, '%h:%i %p') AS timestamp
                               FROM chat_messages
                               WHERE (sender_id = :u1 AND recipient_id = :u2) OR (sender_id = :u2 AND recipient_id = :u1)
                               ORDER BY message_id ASC");
        $stmt->execute([':u1' => $user1, ':u2' => $user2]);
        $messages = $stmt->fetchAll(PDO::FETCH_ASSOC);

        echo json_encode([
            "success" => true,
            "data" => $messages,
            "messages" => $messages
        ]);
    } catch (PDOException $e) {
        echo json_encode(["success" => false, "data" => [], "messages" => []]);
    }
}
?>
