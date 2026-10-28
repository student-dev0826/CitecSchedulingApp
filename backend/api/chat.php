<?php
// ==========================================
// CITEC Scheduling System - Chat API (server-backed, with unread tracking)
// Upload next to schedules.php / login.php. Tables/columns upgrade automatically.
// Actions: send | fetch | conversations | mark_read | delete_message | delete_conversation
// IDs: students = their student number, professors = "PROF-" + instructor_id
// ==========================================

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST, GET");
header("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With");

date_default_timezone_set('Asia/Manila');

// Some free hosts do not have the mbstring extension; fall back to plain substr.
if (!function_exists('mb_substr')) {
    function mb_substr($s, $a, $l = null) {
        return $l === null ? substr($s, $a) : substr($s, $a, $l);
    }
}

function chat_respond($success, $message, $data = null) {
    $out = ["success" => $success, "message" => $message];
    if ($data !== null) $out["data"] = $data;
    echo json_encode($out);
    exit();
}

// Same connection as schedules.php (db.php -> $pdo); falls back to config/database.php.
try {
    if (is_file(__DIR__ . '/db.php')) require_once __DIR__ . '/db.php';
    if (!isset($pdo) || !($pdo instanceof PDO)) {
        require_once __DIR__ . '/config/database.php';
        $pdo = getDatabaseConnection();
    }
} catch (Throwable $e) {
    error_log("chat.php connect: " . $e->getMessage());
    chat_respond(false, "Database connection unavailable.");
}
if (!($pdo instanceof PDO)) chat_respond(false, "Database connection unavailable.");
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);

if ($_SERVER['REQUEST_METHOD'] === 'GET' && empty($_GET['action'])) {
    chat_respond(true, "Chat API is active and online.");
}

$input = file_get_contents("php://input");
$data = json_decode($input, true);
if (!$data || !is_array($data)) $data = array_merge($_GET, $_POST);

function chat_field($data, $key, $default = '') {
    return isset($data[$key]) ? trim((string) $data[$key]) : $default;
}

require_once __DIR__ . '/auth.php';

// ---------- table + upgrade ----------
try {
    $pdo->exec("CREATE TABLE IF NOT EXISTS chat_messages (
        message_id INT AUTO_INCREMENT PRIMARY KEY,
        sender_id VARCHAR(50) NOT NULL,
        sender_name VARCHAR(100) NOT NULL,
        sender_role VARCHAR(20) NOT NULL,
        recipient_id VARCHAR(50) NOT NULL,
        recipient_name VARCHAR(100) NOT NULL,
        message_text TEXT NOT NULL,
        is_read TINYINT(1) NOT NULL DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        KEY idx_recipient (recipient_id, is_read),
        KEY idx_pair (sender_id, recipient_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS chat_message_hidden (
        user_id VARCHAR(50) NOT NULL,
        message_id INT NOT NULL,
        PRIMARY KEY (user_id, message_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    cs_auth_schema($pdo);

    // Add is_read to an older table (SHOW COLUMNS works on free hosts that block information_schema).
    $q = $pdo->query("SHOW COLUMNS FROM chat_messages LIKE 'is_read'");
    if (!$q->fetch()) {
        $pdo->exec("ALTER TABLE chat_messages ADD COLUMN is_read TINYINT(1) NOT NULL DEFAULT 0");
        // Old messages count as already seen so nobody gets a flood of "unread" on upgrade.
        $pdo->exec("UPDATE chat_messages SET is_read = 1");
    }
} catch (Throwable $e) {
    error_log("chat.php tables: " . $e->getMessage());
    chat_respond(false, "Could not prepare chat tables.");
}

$action = strtolower(chat_field($data, 'action', ''));
if ($action === '' && isset($data['message_text'])) $action = 'send';

try {
    // The session, never a supplied user ID, determines whose view is read or removed.
    $actor = cs_actor($pdo, $data);
    if ($actor['role'] === 'FACULTY') {
        $identity = 'PROF-' . $actor['user_id'];
    } else {
        $q = $pdo->prepare('SELECT student_id FROM users WHERE user_id = ?');
        $q->execute([$actor['user_id']]);
        $identity = (string)$q->fetchColumn();
    }
    $identityField = $action === 'send' ? 'sender_id' : ($action === 'fetch' ? 'user1' : 'user_id');
    if (isset($data[$identityField]) && strcasecmp((string)$data[$identityField], $identity) !== 0) {
        throw new InvalidArgumentException('You can access only your own messages.');
    }
    $data[$identityField] = $identity;
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') throw new InvalidArgumentException('Use POST for chat actions.');
    switch ($action) {

        case 'send': {
            $sid   = chat_field($data, 'sender_id');
            $sname = chat_field($data, 'sender_name');
            $srole = $actor['role'];
            $rid   = chat_field($data, 'recipient_id');
            $rname = chat_field($data, 'recipient_name');
            $text  = chat_field($data, 'message_text');

            if ($sid === '' || $rid === '' || $text === '') {
                chat_respond(false, "Sender, recipient, and message text are required.");
            }
            if (strcasecmp($sid, $rid) === 0) chat_respond(false, "You cannot message yourself.");

            $stmt = $pdo->prepare("INSERT INTO chat_messages
                (sender_id, sender_name, sender_role, recipient_id, recipient_name, message_text, is_read, created_at)
                VALUES (:sid, :sname, :srole, :rid, :rname, :txt, 0, :t)");
            $stmt->execute([
                ':sid' => $sid, ':sname' => mb_substr($sname, 0, 100), ':srole' => mb_substr($srole, 0, 20),
                ':rid' => $rid, ':rname' => mb_substr($rname, 0, 100),
                ':txt' => mb_substr($text, 0, 2000), ':t' => date('Y-m-d H:i:s')
            ]);
            chat_respond(true, "Message sent successfully.", ["message_id" => (int) $pdo->lastInsertId()]);
        }

        // One conversation, oldest first. user1 = me, user2 = the other person.
        case 'fetch': {
            $me    = chat_field($data, 'user1');
            $other = chat_field($data, 'user2');
            if ($me === '' || $other === '') chat_respond(false, "User parameters missing.");

            $stmt = $pdo->prepare("SELECT message_id, sender_id, sender_name, sender_role, recipient_id, recipient_name,
                    message_text, is_read, DATE_FORMAT(created_at, '%h:%i %p') AS timestamp
                FROM chat_messages
                WHERE ((sender_id = :a1 AND recipient_id = :b1) OR (sender_id = :b2 AND recipient_id = :a2))
                  AND NOT EXISTS (SELECT 1 FROM chat_message_hidden h WHERE h.user_id = :viewer AND h.message_id = chat_messages.message_id)
                ORDER BY message_id ASC");
            $stmt->execute([':a1' => $me, ':b1' => $other, ':b2' => $other, ':a2' => $me, ':viewer' => $me]);
            chat_respond(true, "OK", $stmt->fetchAll());
        }

        // Inbox: one row per person I talk with, with last message and my unread count.
        case 'conversations': {
            $me = chat_field($data, 'user_id');
            if ($me === '') chat_respond(false, "Missing user.");

            $stmt = $pdo->prepare("SELECT t.contact_id, t.last_id, t.unread,
                    m.message_text AS last_message, m.sender_id AS last_sender_id,
                    DATE_FORMAT(m.created_at, '%b %e, %h:%i %p') AS last_time,
                    CASE WHEN m.sender_id = :me3 THEN m.recipient_name ELSE m.sender_name END AS contact_name
                FROM (
                    SELECT CASE WHEN sender_id = :me1 THEN recipient_id ELSE sender_id END AS contact_id,
                           MAX(message_id) AS last_id,
                           SUM(CASE WHEN recipient_id = :me2 AND is_read = 0 THEN 1 ELSE 0 END) AS unread
                    FROM chat_messages
                    WHERE (sender_id = :me4 OR recipient_id = :me5)
                      AND NOT EXISTS (SELECT 1 FROM chat_message_hidden h WHERE h.user_id = :viewer AND h.message_id = chat_messages.message_id)
                    GROUP BY contact_id
                ) t
                JOIN chat_messages m ON m.message_id = t.last_id
                ORDER BY t.last_id DESC");
            $stmt->execute([':me1' => $me, ':me2' => $me, ':me3' => $me, ':me4' => $me, ':me5' => $me, ':viewer' => $me]);
            $rows = $stmt->fetchAll();
            foreach ($rows as $i => $r) {
                $rows[$i]['unread'] = (int) $r['unread'];
                $rows[$i]['last_id'] = (int) $r['last_id'];
            }
            chat_respond(true, "OK", $rows);
        }

        // I opened the chat: everything the other person sent me is now seen.
        case 'mark_read': {
            $me    = chat_field($data, 'user_id');
            $other = chat_field($data, 'contact_id');
            if ($me === '' || $other === '') chat_respond(false, "Missing parameters.");
            $stmt = $pdo->prepare("UPDATE chat_messages SET is_read = 1
                WHERE recipient_id = :me AND sender_id = :other AND is_read = 0
                  AND NOT EXISTS (SELECT 1 FROM chat_message_hidden h WHERE h.user_id = :viewer AND h.message_id = chat_messages.message_id)");
            $stmt->execute([':me' => $me, ':other' => $other, ':viewer' => $me]);
            chat_respond(true, "OK");
        }

        case 'delete_message': {
            $id = filter_var($data['message_id'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
            if (!$id) throw new InvalidArgumentException('Invalid message.');
            $q = $pdo->prepare('SELECT message_id FROM chat_messages WHERE message_id = ? AND (sender_id = ? OR recipient_id = ?)');
            $q->execute([$id, $identity, $identity]);
            if (!$q->fetchColumn()) throw new InvalidArgumentException('Message unavailable.');
            $q = $pdo->prepare('INSERT IGNORE INTO chat_message_hidden (user_id, message_id) VALUES (?, ?)');
            $q->execute([$identity, $id]);
            chat_respond(true, 'Message removed for you.');
        }
        case 'delete_conversation': {
            $other = chat_field($data, 'contact_id');
            $through = filter_var($data['through_message_id'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
            if ($other === '' || !$through) throw new InvalidArgumentException('Conversation and last displayed message are required.');
            // Only hide the history the user confirmed; messages arriving afterwards survive.
            $q = $pdo->prepare('INSERT IGNORE INTO chat_message_hidden (user_id, message_id)
                SELECT ?, message_id FROM chat_messages
                WHERE ((sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?))
                  AND message_id <= ?');
            $q->execute([$identity, $identity, $other, $other, $identity, $through]);
            chat_respond(true, 'Conversation removed for you.');
        }

        default:
            chat_respond(false, "Unknown action.");
    }
} catch (InvalidArgumentException $e) {
    chat_respond(false, $e->getMessage());
} catch (Throwable $e) {
    error_log("chat.php: " . $e->getMessage());
    chat_respond(false, "Server database error. Please try again.");
}
?>
