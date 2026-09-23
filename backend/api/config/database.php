<?php
// ==========================================
// CITEC Scheduling System Database Connection
// ==========================================

// Database Configuration - Update these credentials for local or production deployment
define('DB_HOST', 'localhost');
define('DB_NAME', 'citec_scheduling');
define('DB_USER', 'root');       // For production, use a dedicated non-root MySQL user
define('DB_PASSWORD', '');       // Enter your MySQL password here

function getDatabaseConnection() {
    try {
        $dsn = "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=utf8mb4";
        $options = [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ];
        return new PDO($dsn, DB_USER, DB_PASSWORD, $options);
    } catch (PDOException $e) {
        // Return JSON error response if connection fails
        http_response_code(500);
        header('Content-Type: application/json');
        echo json_encode([
            "success" => false,
            "message" => "Database connection failed. Please try again later."
        ]);
        exit();
    }
}
?>
