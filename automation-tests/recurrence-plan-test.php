<?php
require __DIR__ . '/../backend/api/recurrence.php';
function check($value, $message) { if (!$value) throw new RuntimeException($message); }
$base = ['weekday' => 2, 'date_from' => '2026-10-01', 'date_to' => '2026-10-31', 'start_time' => '09:00', 'end_time' => '10:00', 'category' => 'Test', 'location' => 'Test'];
check(rec_plan($base)['dates'] === ['2026-10-06', '2026-10-13', '2026-10-20', '2026-10-27'], 'October Tuesdays');
check(rec_plan(array_replace($base, ['weekday' => 7]))['dates'] === ['2026-10-04', '2026-10-11', '2026-10-18', '2026-10-25'], 'Sundays');
check(rec_plan(array_replace($base, ['date_from' => '2028-02-29', 'date_to' => '2028-02-29']))['dates'] === ['2028-02-29'], 'Leap day and inclusive bounds');
foreach ([['weekday' => 0], ['weekday' => 8], ['start_time' => '10:00'], ['end_time' => '25:00'], ['date_from' => '2026-02-30'], ['date_to' => '2026-09-01'], ['date_to' => '2028-01-01'], ['location' => '']] as $bad) {
    try { rec_plan(array_replace($base, $bad)); throw new RuntimeException('Invalid plan accepted: ' . json_encode($bad)); } catch (InvalidArgumentException $expected) {}
}
echo "PASS: October Tuesdays, Sunday, leap day, inclusive range, and eight invalid input cases.\n";
