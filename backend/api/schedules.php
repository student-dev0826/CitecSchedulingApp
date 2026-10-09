<?php
// ==========================================
// CITEC Scheduling System - Schedules API
// One endpoint, many actions (POST field "action").
// Upload this file next to login.php / register.php / db.php.
// Tables/columns are created or upgraded automatically on first request.
// ==========================================

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST, GET");
header("Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With");

// All slot times are Philippine time, matching the app.
date_default_timezone_set('Asia/Manila');

// Uses db.php (provides $pdo) when present; otherwise the project's config/database.php.
if (file_exists(__DIR__ . '/db.php')) {
    require_once __DIR__ . '/db.php';
} else {
    require_once __DIR__ . '/config/database.php';
    $pdo = getDatabaseConnection();
}

function sch_respond($success, $message, $data = null) {
    $out = ["success" => $success, "message" => $message];
    if ($data !== null) $out["data"] = $data;
    echo json_encode($out);
    exit();
}

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    sch_respond(true, "Schedules API is active and online.");
}

$input = file_get_contents("php://input");
$data = json_decode($input, true);
if (!$data || !is_array($data)) $data = $_POST;

function sch_field($data, $key, $default = '') {
    return isset($data[$key]) ? trim($data[$key]) : $default;
}

$action = sch_field($data, 'action');
if (!isset($pdo) || !($pdo instanceof PDO)) {
    sch_respond(false, "Database connection unavailable.");
}
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
$now = date('Y-m-d H:i:s');

// ---------- helpers (needed while preparing tables) ----------
function sch_hasColumn($pdo, $table, $col) {
    $q = $pdo->prepare("SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t AND COLUMN_NAME = :c LIMIT 1");
    $q->execute([':t' => $table, ':c' => $col]);
    return (bool) $q->fetchColumn();
}
function sch_nameExpr($pdo, $table, $alias) {
    if (sch_hasColumn($pdo, $table, 'full_name')) return "$alias.full_name";
    return "TRIM(CONCAT_WS(' ', $alias.first_name, $alias.last_name))";
}

// ---------- tables ----------
try {
    $pdo->exec("CREATE TABLE IF NOT EXISTS cs_schedules (
        schedule_id INT AUTO_INCREMENT PRIMARY KEY,
        faculty_id INT NOT NULL,
        schedule_date DATE NOT NULL,
        time_slot VARCHAR(40) NOT NULL,
        start_at DATETIME NOT NULL,
        end_at DATETIME NOT NULL,
        category VARCHAR(100) NOT NULL,
        location VARCHAR(150) NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
        student_id INT DEFAULT NULL,
        purpose VARCHAR(255) DEFAULT NULL,
        transfer_reason VARCHAR(255) DEFAULT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uq_faculty_slot (faculty_id, schedule_date, time_slot),
        KEY idx_student (student_id),
        KEY idx_start (start_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

    $pdo->exec("CREATE TABLE IF NOT EXISTS cs_notifications (
        notification_id INT AUTO_INCREMENT PRIMARY KEY,
        user_id INT NOT NULL,
        user_role VARCHAR(10) NOT NULL,
        message VARCHAR(255) NOT NULL,
        is_read TINYINT(1) NOT NULL DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        KEY idx_user (user_id, user_role)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
} catch (Throwable $e) {
    error_log("schedules.php: " . $e->getMessage());
    sch_respond(false, "Could not prepare database tables.");
}

// Upgrade older installs: decline_reason, is_custom, cancel_reason (each optional, never fatal).
$hasDecline = false;
$hasCustom  = false;
$hasCancel  = false;
try {
    if (!sch_hasColumn($pdo, 'cs_schedules', 'decline_reason')) {
        try { $pdo->exec("ALTER TABLE cs_schedules ADD COLUMN decline_reason VARCHAR(255) DEFAULT NULL"); } catch (Throwable $ex) {}
    }
    if (!sch_hasColumn($pdo, 'cs_schedules', 'is_custom')) {
        try { $pdo->exec("ALTER TABLE cs_schedules ADD COLUMN is_custom TINYINT(1) NOT NULL DEFAULT 0"); } catch (Throwable $ex) {}
    }
    if (!sch_hasColumn($pdo, 'cs_schedules', 'cancel_reason')) {
        try { $pdo->exec("ALTER TABLE cs_schedules ADD COLUMN cancel_reason VARCHAR(255) DEFAULT NULL"); } catch (Throwable $ex) {}
    }
    // Older installs created status as VARCHAR(10); 'CANCELLED' needs more room.
    // Only runs when the column is still too short, so normal requests never lock the table.
    try {
        $len = $pdo->prepare("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cs_schedules' AND COLUMN_NAME = 'status' LIMIT 1");
        $len->execute();
        $cur = (int) $len->fetchColumn();
        if ($cur > 0 && $cur < 20) {
            $pdo->exec("ALTER TABLE cs_schedules MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN'");
        }
    } catch (Throwable $ex) {}
    $hasDecline = sch_hasColumn($pdo, 'cs_schedules', 'decline_reason');
    $hasCustom  = sch_hasColumn($pdo, 'cs_schedules', 'is_custom');
    $hasCancel  = sch_hasColumn($pdo, 'cs_schedules', 'cancel_reason');
} catch (Throwable $e) {
    error_log("schedules.php column check: " . $e->getMessage());
}

define('F_NAME', sch_nameExpr($pdo, 'instructors', 'f'));
define('U_NAME', sch_nameExpr($pdo, 'users', 'u'));

define('SLOT_SELECT', "SELECT s.schedule_id, s.faculty_id, " . F_NAME . " AS faculty_name, f.department AS department,
        s.schedule_date, s.time_slot, s.start_at, s.end_at, s.category, s.location, s.status,
        s.student_id, " . U_NAME . " AS student_name, u.student_id AS student_number,
        s.purpose, s.transfer_reason, "
        . ($hasDecline ? "s.decline_reason" : "NULL") . " AS decline_reason, "
        . ($hasCancel ? "s.cancel_reason" : "NULL") . " AS cancel_reason, "
        . ($hasCustom ? "s.is_custom" : "0") . " AS is_custom
    FROM cs_schedules s
    JOIN instructors f ON f.instructor_id = s.faculty_id
    LEFT JOIN users u ON u.user_id = s.student_id");

function sch_parseSlot($date, $slot) {
    $parts = explode('-', $slot);
    if (count($parts) !== 2) return null;
    $start = DateTime::createFromFormat('Y-m-d h:i A', $date . ' ' . trim($parts[0]));
    $end   = DateTime::createFromFormat('Y-m-d h:i A', $date . ' ' . trim($parts[1]));
    if (!$start || !$end || $end <= $start) return null;
    return [$start->format('Y-m-d H:i:s'), $end->format('Y-m-d H:i:s')];
}

function sch_notify($pdo, $userId, $role, $message) {
    $stmt = $pdo->prepare("INSERT INTO cs_notifications (user_id, user_role, message, created_at) VALUES (:u, :r, :m, :t)");
    $stmt->execute([':u' => $userId, ':r' => $role, ':m' => mb_substr($message, 0, 250), ':t' => date('Y-m-d H:i:s')]);
}

function sch_prettySlot($row) {
    return date('M j, Y', strtotime($row['schedule_date'])) . ' (' . $row['time_slot'] . ')';
}

function sch_fetchSlot($pdo, $id) {
    $stmt = $pdo->prepare(SLOT_SELECT . " WHERE s.schedule_id = :id LIMIT 1");
    $stmt->execute([':id' => $id]);
    return $stmt->fetch();
}

// Gives a student's slot back: custom requests are deleted, posted slots reopen.
function sch_release($pdo, $slotRow) {
    if ((int) $slotRow['is_custom'] === 1) {
        $q = $pdo->prepare("DELETE FROM cs_schedules WHERE schedule_id = :id");
        $q->execute([':id' => $slotRow['schedule_id']]);
        return;
    }
    $q = $pdo->prepare("UPDATE cs_schedules SET status = 'OPEN', student_id = NULL, purpose = NULL,
        transfer_reason = NULL"
        . ($GLOBALS['hasDecline'] ? ", decline_reason = NULL" : "")
        . ($GLOBALS['hasCancel'] ? ", cancel_reason = NULL" : "") . "
        WHERE schedule_id = :id");
    $q->execute([':id' => $slotRow['schedule_id']]);
}

// Pending requests whose start time has passed can no longer be approved: release them.
try {
    $stale = $pdo->prepare(SLOT_SELECT . " WHERE s.status = 'PENDING' AND s.start_at <= :now");
    $stale->execute([':now' => $now]);
    foreach ($stale->fetchAll() as $old) {
        sch_release($pdo, $old);
        if ($old['student_id']) {
            sch_notify($pdo, $old['student_id'], 'STUDENT',
                "Your " . $old['category'] . " request on " . sch_prettySlot($old) . " expired because it was not approved in time.");
        }
    }
} catch (Throwable $e) {
    error_log("schedules.php housekeeping: " . $e->getMessage());
}

// ---------- actions ----------
try {
    switch ($action) {

        // Open, future slots students can book.
        case 'list_open': {
            $stmt = $pdo->prepare(SLOT_SELECT . " WHERE s.status = 'OPEN' AND s.start_at > :now
                ORDER BY " . F_NAME . ", s.start_at");
            $stmt->execute([':now' => $now]);
            sch_respond(true, "OK", $stmt->fetchAll());
        }

        // All professors (used for transfer targets).
        case 'list_faculty': {
            $stmt = $pdo->query("SELECT f.instructor_id AS faculty_id, " . F_NAME . " AS full_name, f.department AS department
                FROM instructors f ORDER BY " . F_NAME);
            sch_respond(true, "OK", $stmt->fetchAll());
        }

        // Everything a professor posted (open + pending + booked), last 30 days onward.
        case 'faculty_slots': {
            $fid = (int) sch_field($data, 'faculty_id', 0);
            if ($fid <= 0) sch_respond(false, "Missing faculty account.");
            $stmt = $pdo->prepare(SLOT_SELECT . " WHERE s.faculty_id = :fid
                AND s.end_at >= DATE_SUB(:now, INTERVAL 30 DAY) ORDER BY s.start_at");
            $stmt->execute([':fid' => $fid, ':now' => $now]);
            sch_respond(true, "OK", $stmt->fetchAll());
        }

        // A student's appointments (pending, approved, declined and cancelled by the professor).
        case 'student_appointments': {
            $sid = (int) sch_field($data, 'student_id', 0);
            if ($sid <= 0) sch_respond(false, "Missing student account.");
            $stmt = $pdo->prepare(SLOT_SELECT . " WHERE s.student_id = :sid
                AND s.status IN ('PENDING', 'BOOKED', 'ACCEPTED', 'DECLINED', 'CANCELLED') ORDER BY s.start_at");
            $stmt->execute([':sid' => $sid]);
            sch_respond(true, "OK", $stmt->fetchAll());
        }

        // Professor posts a slot.
        case 'post': {
            $fid      = (int) sch_field($data, 'faculty_id', 0);
            $date     = sch_field($data, 'schedule_date');
            $slot     = sch_field($data, 'time_slot');
            $category = sch_field($data, 'category');
            $location = sch_field($data, 'location');

            if ($fid <= 0 || $date === '' || $slot === '' || $category === '' || $location === '') {
                sch_respond(false, "Please complete all fields.");
            }
            $chk = $pdo->prepare("SELECT 1 FROM instructors WHERE instructor_id = :id");
            $chk->execute([':id' => $fid]);
            if (!$chk->fetch()) sch_respond(false, "Faculty account not found.");

            $times = sch_parseSlot($date, $slot);
            if (!$times) sch_respond(false, "Invalid date or time slot.");
            if ($times[0] <= $now) sch_respond(false, "That time has already passed. Pick a later slot.");

            try {
                $ins = $pdo->prepare("INSERT INTO cs_schedules
                    (faculty_id, schedule_date, time_slot, start_at, end_at, category, location)
                    VALUES (:f, :d, :t, :s, :e, :c, :l)");
                $ins->execute([':f' => $fid, ':d' => $date, ':t' => $slot, ':s' => $times[0],
                               ':e' => $times[1], ':c' => $category, ':l' => $location]);
            } catch (PDOException $e) {
                if ($e->getCode() == 23000) sch_respond(false, "You already posted a slot for that date and time.");
                throw $e;
            }
            sch_respond(true, "Schedule posted.", sch_fetchSlot($pdo, $pdo->lastInsertId()));
        }

        // Professor removes an open slot.
        case 'delete': {
            $id  = (int) sch_field($data, 'schedule_id', 0);
            $fid = (int) sch_field($data, 'faculty_id', 0);
            $del = $pdo->prepare("DELETE FROM cs_schedules WHERE schedule_id = :id AND faculty_id = :f AND status = 'OPEN'");
            $del->execute([':id' => $id, ':f' => $fid]);
            if ($del->rowCount() === 0) sch_respond(false, "Only open slots can be removed. Approve, decline or transfer requests first.");
            sch_respond(true, "Slot removed.");
        }

        // Student requests a slot (atomic: only succeeds if still OPEN). The professor then accepts or declines.
        case 'book': {
            $id      = (int) sch_field($data, 'schedule_id', 0);
            $sid     = (int) sch_field($data, 'student_id', 0);
            $purpose = sch_field($data, 'purpose');

            $chk = $pdo->prepare("SELECT " . U_NAME . " AS full_name FROM users u WHERE u.user_id = :id");
            $chk->execute([':id' => $sid]);
            $student = $chk->fetch();
            if (!$student) sch_respond(false, "Student account not found.");

            $slot = sch_fetchSlot($pdo, $id);
            if (!$slot) sch_respond(false, "That schedule no longer exists.");

            // No overlapping bookings for the same student.
            $ov = $pdo->prepare("SELECT 1 FROM cs_schedules WHERE student_id = :sid
                AND status IN ('PENDING', 'BOOKED', 'ACCEPTED')
                AND start_at < :e AND end_at > :s LIMIT 1");
            $ov->execute([':sid' => $sid, ':s' => $slot['start_at'], ':e' => $slot['end_at']]);
            if ($ov->fetch()) sch_respond(false, "You already have an appointment at that time.");

            $upd = $pdo->prepare("UPDATE cs_schedules SET status = 'PENDING', student_id = :sid, purpose = :p
                WHERE schedule_id = :id AND status = 'OPEN' AND start_at > :now");
            $upd->execute([':sid' => $sid, ':p' => mb_substr($purpose, 0, 250), ':id' => $id, ':now' => $now]);
            if ($upd->rowCount() === 0) {
                sch_respond(false, "Sorry, that slot was just booked by someone else or has already passed.");
            }

            sch_notify($pdo, $slot['faculty_id'], 'FACULTY',
                $student['full_name'] . " requested " . $slot['category'] . " on " . sch_prettySlot($slot) . ". Open Schedule to accept or decline.");
            sch_respond(true, "Request sent to " . $slot['faculty_name'] . ". You'll be notified once it's approved.", sch_fetchSlot($pdo, $id));
        }

        // Student asks for a time the professor never posted.
        case 'request_custom_appointment': {
            $sid      = (int) sch_field($data, 'student_id', 0);
            $fid      = (int) sch_field($data, 'faculty_id', 0);
            $date     = sch_field($data, 'schedule_date');
            $slotTxt  = sch_field($data, 'time_slot');
            $category = sch_field($data, 'category');
            $location = sch_field($data, 'location');
            $purpose  = sch_field($data, 'purpose');

            if ($sid <= 0 || $fid <= 0 || $date === '' || $slotTxt === '' || $category === '') {
                sch_respond(false, "Please select professor, date, time slot, and category.");
            }
            $times = sch_parseSlot($date, $slotTxt);
            if (!$times) sch_respond(false, "Invalid date or time slot.");
            if ($times[0] <= $now) sch_respond(false, "Please choose a time in the future.");

            $chk = $pdo->prepare("SELECT 1 FROM instructors WHERE instructor_id = :id");
            $chk->execute([':id' => $fid]);
            if (!$chk->fetch()) sch_respond(false, "Professor not found.");

            $chk = $pdo->prepare("SELECT " . U_NAME . " AS full_name FROM users u WHERE u.user_id = :id");
            $chk->execute([':id' => $sid]);
            $student = $chk->fetch();
            if (!$student) sch_respond(false, "Student account not found.");

            // Professor already confirmed for someone at that time, or you already asked for it.
            $clash = $pdo->prepare("SELECT status FROM cs_schedules
                WHERE faculty_id = :f AND start_at < :e AND end_at > :s
                  AND (status IN ('BOOKED','ACCEPTED') OR (status = 'PENDING' AND student_id = :sid)) LIMIT 1");
            $clash->execute([':f' => $fid, ':s' => $times[0], ':e' => $times[1], ':sid' => $sid]);
            $hit = $clash->fetch();
            if ($hit) {
                sch_respond(false, ($hit['status'] === 'PENDING')
                    ? "You already have a pending request with this professor at that time."
                    : "That time is already taken. Please pick another time.");
            }

            // Student cannot be double-booked either.
            $ov = $pdo->prepare("SELECT 1 FROM cs_schedules WHERE student_id = :sid
                AND status IN ('PENDING', 'BOOKED', 'ACCEPTED') AND start_at < :e AND end_at > :s LIMIT 1");
            $ov->execute([':sid' => $sid, ':s' => $times[0], ':e' => $times[1]]);
            if ($ov->fetch()) sch_respond(false, "You already have an appointment at that time.");

            try {
                $ins = $pdo->prepare("INSERT INTO cs_schedules
                    (faculty_id, schedule_date, time_slot, start_at, end_at, category, location, status, student_id, purpose"
                    . ($hasCustom ? ", is_custom" : "") . ")
                    VALUES (:f, :d, :t, :s, :e, :c, :l, 'PENDING', :sid, :p" . ($hasCustom ? ", 1" : "") . ")");
                $ins->execute([
                    ':f' => $fid, ':d' => $date, ':t' => $slotTxt, ':s' => $times[0], ':e' => $times[1],
                    ':c' => $category, ':l' => ($location !== '' ? $location : 'Faculty Office'),
                    ':sid' => $sid, ':p' => mb_substr($purpose, 0, 250)
                ]);
            } catch (PDOException $e) {
                if ($e->getCode() == 23000) sch_respond(false, "That professor already has a slot or request at that exact time.");
                throw $e;
            }

            $row = sch_fetchSlot($pdo, $pdo->lastInsertId());
            sch_notify($pdo, $fid, 'FACULTY',
                "New appointment request from " . $student['full_name'] . " for " . sch_prettySlot($row) . ". Open Schedule to accept or decline.");
            sch_respond(true, "Custom appointment request sent to " . $row['faculty_name'] . "!", $row);
        }

        // Student cancels / withdraws / dismisses.
        case 'cancel': {
            $id  = (int) sch_field($data, 'schedule_id', 0);
            $sid = (int) sch_field($data, 'student_id', 0);

            $slot = sch_fetchSlot($pdo, $id);
            if (!$slot || (int)$slot['student_id'] !== $sid
                || !in_array($slot['status'], ['PENDING', 'BOOKED', 'ACCEPTED', 'DECLINED', 'CANCELLED'], true)) {
                sch_respond(false, "Appointment not found.");
            }
            // Declined / professor-cancelled entries are just dismissed; anything else must be in the future.
            $dismissOnly = in_array($slot['status'], ['DECLINED', 'CANCELLED'], true);
            if (!$dismissOnly && $slot['start_at'] <= $now) {
                sch_respond(false, "Past appointments cannot be cancelled.");
            }

            if ($slot['status'] === 'CANCELLED') {
                // Student dismisses a professor-cancelled appointment from their list.
                // The row is deleted, so the time is not handed back to other students as OPEN.
                $q = $pdo->prepare("DELETE FROM cs_schedules WHERE schedule_id = :id AND student_id = :sid AND status = 'CANCELLED'");
                $q->execute([':id' => $id, ':sid' => $sid]);
                sch_respond(true, "Removed.");
            }

            sch_release($pdo, $slot);

            if (!$dismissOnly) {
                $wasPending = ($slot['status'] === 'PENDING');
                sch_notify($pdo, $slot['faculty_id'], 'FACULTY',
                    $slot['student_name'] . ($wasPending ? " withdrew the request for " : " cancelled the ") . $slot['category']
                    . ($wasPending ? "" : " appointment") . " on " . sch_prettySlot($slot) . ".");
                sch_respond(true, $wasPending ? "Request cancelled." : "Appointment cancelled.");
            }
            sch_respond(true, "Removed.");
        }

        // Professor cancels a confirmed appointment (reason is required).
        // reason_type: SUDDEN_CONFLICT | PERSONAL_EMERGENCY | MEETING | OTHERS (OTHERS needs reason_text)
        case 'faculty_cancel': {
            $id         = (int) sch_field($data, 'schedule_id', 0);
            $fid        = (int) sch_field($data, 'faculty_id', 0);
            $reasonType = strtoupper(sch_field($data, 'reason_type'));
            $reasonText = preg_replace('/\s+/', ' ', sch_field($data, 'reason_text'));

            if ($id <= 0 || $fid <= 0) sch_respond(false, "Invalid parameters.");

            $labels = [
                'SUDDEN_CONFLICT'    => 'Sudden conflict',
                'PERSONAL_EMERGENCY' => 'Personal matter or emergency',
                'MEETING'            => 'Meeting',
                'OTHERS'             => 'Others',
            ];
            if (!isset($labels[$reasonType])) sch_respond(false, "Please choose a reason for cancelling.");

            if ($reasonType === 'OTHERS') {
                if (mb_strlen($reasonText) < 3) sch_respond(false, "Please type your reason for cancelling.");
                $reason = 'Others: ' . mb_substr($reasonText, 0, 120);
            } else {
                $reason = $labels[$reasonType];
            }

            if (!$hasCancel) sch_respond(false, "Server is not ready for cancellations yet. Please try again.");

            $slot = sch_fetchSlot($pdo, $id);
            if (!$slot || (int)$slot['faculty_id'] !== $fid) {
                sch_respond(false, "Appointment not found.");
            }
            if (!in_array($slot['status'], ['BOOKED', 'ACCEPTED'], true) || empty($slot['student_id'])) {
                sch_respond(false, "Only confirmed appointments can be cancelled. Refresh your list.");
            }
            if ($slot['end_at'] <= $now) {
                sch_respond(false, "This appointment time has already passed.");
            }

            // Keep the row (and the student) so the student can see it was cancelled and why.
            // The time stays blocked: it is not given back to students as an open slot.
            $upd = $pdo->prepare("UPDATE cs_schedules SET status = 'CANCELLED', cancel_reason = :r
                WHERE schedule_id = :id AND faculty_id = :f AND status IN ('BOOKED','ACCEPTED') AND end_at > :now");
            $upd->execute([':r' => $reason, ':id' => $id, ':f' => $fid, ':now' => $now]);
            if ($upd->rowCount() === 0) {
                sch_respond(false, "This appointment was just changed. Refresh your list.");
            }

            sch_notify($pdo, $slot['student_id'], 'STUDENT',
                $slot['faculty_name'] . " cancelled your " . $slot['category'] . " appointment on " .
                sch_prettySlot($slot) . ". Reason: " . $reason);
            sch_respond(true, "Appointment cancelled. " . $slot['student_name'] . " has been notified.");
        }

        // Professor answers a pending request (what the app's Accept / Decline buttons call).
        case 'respond_appointment': {
            $id     = (int) sch_field($data, 'schedule_id', 0);
            $fid    = (int) sch_field($data, 'faculty_id', 0);
            $status = strtoupper(sch_field($data, 'status'));
            $reason = sch_field($data, 'decline_reason');

            if ($id <= 0 || $fid <= 0) sch_respond(false, "Invalid parameters.");

            $slot = sch_fetchSlot($pdo, $id);
            if (!$slot || (int)$slot['faculty_id'] !== $fid) sch_respond(false, "Appointment request not found.");
            if ($slot['status'] !== 'PENDING') {
                sch_respond(false, "This request was already " . strtolower($slot['status']) . " or withdrawn by the student. Refresh your list.");
            }
            if ($slot['start_at'] <= $now) sch_respond(false, "That time has already passed.");

            $accept = ($status === 'ACCEPTED' || $status === 'CONFIRMED');

            if ($accept) {
                $clash = $pdo->prepare("SELECT 1 FROM cs_schedules
                    WHERE faculty_id = :f AND schedule_id <> :id AND status IN ('BOOKED','ACCEPTED')
                      AND start_at < :e AND end_at > :s LIMIT 1");
                $clash->execute([':f' => $fid, ':id' => $id, ':s' => $slot['start_at'], ':e' => $slot['end_at']]);
                if ($clash->fetch()) {
                    sch_respond(false, "You already have a confirmed appointment that overlaps this time. Decline this request instead.");
                }
            } elseif ($reason === '') {
                $reason = 'No reason provided.';
            }

            $newStatus = $accept ? 'ACCEPTED' : 'DECLINED';
            $sql = "UPDATE cs_schedules SET status = :s" . ($hasDecline ? ", decline_reason = :r" : "")
                 . " WHERE schedule_id = :id AND faculty_id = :f AND status = 'PENDING'";
            $params = [':s' => $newStatus, ':id' => $id, ':f' => $fid];
            if ($hasDecline) $params[':r'] = $accept ? '' : mb_substr($reason, 0, 250);
            $upd = $pdo->prepare($sql);
            $upd->execute($params);
            if ($upd->rowCount() === 0) sch_respond(false, "This request is no longer pending.");

            if ($accept) {
                sch_notify($pdo, $slot['student_id'], 'STUDENT',
                    "Your " . $slot['category'] . " appointment with " . $slot['faculty_name'] . " on "
                    . sch_prettySlot($slot) . " has been ACCEPTED!");
                sch_respond(true, "Request accepted.");
            }
            sch_notify($pdo, $slot['student_id'], 'STUDENT',
                "Your " . $slot['category'] . " request with " . $slot['faculty_name'] . " on "
                . sch_prettySlot($slot) . " was declined. Reason: " . $reason);
            sch_respond(true, "Request declined.");
        }

        // Older app versions: approve / decline.
        case 'approve': {
            $id  = (int) sch_field($data, 'schedule_id', 0);
            $fid = (int) sch_field($data, 'faculty_id', 0);
            $slot = sch_fetchSlot($pdo, $id);
            if (!$slot || (int)$slot['faculty_id'] !== $fid || $slot['status'] !== 'PENDING') {
                sch_respond(false, "Request not found. The student may have cancelled it.");
            }
            if ($slot['start_at'] <= $now) sch_respond(false, "That time has already passed.");
            $upd = $pdo->prepare("UPDATE cs_schedules SET status = 'BOOKED'
                WHERE schedule_id = :id AND faculty_id = :f AND status = 'PENDING'");
            $upd->execute([':id' => $id, ':f' => $fid]);
            if ($upd->rowCount() === 0) sch_respond(false, "This request is no longer pending.");
            sch_notify($pdo, $slot['student_id'], 'STUDENT',
                "Your " . $slot['category'] . " appointment with " . $slot['faculty_name'] . " on "
                . sch_prettySlot($slot) . " was approved.");
            sch_respond(true, "Request approved.");
        }

        case 'decline': {
            $id     = (int) sch_field($data, 'schedule_id', 0);
            $fid    = (int) sch_field($data, 'faculty_id', 0);
            $reason = sch_field($data, 'reason');
            $slot = sch_fetchSlot($pdo, $id);
            if (!$slot || (int)$slot['faculty_id'] !== $fid || $slot['status'] !== 'PENDING') {
                sch_respond(false, "Request not found. The student may have cancelled it.");
            }
            sch_release($pdo, $slot);
            sch_notify($pdo, $slot['student_id'], 'STUDENT',
                "Your " . $slot['category'] . " request with " . $slot['faculty_name'] . " on "
                . sch_prettySlot($slot) . " was declined" . ($reason !== '' ? ". Reason: $reason" : ".")
                . " You can book another slot.");
            sch_respond(true, "Request declined.");
        }

        // Professor hands an accepted appointment to another professor (same date/time).
        case 'transfer': {
            $id     = (int) sch_field($data, 'schedule_id', 0);
            $from   = (int) sch_field($data, 'from_faculty_id', 0);
            $to     = (int) sch_field($data, 'to_faculty_id', 0);
            $reason = sch_field($data, 'reason');

            if ($to <= 0 || $to === $from) sch_respond(false, "Choose a different professor.");

            $slot = sch_fetchSlot($pdo, $id);
            if (!$slot || (int)$slot['faculty_id'] !== $from || !in_array($slot['status'], ['BOOKED', 'ACCEPTED'], true)) {
                sch_respond(false, "Booked appointment not found.");
            }
            if ($slot['start_at'] <= $now) sch_respond(false, "Past appointments cannot be transferred.");

            $t = $pdo->prepare("SELECT " . F_NAME . " AS full_name FROM instructors f WHERE f.instructor_id = :id");
            $t->execute([':id' => $to]);
            $target = $t->fetch();
            if (!$target) sch_respond(false, "Target professor not found.");

            $loc = $pdo->prepare("SELECT location FROM cs_schedules WHERE faculty_id = :f ORDER BY schedule_id DESC LIMIT 1");
            $loc->execute([':f' => $to]);
            $locRow = $loc->fetch();
            $newLocation = $locRow ? $locRow['location'] : $slot['location'];

            try {
                $upd = $pdo->prepare("UPDATE cs_schedules SET faculty_id = :to, location = :loc, transfer_reason = :r
                    WHERE schedule_id = :id AND faculty_id = :from AND status IN ('BOOKED','ACCEPTED')");
                $upd->execute([':to' => $to, ':loc' => $newLocation, ':r' => mb_substr($reason, 0, 250),
                               ':id' => $id, ':from' => $from]);
            } catch (PDOException $e) {
                if ($e->getCode() == 23000) sch_respond(false, $target['full_name'] . " already has a slot at that date and time.");
                throw $e;
            }
            if ($upd->rowCount() === 0) sch_respond(false, "Transfer failed. Please try again.");

            $when = sch_prettySlot($slot);
            sch_notify($pdo, $slot['student_id'], 'STUDENT',
                "Your " . $slot['category'] . " appointment on $when was transferred from " . $slot['faculty_name'] .
                " to " . $target['full_name'] . ($reason !== '' ? ". Reason: $reason" : "."));
            sch_notify($pdo, $to, 'FACULTY',
                $slot['faculty_name'] . " transferred " . $slot['student_name'] . "'s " . $slot['category'] . " appointment on $when to you.");
            sch_respond(true, "Appointment transferred to " . $target['full_name'] . ".");
        }

        case 'list_notifications': {
            $uid  = (int) sch_field($data, 'user_id', 0);
            $role = strtoupper(sch_field($data, 'user_role'));
            $stmt = $pdo->prepare("SELECT notification_id, message, is_read, created_at FROM cs_notifications
                WHERE user_id = :u AND user_role = :r ORDER BY notification_id DESC LIMIT 30");
            $stmt->execute([':u' => $uid, ':r' => $role]);
            sch_respond(true, "OK", $stmt->fetchAll());
        }

        case 'mark_read': {
            $uid  = (int) sch_field($data, 'user_id', 0);
            $role = strtoupper(sch_field($data, 'user_role'));
            $stmt = $pdo->prepare("UPDATE cs_notifications SET is_read = 1 WHERE user_id = :u AND user_role = :r");
            $stmt->execute([':u' => $uid, ':r' => $role]);
            sch_respond(true, "OK");
        }

        default:
            sch_respond(false, "Unknown action.");
    }
} catch (Throwable $e) {
    error_log("schedules.php: " . $e->getMessage());
    sch_respond(false, "Server database error. Please try again.");
}
?>
