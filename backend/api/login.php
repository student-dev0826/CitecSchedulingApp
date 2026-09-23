<?php
// ==========================================
// CITEC Scheduling System - User Login API
// ==========================================

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");
header("Access-Control-Allow-Headers: Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");

require_once __DIR__ . '/config/database.php';

// Only accept POST requests
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(405);
    echo json_encode(["success" => false, "message" => "Method Not Allowed."]);
    exit();
}

// Read raw POST body JSON
$input = file_get_contents("php://input");
$data = json_decode($input, true);

if (!$data) {
    echo json_encode(["success" => false, "message" => "Invalid JSON payload."]);
    exit();
}

$identifier = isset($data['identifier']) ? trim($data['identifier']) : '';
$password   = isset($data['password']) ? trim($data['password']) : '';

// Validation: require credentials
if (empty($identifier) || empty($password)) {
    echo json_encode(["success" => false, "message" => "Email/Student ID and Password are required."]);
    exit();
}

$pdo = getDatabaseConnection();

try {
    // Query user by email OR student_id
    $sql = "SELECT user_id, student_id, full_name, email, password
            FROM users
            WHERE email = :identifier OR student_id = :identifier
            LIMIT 1";

    $stmt = $pdo->prepare($sql);
    $stmt->execute([':identifier' => $identifier]);
    $user = $stmt->fetch();

    if ($user && password_verify($password, $user['password'])) {
        // Login successful - Return user details WITHOUT returning the password
        echo json_encode([
            "success" => true,
            "message" => "Login successful.",
            "user" => [
                "user_id"    => (int)$user['user_id'],
                "student_id" => $user['student_id'],
                "full_name"  => $user['full_name'],
                "email"      => $user['email']
            ]
        ]);
    } else {
        // Generic error message for security
        echo json_encode([
            "success" => false,
            "message" => "Invalid email/student ID or password."
        ]);
    }

} catch (PDOException $e) {
    echo json_encode([
        "success" => false,
        "message" => "Database error occurred. Please try again."
    ]);
}
?>
