<?php
// ==========================================
// CITEC Scheduling System - User Registration API
// ==========================================

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST, GET");
header("Access-Control-Allow-Headers: Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");

require_once __DIR__ . '/config/database.php';

// Allow GET for API status check
if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    echo json_encode(["success" => true, "message" => "Registration API is active and online."]);
    exit();
}

// Read raw POST body JSON or $_POST form-data
$input = file_get_contents("php://input");
$data = json_decode($input, true);

if (!$data || !is_array($data)) {
    $data = $_POST;
}

$student_id = isset($data['student_id']) ? trim($data['student_id']) : (isset($data['faculty_id']) ? trim($data['faculty_id']) : '');
$first_name = isset($data['first_name']) ? trim($data['first_name']) : '';
$last_name  = isset($data['last_name']) ? trim($data['last_name']) : '';
$full_name  = isset($data['full_name']) ? trim($data['full_name']) : '';

if (empty($first_name) && !empty($full_name)) {
    $parts = explode(' ', $full_name, 2);
    $first_name = $parts[0];
    $last_name = isset($parts[1]) ? $parts[1] : '';
}
if (empty($full_name) && (!empty($first_name) || !empty($last_name))) {
    $full_name = trim($first_name . ' ' . $last_name);
}

$email      = isset($data['email']) ? trim($data['email']) : '';
$password   = isset($data['password']) ? trim($data['password']) : '';
$role       = isset($data['role']) ? strtoupper(trim($data['role'])) : 'STUDENT';
$department = isset($data['department']) ? trim($data['department']) : '';

// Validation: complete all required fields
if (empty($student_id) || (empty($first_name) && empty($full_name)) || empty($email) || empty($password)) {
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

// Ensure required tables exist on the server automatically
function ensureTablesExist($pdo) {
    try {
        $pdo->exec("CREATE TABLE IF NOT EXISTS users (
            user_id INT AUTO_INCREMENT PRIMARY KEY,
            student_id VARCHAR(50) NOT NULL UNIQUE,
            first_name VARCHAR(50) DEFAULT NULL,
            last_name VARCHAR(50) DEFAULT NULL,
            full_name VARCHAR(100) DEFAULT NULL,
            email VARCHAR(150) NOT NULL UNIQUE,
            password VARCHAR(255) NOT NULL,
            role VARCHAR(20) DEFAULT 'STUDENT',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        $pdo->exec("CREATE TABLE IF NOT EXISTS instructors (
            instructor_id INT AUTO_INCREMENT PRIMARY KEY,
            faculty_id VARCHAR(50) NOT NULL UNIQUE,
            first_name VARCHAR(50) DEFAULT NULL,
            last_name VARCHAR(50) DEFAULT NULL,
            full_name VARCHAR(100) DEFAULT NULL,
            email VARCHAR(150) NOT NULL UNIQUE,
            department VARCHAR(100) DEFAULT NULL,
            password VARCHAR(255) NOT NULL,
            role VARCHAR(20) DEFAULT 'FACULTY',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    } catch (Exception $e) {
        // Table creation errors ignored if already existing or permissions restricted
    }
}

ensureTablesExist($pdo);

function getTableColumnsList($pdo, $tableName) {
    try {
        $stmt = $pdo->query("DESCRIBE `$tableName`");
        return $stmt->fetchAll(PDO::FETCH_COLUMN);
    } catch (Exception $e) {
        return [];
    }
}

try {
    $hashed_password = password_hash($password, PASSWORD_BCRYPT);
    $isFaculty = ($role === 'FACULTY' || $role === 'INSTRUCTOR' || $role === 'PROFESSOR');

    // Target Table Selection
    $tableName = $isFaculty ? 'instructors' : 'users';
    $cols = getTableColumnsList($pdo, $tableName);

    if (empty($cols) && $isFaculty) {
        $tableName = 'faculty';
        $cols = getTableColumnsList($pdo, $tableName);
    }
    if (empty($cols) && !$isFaculty) {
        $tableName = 'students';
        $cols = getTableColumnsList($pdo, $tableName);
    }

    if (empty($cols)) {
        echo json_encode(["success" => false, "message" => "Target database table '$tableName' not found on server."]);
        exit();
    }

    // Determine ID Column Name dynamically
    $idCol = 'student_id';
    if ($isFaculty && in_array('faculty_id', $cols)) {
        $idCol = 'faculty_id';
    } elseif (in_array('student_id', $cols)) {
        $idCol = 'student_id';
    } elseif (in_array('instructor_id', $cols)) {
        $idCol = 'instructor_id';
    } elseif (in_array('user_id', $cols)) {
        $idCol = 'user_id';
    } elseif (in_array('id', $cols)) {
        $idCol = 'id';
    }

    // Check duplicate ID
    if (in_array($idCol, $cols)) {
        $checkIdStmt = $pdo->prepare("SELECT * FROM `$tableName` WHERE `$idCol` = :id_val LIMIT 1");
        $checkIdStmt->execute([':id_val' => $student_id]);
        if ($checkIdStmt->fetch()) {
            $msg = $isFaculty ? "Faculty ID is already registered." : "Student ID is already registered.";
            echo json_encode(["success" => false, "message" => $msg]);
            exit();
        }
    }

    // Check duplicate Email
    if (in_array('email', $cols)) {
        $checkEmailStmt = $pdo->prepare("SELECT * FROM `$tableName` WHERE `email` = :email_val LIMIT 1");
        $checkEmailStmt->execute([':email_val' => $email]);
        if ($checkEmailStmt->fetch()) {
            echo json_encode(["success" => false, "message" => "Email is already registered."]);
            exit();
        }
    }

    // Construct Dynamic Insert Query based ONLY on columns that exist
    $fields = [];
    $params = [];

    if (in_array($idCol, $cols)) {
        $fields[] = "`$idCol`";
        $params[':id_val'] = $student_id;
    }

    if (in_array('first_name', $cols)) {
        $fields[] = "`first_name`";
        $params[':first_name'] = $first_name;
    }

    if (in_array('last_name', $cols)) {
        $fields[] = "`last_name`";
        $params[':last_name'] = $last_name;
    }

    if (in_array('full_name', $cols)) {
        $fields[] = "`full_name`";
        $params[':full_name'] = $full_name;
    }

    if (in_array('email', $cols)) {
        $fields[] = "`email`";
        $params[':email'] = $email;
    }

    if (in_array('password', $cols)) {
        $fields[] = "`password`";
        $params[':password'] = $hashed_password;
    }

    if (in_array('department', $cols) && $isFaculty) {
        $fields[] = "`department`";
        $params[':department'] = $department;
    }

    if (in_array('role', $cols)) {
        $fields[] = "`role`";
        $params[':role'] = $isFaculty ? 'FACULTY' : 'STUDENT';
    }

    $fieldList = implode(', ', $fields);
    $paramList = implode(', ', array_keys($params));

    $insertSql = "INSERT INTO `$tableName` ($fieldList) VALUES ($paramList)";
    $insertStmt = $pdo->prepare($insertSql);
    $success = $insertStmt->execute($params);

    if ($success) {
        $msg = $isFaculty ? "Faculty registration successful." : "Student registration successful.";
        echo json_encode(["success" => true, "message" => $msg]);
    } else {
        $errorInfo = $insertStmt->errorInfo();
        $errDetail = isset($errorInfo[2]) ? $errorInfo[2] : "Database insertion failed";
        echo json_encode(["success" => false, "message" => $errDetail]);
    }

} catch (PDOException $e) {
    echo json_encode([
        "success" => false,
        "message" => "Database error: " . $e->getMessage()
    ]);
}
?>
