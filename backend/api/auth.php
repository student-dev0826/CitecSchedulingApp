<?php
// Scheduling sessions are issued only after login.php verifies the password.
function cs_auth_schema(PDO $pdo) {
    $pdo->exec("CREATE TABLE IF NOT EXISTS cs_auth_sessions (
        token_hash CHAR(64) PRIMARY KEY, user_id INT NOT NULL, role VARCHAR(10) NOT NULL,
        expires_at DATETIME NOT NULL, KEY idx_expiry (expires_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}

function cs_issue_token(PDO $pdo, int $id, string $role): string {
    cs_auth_schema($pdo);
    $token = bin2hex(random_bytes(32));
    $stmt = $pdo->prepare('INSERT INTO cs_auth_sessions VALUES (?, ?, ?, ?)');
    $stmt->execute([hash('sha256', $token), $id, $role, gmdate('Y-m-d H:i:s', time() + 30 * 86400)]);
    $pdo->exec("DELETE FROM cs_auth_sessions WHERE expires_at < UTC_TIMESTAMP()");
    return $token;
}

function cs_actor(PDO $pdo, array $data): array {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    $token = preg_match('/^Bearer (.+)$/i', $header, $m) ? $m[1] : ($data['auth_token'] ?? '');
    if (!is_string($token) || !preg_match('/^[a-f0-9]{64}$/D', $token)) {
        throw new InvalidArgumentException('Please log in again online to manage schedules.');
    }
    $q = $pdo->prepare('SELECT user_id, role FROM cs_auth_sessions WHERE token_hash = ? AND expires_at > UTC_TIMESTAMP()');
    $q->execute([hash('sha256', $token)]);
    $actor = $q->fetch(PDO::FETCH_ASSOC);
    if (!$actor) throw new InvalidArgumentException('Your scheduling session expired. Please log in again.');
    $table = $actor['role'] === 'FACULTY' ? 'instructors' : 'users';
    $pk = $actor['role'] === 'FACULTY' ? 'instructor_id' : 'user_id';
    $q = $pdo->prepare("SELECT 1 FROM $table WHERE $pk = ?");
    $q->execute([$actor['user_id']]);
    if (!$q->fetchColumn()) throw new InvalidArgumentException('Account no longer exists.');
    return $actor;
}

function cs_authorize(PDO $pdo, array $data, string $action): array {
    $actor = cs_actor($pdo, $data);
    $faculty = ['faculty_slots', 'post', 'delete', 'faculty_cancel', 'respond_appointment', 'approve', 'decline', 'transfer'];
    $student = ['student_appointments', 'book', 'cancel', 'request_custom_appointment'];
    if (strpos($action, 'recurring_') === 0 || in_array($action, $faculty, true)) {
        if ($actor['role'] !== 'FACULTY') throw new InvalidArgumentException('Only professors can manage availability.');
        $field = $action === 'transfer' ? 'from_faculty_id' : 'faculty_id';
        if (isset($data[$field]) && (int)$data[$field] !== (int)$actor['user_id']) {
            throw new InvalidArgumentException('You can manage only your own availability.');
        }
    } elseif (in_array($action, $student, true)) {
        if ($actor['role'] !== 'STUDENT' || (int)($data['student_id'] ?? 0) !== (int)$actor['user_id']) {
            throw new InvalidArgumentException('You can manage only your own appointments.');
        }
    } elseif (in_array($action, ['list_notifications', 'mark_read'], true)) {
        if ((int)($data['user_id'] ?? 0) !== (int)$actor['user_id'] || strtoupper($data['user_role'] ?? '') !== $actor['role']) {
            throw new InvalidArgumentException('You can access only your own notifications.');
        }
    }
    return $actor;
}
