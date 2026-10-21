<?php
// Dedicated disposable test instance only. Never run this against hosting.
if (getenv('DB_PORT') !== '3307' || getenv('DB_NAME') !== 'citec_recurring_test') exit('Requires isolated test database on port 3307.');
$pdo = new PDO('mysql:host=127.0.0.1;port=3307;charset=utf8mb4', 'root', '', [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$pdo->exec('CREATE DATABASE IF NOT EXISTS citec_recurring_test CHARACTER SET utf8mb4');
$pdo->exec('USE citec_recurring_test');
$sql = file_get_contents(__DIR__ . '/../backend/database.sql');
$sql = preg_replace('/CREATE DATABASE IF NOT EXISTS citec_scheduling;|USE citec_scheduling;/', '', $sql);
$pdo->exec($sql);
$password = getenv('TEST_PASSWORD');
if (!$password) exit('Set TEST_PASSWORD.');
foreach ([['users', 'student_id', '98765', 'Automation Student'], ['instructors', 'faculty_id', '9876', 'Automation Professor']] as [$table, $id, $number, $name]) {
    $q = $pdo->prepare("INSERT INTO $table ($id, full_name, email, password) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE password=VALUES(password)");
    $q->execute([$number, $name, strtolower(str_replace(' ', '.', $name)) . '@example.invalid', password_hash($password, PASSWORD_DEFAULT)]);
}
echo "Isolated test accounts prepared.\n";
