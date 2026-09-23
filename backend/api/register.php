<?php
// ==========================================
// CITEC Scheduling System - User Registration API
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

$student_id = isset($data['student_id']) ? trim($data['student_id']) : (isset($data['faculty_id']) ? trim($data['faculty_id']) : '');
$full_name  = isset($data['full_name']) ? trim($data['full_name']) : '';
$email      = isset($data['email']) ? trim($data['email']) : '';
$password   = isset($data['password']) ? trim($data['password']) : '';
$role       = isset($data['role']) ? strtoupper(trim($data['role'])) : 'STUDENT';
$department = isset($data['department']) ? trim($data['department']) : '';

// Validation: complete all required fields
if (empty($student_id) || empty($full_name) || empty($email) || empty($password)) {
    echo json_encode(["success" => false, "message" => "Please complete all required fields."]);
    exit();
}

// Validate Email Format
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
    echo json_encode(["success" => false, "message" => "Please enter a valid email address."]);
    exit();
}

// Validate Password Length
if (strlen($password) < 8) {
    echo json_encode(["success" => false, "message" => "Password must be at least 8 characters."]);
    exit();
}

$pdo = getDatabaseConnection();

try {
    $hashed_password = password_hash($password, PASSWORD_BCRYPT);

    if ($role === 'FACULTY' || $role === 'INSTRUCTOR' || $role === 'PROFESSOR') {
        // --- FACULTY / INSTRUCTOR REGISTRATION (Stored in instructors table) ---

        // Check duplicate faculty_id in instructors table
        $checkFacultyStmt = $pdo->prepare("SELECT instructor_id FROM instructors WHERE faculty_id = :faculty_id LIMIT 1");
        $checkFacultyStmt->execute([':faculty_id' => $student_id]);
        if ($checkFacultyStmt->fetch()) {
            echo json_encode(["success" => false, "message" => "Faculty ID is already registered."]);
            exit();
        }

        // Check duplicate email in instructors table
        $checkEmailStmt = $pdo->prepare("SELECT instructor_id FROM instructors WHERE email = :email LIMIT 1");
        $checkEmailStmt->execute([':email' => $email]);
        if ($checkEmailStmt->fetch()) {
            echo json_encode(["success" => false, "message" => "Email is already registered in instructor database."]);
            exit();
        }

        // Insert instructor record into instructors table
        $insertSql = "INSERT INTO instructors (faculty_id, full_name, email, password, department, role)
                      VALUES (:faculty_id, :full_name, :email, :password, :department, :role)";
        $insertStmt = $pdo->prepare($insertSql);
        $success = $insertStmt->execute([
            ':faculty_id' => $student_id,
            ':full_name'  => $full_name,
            ':email'      => $email,
            ':password'   => $hashed_password,
            ':department' => $department,
            ':role'       => 'FACULTY'
        ]);

        if ($success) {
            echo json_encode([
                "success" => true,
                "message" => "Faculty registration successful."
            ]);
        } else {
            echo json_encode([
                "success" => false,
                "message" => "Failed to register faculty account. Please try again."
            ]);
        }

    } else {
        // --- STUDENT REGISTRATION (Stored in users table) ---

        // Check duplicate student_id in users table
        $checkStudentStmt = $pdo->prepare("SELECT user_id FROM users WHERE student_id = :student_id LIMIT 1");
        $checkStudentStmt->execute([':student_id' => $student_id]);
        if ($checkStudentStmt->fetch()) {
            echo json_encode(["success" => false, "message" => "Student ID is already registered."]);
            exit();
        }

        // Check duplicate email in users table
        $checkEmailStmt = $pdo->prepare("SELECT user_id FROM users WHERE email = :email LIMIT 1");
        $checkEmailStmt->execute([':email' => $email]);
        if ($checkEmailStmt->fetch()) {
            echo json_encode(["success" => false, "message" => "Email is already registered."]);
            exit();
        }

        // Insert student record into users table
        $insertSql = "INSERT INTO users (student_id, full_name, email, password, role)
                      VALUES (:student_id, :full_name, :email, :password, :role)";
        $insertStmt = $pdo->prepare($insertSql);
        $success = $insertStmt->execute([
            ':student_id' => $student_id,
            ':full_name'  => $full_name,
            ':email'      => $email,
            ':password'   => $hashed_password,
            ':role'       => 'STUDENT'
        ]);

        if ($success) {
            echo json_encode([
                "success" => true,
                "message" => "Student registration successful."
            ]);
        } else {
            echo json_encode([
                "success" => false,
                "message" => "Failed to register student account. Please try again."
            ]);
        }
    }

} catch (PDOException $e) {
    echo json_encode([
        "success" => false,
        "message" => "Database error occurred. Please try again."
    ]);
}
?>
