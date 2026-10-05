<?php
// ==========================================
// CITEC Scheduling System - User Login API
// ==========================================

header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST, GET");
header("Access-Control-Allow-Headers: Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");

require_once __DIR__ . '/config/database.php';

// Allow GET for API status check
if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    echo json_encode(["success" => true, "message" => "Login API is active and online."]);
    exit();
}

// Read raw POST body JSON or $_POST form-data
$input = file_get_contents("php://input");
$data = json_decode($input, true);

if (!$data || !is_array($data)) {
    $data = $_POST;
}

$identifier     = isset($data['identifier']) ? trim($data['identifier']) : (isset($data['email']) ? trim($data['email']) : '');
$password       = isset($data['password']) ? trim($data['password']) : '';
$requested_role = isset($data['role']) ? strtoupper(trim($data['role'])) : '';

// Validation: require credentials
if (empty($identifier) || empty($password)) {
    echo json_encode(["success" => false, "message" => "Student/Faculty ID or Email and Password are required."]);
    exit();
}

$pdo = getDatabaseConnection();

function getTableColumnsList($pdo, $tableName) {
    try {
        $stmt = $pdo->query("DESCRIBE `$tableName`");
        return $stmt->fetchAll(PDO::FETCH_COLUMN);
    } catch (Exception $e) {
        return [];
    }
}

try {
    $isFacultyRequest = ($requested_role === 'FACULTY' || $requested_role === 'INSTRUCTOR' || $requested_role === 'PROFESSOR');
    $isStudentRequest = ($requested_role === 'STUDENT');

    // Attempt Faculty Login
    if ($isFacultyRequest || empty($requested_role)) {
        $instCols = getTableColumnsList($pdo, 'instructors');
        $tableName = !empty($instCols) ? 'instructors' : 'faculty';
        $instCols = getTableColumnsList($pdo, $tableName);

        if (!empty($instCols)) {
            $idCol = in_array('faculty_id', $instCols) ? 'faculty_id' : (in_array('instructor_id', $instCols) ? 'instructor_id' : 'student_id');
            $pkCol = in_array('instructor_id', $instCols) ? 'instructor_id' : 'user_id';

            $sql = "SELECT * FROM `$tableName` WHERE LOWER(`email`) = LOWER(:id1) OR LOWER(`$idCol`) = LOWER(:id2) LIMIT 1";
            $stmt = $pdo->prepare($sql);
            $stmt->execute([':id1' => $identifier, ':id2' => $identifier]);
            $inst = $stmt->fetch();

            if ($inst && password_verify($password, $inst['password'])) {
                $firstName = isset($inst['first_name']) ? $inst['first_name'] : '';
                $lastName  = isset($inst['last_name']) ? $inst['last_name'] : '';
                $fullName  = isset($inst['full_name']) ? $inst['full_name'] : trim($firstName . ' ' . $lastName);

                echo json_encode([
                    "success" => true,
                    "message" => "Faculty login successful.",
                    "user" => [
                        "user_id"    => isset($inst[$pkCol]) ? (int)$inst[$pkCol] : (isset($inst['user_id']) ? (int)$inst['user_id'] : 1),
                        "student_id" => isset($inst[$idCol]) ? $inst[$idCol] : $identifier,
                        "first_name" => $firstName,
                        "last_name"  => $lastName,
                        "full_name"  => $fullName,
                        "email"      => $inst['email'],
                        "role"       => "FACULTY",
                        "department" => isset($inst['department']) ? $inst['department'] : ''
                    ]
                ]);
                exit();
            }
        }
    }

    // Attempt Student Login
    if ($isStudentRequest || empty($requested_role)) {
        $userCols = getTableColumnsList($pdo, 'users');
        $tableName = !empty($userCols) ? 'users' : 'students';
        $userCols = getTableColumnsList($pdo, $tableName);

        if (!empty($userCols)) {
            $idCol = in_array('student_id', $userCols) ? 'student_id' : 'id';
            $pkCol = in_array('user_id', $userCols) ? 'user_id' : 'id';

            $sql = "SELECT * FROM `$tableName` WHERE LOWER(`email`) = LOWER(:id1) OR LOWER(`$idCol`) = LOWER(:id2) LIMIT 1";
            $stmt = $pdo->prepare($sql);
            $stmt->execute([':id1' => $identifier, ':id2' => $identifier]);
            $usr = $stmt->fetch();

            if ($usr && password_verify($password, $usr['password'])) {
                $firstName = isset($usr['first_name']) ? $usr['first_name'] : '';
                $lastName  = isset($usr['last_name']) ? $usr['last_name'] : '';
                $fullName  = isset($usr['full_name']) ? $usr['full_name'] : trim($firstName . ' ' . $lastName);

                echo json_encode([
                    "success" => true,
                    "message" => "Student login successful.",
                    "user" => [
                        "user_id"    => isset($usr[$pkCol]) ? (int)$usr[$pkCol] : 1,
                        "student_id" => isset($usr[$idCol]) ? $usr[$idCol] : $identifier,
                        "first_name" => $firstName,
                        "last_name"  => $lastName,
                        "full_name"  => $fullName,
                        "email"      => $usr['email'],
                        "role"       => "STUDENT"
                    ]
                ]);
                exit();
            }
        }
    }

    echo json_encode([
        "success" => false,
        "message" => "Invalid email/ID or password."
    ]);

} catch (PDOException $e) {
    echo json_encode([
        "success" => false,
        "message" => "Database error: " . $e->getMessage()
    ]);
}
?>
