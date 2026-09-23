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

// Read raw POST body JSON or $_POST form-data
$input = file_get_contents("php://input");
$data = json_decode($input, true);

if (!$data) {
    $data = $_POST;
}

$identifier     = isset($data['identifier']) ? trim($data['identifier']) : (isset($data['email']) ? trim($data['email']) : '');
$password       = isset($data['password']) ? trim($data['password']) : '';
$requested_role = isset($data['role']) ? strtoupper(trim($data['role'])) : '';

// Validation: require credentials
if (empty($identifier) || empty($password)) {
    echo json_encode(["success" => false, "message" => "Email/ID and Password are required."]);
    exit();
}

$pdo = getDatabaseConnection();

try {
    // Check if user is attempting Faculty login vs Student login
    if ($requested_role === 'FACULTY' || $requested_role === 'INSTRUCTOR' || $requested_role === 'PROFESSOR') {
        // --- FACULTY / INSTRUCTOR LOGIN ONLY (queries instructors table) ---
        $sqlInst = "SELECT instructor_id AS user_id, faculty_id AS student_id, full_name, email, password, department, 'FACULTY' as role
                    FROM instructors
                    WHERE email = :identifier OR faculty_id = :identifier
                    LIMIT 1";

        $stmtInst = $pdo->prepare($sqlInst);
        $stmtInst->execute([':identifier' => $identifier]);
        $instructor = $stmtInst->fetch();

        if ($instructor && password_verify($password, $instructor['password'])) {
            echo json_encode([
                "success" => true,
                "message" => "Faculty login successful.",
                "user" => [
                    "user_id"    => (int)$instructor['user_id'],
                    "student_id" => $instructor['student_id'],
                    "full_name"  => $instructor['full_name'],
                    "email"      => $instructor['email'],
                    "role"       => "FACULTY",
                    "department" => $instructor['department']
                ]
            ]);
            exit();
        } else {
            // Check if account exists in student database to give clear error message
            $checkUser = $pdo->prepare("SELECT user_id FROM users WHERE email = :identifier OR student_id = :identifier LIMIT 1");
            $checkUser->execute([':identifier' => $identifier]);
            if ($checkUser->fetch()) {
                echo json_encode([
                    "success" => false,
                    "message" => "Student account detected. Please switch to the Student Portal to log in."
                ]);
            } else {
                echo json_encode([
                    "success" => false,
                    "message" => "Invalid Faculty ID/email or password."
                ]);
            }
            exit();
        }

    } elseif ($requested_role === 'STUDENT') {
        // --- STUDENT LOGIN ONLY (queries users table) ---
        $sqlUser = "SELECT user_id, student_id, full_name, email, password, COALESCE(role, 'STUDENT') as role
                    FROM users
                    WHERE email = :identifier OR student_id = :identifier
                    LIMIT 1";

        $stmtUser = $pdo->prepare($sqlUser);
        $stmtUser->execute([':identifier' => $identifier]);
        $user = $stmtUser->fetch();

        if ($user && password_verify($password, $user['password'])) {
            echo json_encode([
                "success" => true,
                "message" => "Student login successful.",
                "user" => [
                    "user_id"    => (int)$user['user_id'],
                    "student_id" => $user['student_id'],
                    "full_name"  => $user['full_name'],
                    "email"      => $user['email'],
                    "role"       => "STUDENT"
                ]
            ]);
            exit();
        } else {
            // Check if account exists in instructor database to give clear error message
            $checkInst = $pdo->prepare("SELECT instructor_id FROM instructors WHERE email = :identifier OR faculty_id = :identifier LIMIT 1");
            $checkInst->execute([':identifier' => $identifier]);
            if ($checkInst->fetch()) {
                echo json_encode([
                    "success" => false,
                    "message" => "Faculty/Instructor account detected. Please switch to the Faculty Portal to log in."
                ]);
            } else {
                echo json_encode([
                    "success" => false,
                    "message" => "Invalid Student ID/email or password."
                ]);
            }
            exit();
        }

    } else {
        // --- DUAL / FALLBACK CHECK (when role is not explicitly specified) ---
        // 1. Check instructors
        $sqlInst = "SELECT instructor_id AS user_id, faculty_id AS student_id, full_name, email, password, department, 'FACULTY' as role
                    FROM instructors
                    WHERE email = :identifier OR faculty_id = :identifier
                    LIMIT 1";
        $stmtInst = $pdo->prepare($sqlInst);
        $stmtInst->execute([':identifier' => $identifier]);
        $instructor = $stmtInst->fetch();

        if ($instructor && password_verify($password, $instructor['password'])) {
            echo json_encode([
                "success" => true,
                "message" => "Login successful.",
                "user" => [
                    "user_id"    => (int)$instructor['user_id'],
                    "student_id" => $instructor['student_id'],
                    "full_name"  => $instructor['full_name'],
                    "email"      => $instructor['email'],
                    "role"       => "FACULTY",
                    "department" => $instructor['department']
                ]
            ]);
            exit();
        }

        // 2. Check users
        $sqlUser = "SELECT user_id, student_id, full_name, email, password, COALESCE(role, 'STUDENT') as role
                    FROM users
                    WHERE email = :identifier OR student_id = :identifier
                    LIMIT 1";
        $stmtUser = $pdo->prepare($sqlUser);
        $stmtUser->execute([':identifier' => $identifier]);
        $user = $stmtUser->fetch();

        if ($user && password_verify($password, $user['password'])) {
            echo json_encode([
                "success" => true,
                "message" => "Login successful.",
                "user" => [
                    "user_id"    => (int)$user['user_id'],
                    "student_id" => $user['student_id'],
                    "full_name"  => $user['full_name'],
                    "email"      => $user['email'],
                    "role"       => "STUDENT"
                ]
            ]);
            exit();
        }

        echo json_encode([
            "success" => false,
            "message" => "Invalid email/ID or password."
        ]);
    }

} catch (PDOException $e) {
    echo json_encode([
        "success" => false,
        "message" => "Database error occurred. Please try again."
    ]);
}
?>
