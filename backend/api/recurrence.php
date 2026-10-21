<?php
// ISO weekdays: Monday=1 ... Sunday=7. Times and date ranges use Asia/Manila.
function rec_date(string $value): DateTimeImmutable {
    $date = DateTimeImmutable::createFromFormat('!Y-m-d', $value, new DateTimeZone('Asia/Manila'));
    if (!$date || $date->format('Y-m-d') !== $value) throw new InvalidArgumentException('Use a valid date in YYYY-MM-DD format.');
    return $date;
}

function rec_time(string $value): int {
    if (!preg_match('/^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/D', $value)) throw new InvalidArgumentException('Use a valid time in HH:mm format.');
    return (int)substr($value, 0, 2) * 60 + (int)substr($value, 3, 2);
}

function rec_plan(array $data): array {
    $from = rec_date(trim($data['date_from'] ?? ''));
    $to = rec_date(trim($data['date_to'] ?? ''));
    if ($to < $from || $from->diff($to)->days > 365) throw new InvalidArgumentException('Choose an inclusive date range of at most one year.');
    $weekday = filter_var($data['weekday'] ?? null, FILTER_VALIDATE_INT);
    if (!$weekday || $weekday > 7) throw new InvalidArgumentException('Choose Monday through Sunday.');
    $start = trim($data['start_time'] ?? ''); $end = trim($data['end_time'] ?? '');
    if (rec_time($end) <= rec_time($start)) throw new InvalidArgumentException('End time must be after start time on the same day.');
    $category = trim($data['category'] ?? ''); $location = trim($data['location'] ?? '');
    if ($category === '' || mb_strlen($category) > 100 || $location === '' || mb_strlen($location) > 150) {
        throw new InvalidArgumentException('Enter a category (up to 100 characters) and location (up to 150 characters).');
    }
    $dates = [];
    for ($date = $from; $date <= $to; $date = $date->modify('+1 day')) {
        if ((int)$date->format('N') === $weekday) $dates[] = $date->format('Y-m-d');
    }
    if (!$dates) throw new InvalidArgumentException('The selected weekday does not occur in this range.');
    return ['weekday' => $weekday, 'start_time' => $start, 'end_time' => $end,
        'date_from' => $from->format('Y-m-d'), 'date_to' => $to->format('Y-m-d'),
        'category' => $category, 'location' => $location, 'dates' => $dates];
}

function rec_schema(PDO $pdo) {
    $pdo->exec("CREATE TABLE IF NOT EXISTS cs_recurring_availability (
        recurrence_id INT AUTO_INCREMENT PRIMARY KEY, faculty_id INT NOT NULL,
        weekday TINYINT NOT NULL, start_time CHAR(5) NOT NULL, end_time CHAR(5) NOT NULL,
        date_from DATE NOT NULL, date_to DATE NOT NULL, category VARCHAR(100) NOT NULL,
        location VARCHAR(150) NOT NULL, active TINYINT NOT NULL DEFAULT 1,
        version INT NOT NULL DEFAULT 1, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        KEY idx_owner (faculty_id, active)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    if (!sch_hasColumn($pdo, 'cs_schedules', 'recurrence_id')) {
        try { $pdo->exec('ALTER TABLE cs_schedules ADD COLUMN recurrence_id INT DEFAULT NULL, ADD KEY idx_recurrence (recurrence_id)'); }
        catch (PDOException $e) { if (!sch_hasColumn($pdo, 'cs_schedules', 'recurrence_id')) throw $e; }
    }
    // Cancelled/declined appointment history may share an interval with new availability.
    // Active overlap checks and the shared writer lock replace the old exact-text unique key.
    $q = $pdo->query("SHOW INDEX FROM cs_schedules WHERE Key_name = 'uq_faculty_slot'");
    if ($q->fetch()) {
        try { $pdo->exec('ALTER TABLE cs_schedules DROP INDEX uq_faculty_slot, ADD KEY idx_faculty_slot (faculty_id, schedule_date, time_slot)'); }
        catch (PDOException $e) {
            if ($pdo->query("SHOW INDEX FROM cs_schedules WHERE Key_name = 'uq_faculty_slot'")->fetch()) throw $e;
        }
    }
}

function rec_overlap(PDO $pdo, int $faculty, string $start, string $end, int $except = 0): bool {
    $q = $pdo->prepare("SELECT 1 FROM cs_schedules WHERE faculty_id = ? AND schedule_id <> ?
        AND status NOT IN ('CANCELLED','DECLINED') AND start_at < ? AND end_at > ? LIMIT 1");
    $q->execute([$faculty, $except, $end, $start]);
    return (bool)$q->fetchColumn();
}

function rec_handle(PDO $pdo, array $data, array $actor, string $action): array {
    $faculty = (int)$actor['user_id'];
    if ($action === 'recurring_list') {
        $q = $pdo->prepare('SELECT * FROM cs_recurring_availability WHERE faculty_id = ? AND active = 1 ORDER BY weekday, start_time');
        $q->execute([$faculty]); return ['message' => 'OK', 'data' => $q->fetchAll()];
    }
    if ($action === 'recurring_calendar') {
        $month = trim($data['month'] ?? '');
        if (!preg_match('/^\d{4}-\d{2}$/D', $month)) throw new InvalidArgumentException('Choose a valid month.');
        $from = rec_date($month . '-01'); $until = $from->modify('+1 month')->format('Y-m-d');
        $q = $pdo->prepare(SLOT_SELECT . ' WHERE s.faculty_id = ? AND s.schedule_date >= ? AND s.schedule_date < ? ORDER BY s.start_at');
        $q->execute([$faculty, $from->format('Y-m-d'), $until]);
        return ['message' => 'OK', 'data' => $q->fetchAll()];
    }
    if (!in_array($action, ['recurring_save', 'recurring_delete'], true)) throw new InvalidArgumentException('Unknown recurring action.');
    $id = (int)($data['recurrence_id'] ?? 0);
    $plan = $action === 'recurring_save' ? rec_plan($data) : null;
    $pdo->beginTransaction();
    try {
        if ($id > 0) {
            $q = $pdo->prepare('SELECT * FROM cs_recurring_availability WHERE recurrence_id = ? AND faculty_id = ? AND active = 1 FOR UPDATE');
            $q->execute([$id, $faculty]); $old = $q->fetch();
            if (!$old) throw new InvalidArgumentException('Recurring schedule not found for your account.');
            if ((int)($data['version'] ?? 0) !== (int)$old['version']) throw new InvalidArgumentException('This schedule changed. Refresh before editing.');
        } elseif ($action === 'recurring_delete') {
            throw new InvalidArgumentException('Select a recurring schedule to remove.');
        }
        // Booked, pending, cancelled, and past occurrences are never changed or removed.
        if ($id > 0) {
            $q = $pdo->prepare("DELETE FROM cs_schedules WHERE recurrence_id = ? AND faculty_id = ? AND status = 'OPEN' AND student_id IS NULL AND start_at > ?");
            $q->execute([$id, $faculty, date('Y-m-d H:i:s')]);
        }
        if ($action === 'recurring_delete') {
            $q = $pdo->prepare('UPDATE cs_recurring_availability SET active = 0, version = version + 1 WHERE recurrence_id = ? AND faculty_id = ?');
            $q->execute([$id, $faculty]); $pdo->commit();
            return ['message' => 'Recurring schedule removed. Existing appointments and past dates were kept.', 'data' => ['recurrence_id' => $id]];
        }
        if ($id === 0) {
            $q = $pdo->prepare('INSERT INTO cs_recurring_availability (faculty_id, weekday, start_time, end_time, date_from, date_to, category, location) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
            $q->execute([$faculty, $plan['weekday'], $plan['start_time'], $plan['end_time'], $plan['date_from'], $plan['date_to'], $plan['category'], $plan['location']]);
            $id = (int)$pdo->lastInsertId();
        } else {
            $q = $pdo->prepare('UPDATE cs_recurring_availability SET weekday=?, start_time=?, end_time=?, date_from=?, date_to=?, category=?, location=?, version=version+1 WHERE recurrence_id=? AND faculty_id=?');
            $q->execute([$plan['weekday'], $plan['start_time'], $plan['end_time'], $plan['date_from'], $plan['date_to'], $plan['category'], $plan['location'], $id, $faculty]);
        }
        $created = 0; $past = 0; $protected = 0;
        foreach ($plan['dates'] as $date) {
            $start = "$date {$plan['start_time']}:00"; $end = "$date {$plan['end_time']}:00";
            if ($start <= date('Y-m-d H:i:s')) { $past++; continue; }
            // Do not regenerate a date with an appointment belonging to this series.
            $q = $pdo->prepare('SELECT 1 FROM cs_schedules WHERE recurrence_id = ? AND schedule_date = ? AND student_id IS NOT NULL LIMIT 1');
            $q->execute([$id, $date]);
            if ($q->fetchColumn()) { $protected++; continue; }
            if (rec_overlap($pdo, $faculty, $start, $end)) throw new InvalidArgumentException("Overlapping availability or appointment on $date. Nothing was changed.");
            $slot = date('h:i A', strtotime($start)) . ' - ' . date('h:i A', strtotime($end));
            $q = $pdo->prepare("INSERT INTO cs_schedules (faculty_id, schedule_date, time_slot, start_at, end_at, category, location, recurrence_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            $q->execute([$faculty, $date, $slot, $start, $end, $plan['category'], $plan['location'], $id]); $created++;
        }
        if (!$created && !$protected) throw new InvalidArgumentException('No future occurrences in this range. Choose a later date range.');
        $pdo->commit();
        return ['message' => "$created available dates saved. $protected appointment dates kept; $past past dates skipped.", 'data' => ['recurrence_id' => $id, 'generated' => $created, 'protected' => $protected, 'skipped_past' => $past]];
    } catch (Throwable $e) { if ($pdo->inTransaction()) $pdo->rollBack(); throw $e; }
}
