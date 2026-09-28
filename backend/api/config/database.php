<?php
// ==========================================
// CITEC Scheduling System Database Connection
// ==========================================

// Database Configuration - Update these credentials for x10hosting or local deployment
define('DB_HOST', getenv('DB_HOST') ? getenv('DB_HOST') : 'localhost');
define('DB_NAME', getenv('DB_NAME') ? getenv('DB_NAME') : 'citec_scheduling');
define('DB_USER', getenv('DB_USER') ? getenv('DB_USER') : 'root');
define('DB_PASSWORD', getenv('DB_PASSWORD') ? getenv('DB_PASSWORD') : '');

function getDatabaseConnection() {
    $dsn = "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=utf8mb4";
    $options = [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
    ];

    try {
        return new PDO($dsn, DB_USER, DB_PASSWORD, $options);
    } catch (PDOException $e) {
        // If database doesn't exist, attempt connecting without DB_NAME to create it
        if ($e->getCode() == 1049 || strpos($e->getMessage(), 'Unknown database') !== false) {
            try {
                $rootDsn = "mysql:host=" . DB_HOST . ";charset=utf8mb4";
                $pdoRoot = new PDO($rootDsn, DB_USER, DB_PASSWORD, $options);
                $pdoRoot->exec("CREATE DATABASE IF NOT EXISTS `" . DB_NAME . "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                return new PDO($dsn, DB_USER, DB_PASSWORD, $options);
            } catch (PDOException $e2) {
                // Return detailed connection error
                http_response_code(200);
                header('Content-Type: application/json');
                echo json_encode([
                    "success" => false,
                    "message" => "Database connection failed (" . DB_NAME . "): " . $e2->getMessage()
                ]);
                exit();
            }
        }

        // Return detailed connection error for debugging
        http_response_code(200);
        header('Content-Type: application/json');
        echo json_encode([
            "success" => false,
            "message" => "Database connection failed (" . DB_USER . "@" . DB_HOST . "): " . $e->getMessage()
        ]);
        exit();
    }
}
?>
