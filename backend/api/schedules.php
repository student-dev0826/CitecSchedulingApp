<?php
// ==========================================
// CITEC Scheduling System - Schedules API
// One endpoint, many actions (POST field "action").
// Upload this file next to login.php / register.php.
// Tables are created automatically on first request.
// ==========================================

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST, GET");
header("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With");

// All slot times are Philippine time, matching the app.
date_default_timezone_set('Asia/Manila');

require_once __DIR__ . '/config/database.php';

function respond($success, $message, $data = null) {
    $out = ["success" => $success, "message" => $message];
    if ($data !== null) $out["data"] = $data;
    echo json_encode($out);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    respond(true, "Schedules API is active and online.");
}

$input = file_get_contents("php://input");
$data = json_decode($input, true);
if (!$data || !is_array($data)) $data = $_POST;

function field($data, $key, $default = '') {
    return isset($data[$key]) ? trim($data[$key]) : $default;
}

$action = field($data, 'action');
$pdo = getDatabaseConnection();
$now = date('Y-m-d H:i:s');

// ---------- tables ----------
try {
    $pdo->exec("CREATE TABLE IF NOT EXISTS schedules (
        schedule_id INT AUTO_INCREMENT PRIMARY KEY,
        faculty_id INT NOT NULL,
        schedule_date DATE NOT NULL,
        time_slot VARCHAR(40) NOT NULL,
        start_at DATETIME NOT NULL,
        end_at DATETIME NOT NULL,
        category VARCHAR(100) NOT NULL,
        location VARCHAR(150) NOT NULL,
        status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
        student_id INT DEFAULT NULL,
        purpose VARCHAR(255) DEFAULT NULL,
        transfer_reason VARCHAR(255) DEFAULT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uq_faculty_slot (faculty_id, schedule_date, time_slot),
        KEY idx_student (student_id),
        KEY idx_start (start_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS notifications (
        notification_id INT AUTO_INCREMENT PRIMARY KEY,
        user_id INT NOT NULL,
        user_role VARCHAR(10) NOT NULL,
        message VARCHAR(255) NOT NULL,
        is_read TINYINT(1) NOT NULL DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        KEY idx_user (user_id, user_role)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
} catch (Exception $e) {
    respond(false, "Could not prepare database tables.");
}

// ---------- helpers ----------
const SLOT_SELECT = "SELECT s.schedule_id, s.faculty_id, f.full_name AS faculty_name, f.department AS department,
        s.schedule_date, s.time_slot, s.start_at, s.end_at, s.category, s.location, s.status,
        s.student_id, u.full_name AS student_name, u.student_id AS student_number,
        s.purpose, s.transfer_reason
    FROM schedules s
    JOIN instructors f ON f.instructor_id = s.faculty_id
    LEFT JOIN users u ON u.user_id = s.student_id";

function parseSlot($date, $slot) {
    $parts = explode('-', $slot);
    if (count($parts) !== 2) return null;
    $start = DateTime::createFromFormat('Y-m-d h:i A', $date . ' ' . trim($parts[0]));
    $end   = DateTime::createFromFormat('Y-m-d h:i A', $date . ' ' . trim($parts[1]));
    if (!$start || !$end || $end <= $start) return null;
    return [$start->format('Y-m-d H:i:s'), $end->format('Y-m-d H:i:s')];
}

function notify($pdo, $userId, $role, $message) {
    $stmt = $pdo->prepare("INSERT INTO notifications (user_id, user_role, message, created_at) VALUES (:u, :r, :m, :t)");
    $stmt->execute([':u' => $userId, ':r' => $role, ':m' => mb_substr($message, 0, 250), ':t' => date('Y-m-d H:i:s')]);
}

function prettySlot($row) {
    return date('M j, Y', strtotime($row['schedule_date'])) . ' (' . $row['time_slot'] . ')';
}

function fetchSlot($pdo, $id) {
    $stmt = $pdo->prepare(SLOT_SELECT . " WHERE s.schedule_id = :id LIMIT 1");
    $stmt->execute([':id' => $id]);
    return $stmt->fetch();
}

// ---------- actions ----------
try {
    switch ($action) {

        // Open, future slots students can book.
        case 'list_open': {
            $stmt = $pdo->prepare(SLOT_SELECT . " WHERE s.status = 'OPEN' AND s.start_at > :now
                ORDER BY f.full_name, s.start_at");
            $stmt->execute([':now' => $now]);
            respond(true, "OK", $stmt->fetchAll());
        }

        // All professors (used for transfer targets).
        case 'list_faculty': {
            $stmt = $pdo->query("SELECT instructor_id AS faculty_id, full_name, department
                FROM instructors ORDER BY full_name");
            respond(true, "OK", $stmt->fetchAll());
        }

        // Everything a professor posted (open + booked), last 30 days onward.
        case 'faculty_slots': {
            $fid = (int) field($data, 'faculty_id', 0);
            if ($fid <= 0) respond(false, "Missing faculty account.");
            $stmt = $pdo->prepare(SLOT_SELECT . " WHERE s.faculty_id = :fid
                AND s.end_at >= DATE_SUB(:now, INTERVAL 30 DAY) ORDER BY s.start_at");
            $stmt->execute([':fid' => $fid, ':now' => $now]);
            respond(true, "OK", $stmt->fetchAll());
        }

        // A student's booked appointments.
        case 'student_appointments': {
            $sid = (int) field($data, 'student_id', 0);
            if ($sid <= 0) respond(false, "Missing student account.");
            $stmt = $pdo->prepare(SLOT_SELECT . " WHERE s.student_id = :sid AND s.status = 'BOOKED'
                ORDER BY s.start_at");
            $stmt->execute([':sid' => $sid]);
            respond(true, "OK", $stmt->fetchAll());
        }

        // Professor posts a slot.
        case 'post': {
            $fid      = (int) field($data, 'faculty_id', 0);
            $date     = field($data, 'schedule_date');
            $slot     = field($data, 'time_slot');
            $category = field($data, 'category');
            $location = field($data, 'location');

            if ($fid <= 0 || $date === '' || $slot === '' || $category === '' || $location === '') {
                respond(false, "Please complete all fields.");
            }
            $chk = $pdo->prepare("SELECT 1 FROM instructors WHERE instructor_id = :id");
            $chk->execute([':id' => $fid]);
            if (!$chk->fetch()) respond(false, "Faculty account not found.");

            $times = parseSlot($date, $slot);
            if (!$times) respond(false, "Invalid date or time slot.");
            if ($times[0] <= $now) respond(false, "That time has already passed. Pick a later slot.");

            try {
                $ins = $pdo->prepare("INSERT INTO schedules
                    (faculty_id, schedule_date, time_slot, start_at, end_at, category, location)
                    VALUES (:f, :d, :t, :s, :e, :c, :l)");
                $ins->execute([':f' => $fid, ':d' => $date, ':t' => $slot, ':s' => $times[0],
                               ':e' => $times[1], ':c' => $category, ':l' => $location]);
            } catch (PDOException $e) {
                if ($e->getCode() == 23000) respond(false, "You already posted a slot for that date and time.");
                throw $e;
            }
            respond(true, "Schedule posted.", fetchSlot($pdo, $pdo->lastInsertId()));
        }

        // Professor removes an open slot.
        case 'delete': {
            $id  = (int) field($data, 'schedule_id', 0);
            $fid = (int) field($data, 'faculty_id', 0);
            $del = $pdo->prepare("DELETE FROM schedules WHERE schedule_id = :id AND faculty_id = :f AND status = 'OPEN'");
            $del->execute([':id' => $id, ':f' => $fid]);
            if ($del->rowCount() === 0) respond(false, "Only open slots can be removed. Transfer or cancel booked ones instead.");
            respond(true, "Slot removed.");
        }

        // Student books a slot (atomic: only succeeds if still OPEN).
        case 'book': {
            $id      = (int) field($data, 'schedule_id', 0);
            $sid     = (int) field($data, 'student_id', 0);
            $purpose = field($data, 'purpose');

            $chk = $pdo->prepare("SELECT full_name FROM users WHERE user_id = :id");
            $chk->execute([':id' => $sid]);
            $student = $chk->fetch();
            if (!$student) respond(false, "Student account not found.");

            $slot = fetchSlot($pdo, $id);
            if (!$slot) respond(false, "That schedule no longer exists.");

            // No overlapping bookings for the same student.
            $ov = $pdo->prepare("SELECT 1 FROM schedules WHERE student_id = :sid AND status = 'BOOKED'
                AND start_at < :e AND end_at > :s LIMIT 1");
            $ov->execute([':sid' => $sid, ':s' => $slot['start_at'], ':e' => $slot['end_at']]);
            if ($ov->fetch()) respond(false, "You already have an appointment at that time.");

            $upd = $pdo->prepare("UPDATE schedules SET status = 'BOOKED', student_id = :sid, purpose = :p
                WHERE schedule_id = :id AND status = 'OPEN' AND start_at > :now");
            $upd->execute([':sid' => $sid, ':p' => mb_substr($purpose, 0, 250), ':id' => $id, ':now' => $now]);
            if ($upd->rowCount() === 0) {
                respond(false, "Sorry, that slot was just booked by someone else or has already passed.");
            }

            notify($pdo, $slot['faculty_id'], 'FACULTY',
                $student['full_name'] . " booked " . $slot['category'] . " on " . prettySlot($slot) . ".");
            respond(true, "Appointment booked with " . $slot['faculty_name'] . ".", fetchSlot($pdo, $id));
        }

        // Student cancels; slot goes back to OPEN so others can book it.
        case 'cancel': {
            $id  = (int) field($data, 'schedule_id', 0);
            $sid = (int) field($data, 'student_id', 0);

            $slot = fetchSlot($pdo, $id);
            if (!$slot || (int)$slot['student_id'] !== $sid || $slot['status'] !== 'BOOKED') {
                respond(false, "Appointment not found.");
            }
            if ($slot['start_at'] <= $now) respond(false, "Past appointments cannot be cancelled.");

            $upd = $pdo->prepare("UPDATE schedules SET status = 'OPEN', student_id = NULL, purpose = NULL,
                transfer_reason = NULL WHERE schedule_id = :id AND student_id = :sid");
            $upd->execute([':id' => $id, ':sid' => $sid]);

            notify($pdo, $slot['faculty_id'], 'FACULTY',
                $slot['student_name'] . " cancelled the " . $slot['category'] . " appointment on " . prettySlot($slot) . ". The slot is open again.");
            respond(true, "Appointment cancelled.");
        }

        // Professor hands a booked appointment to another professor (same date/time).
        case 'transfer': {
            $id     = (int) field($data, 'schedule_id', 0);
            $from   = (int) field($data, 'from_faculty_id', 0);
            $to     = (int) field($data, 'to_faculty_id', 0);
            $reason = field($data, 'reason');

            if ($to <= 0 || $to === $from) respond(false, "Choose a different professor.");

            $slot = fetchSlot($pdo, $id);
            if (!$slot || (int)$slot['faculty_id'] !== $from || $slot['status'] !== 'BOOKED') {
                respond(false, "Booked appointment not found.");
            }
            if ($slot['start_at'] <= $now) respond(false, "Past appointments cannot be transferred.");

            $t = $pdo->prepare("SELECT full_name FROM instructors WHERE instructor_id = :id");
            $t->execute([':id' => $to]);
            $target = $t->fetch();
            if (!$target) respond(false, "Target professor not found.");

            // Use the new professor's usual office if we know it.
            $loc = $pdo->prepare("SELECT location FROM schedules WHERE faculty_id = :f ORDER BY schedule_id DESC LIMIT 1");
            $loc->execute([':f' => $to]);
            $locRow = $loc->fetch();
            $newLocation = $locRow ? $locRow['location'] : $slot['location'];

            try {
                $upd = $pdo->prepare("UPDATE schedules SET faculty_id = :to, location = :loc, transfer_reason = :r
                    WHERE schedule_id = :id AND faculty_id = :from AND status = 'BOOKED'");
                $upd->execute([':to' => $to, ':loc' => $newLocation, ':r' => mb_substr($reason, 0, 250),
                               ':id' => $id, ':from' => $from]);
            } catch (PDOException $e) {
                if ($e->getCode() == 23000) respond(false, $target['full_name'] . " already has a slot at that date and time.");
                throw $e;
            }
            if ($upd->rowCount() === 0) respond(false, "Transfer failed. Please try again.");

            $when = prettySlot($slot);
            notify($pdo, $slot['student_id'], 'STUDENT',
                "Your " . $slot['category'] . " appointment on $when was transferred from " . $slot['faculty_name'] .
                " to " . $target['full_name'] . ($reason !== '' ? ". Reason: $reason" : "."));
            notify($pdo, $to, 'FACULTY',
                $slot['faculty_name'] . " transferred " . $slot['student_name'] . "'s " . $slot['category'] . " appointment on $when to you.");
            respond(true, "Appointment transferred to " . $target['full_name'] . ".");
        }

        case 'list_notifications': {
            $uid  = (int) field($data, 'user_id', 0);
            $role = strtoupper(field($data, 'user_role'));
            $stmt = $pdo->prepare("SELECT notification_id, message, is_read, created_at FROM notifications
                WHERE user_id = :u AND user_role = :r ORDER BY notification_id DESC LIMIT 30");
            $stmt->execute([':u' => $uid, ':r' => $role]);
            respond(true, "OK", $stmt->fetchAll());
        }

        case 'mark_read': {
            $uid  = (int) field($data, 'user_id', 0);
            $role = strtoupper(field($data, 'user_role'));
            $stmt = $pdo->prepare("UPDATE notifications SET is_read = 1 WHERE user_id = :u AND user_role = :r");
            $stmt->execute([':u' => $uid, ':r' => $role]);
            respond(true, "OK");
        }

        default:
            respond(false, "Unknown action.");
    }
} catch (PDOException $e) {
    respond(false, "Server database error. Please try again.");
}
?>
