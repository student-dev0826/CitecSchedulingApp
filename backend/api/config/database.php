<?php
// ==========================================
// CITEC Scheduling System Database Connection
// ==========================================

// Database Configuration - Supports local development and cPanel/x10hosting
define('DB_HOST', getenv('DB_HOST') ? getenv('DB_HOST') : 'localhost');
define('DB_NAME', getenv('DB_NAME') ? getenv('DB_NAME') : 'citec_scheduling');
define('DB_USER', getenv('DB_USER') ? getenv('DB_USER') : 'root');
define('DB_PASSWORD', getenv('DB_PASSWORD') ? getenv('DB_PASSWORD') : '');

function getDatabaseConnection() {
    $options = [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
    ];

    // Array of database configurations to attempt in order
    $configs = [
        ['host' => DB_HOST, 'name' => DB_NAME, 'user' => DB_USER, 'pass' => DB_PASSWORD],
        ['host' => 'localhost', 'name' => 'citecsch_scheduling', 'user' => 'citecsch_user', 'pass' => '']
    ];

    foreach ($configs as $config) {
        try {
            $dsn = "mysql:host=" . $config['host'] . ";dbname=" . $config['name'] . ";charset=utf8mb4";
            return new PDO($dsn, $config['user'], $config['pass'], $options);
        } catch (PDOException $e) {
            // Attempt creating database locally if missing
            if (($e->getCode() == 1049 || strpos($e->getMessage(), 'Unknown database') !== false) && $config['user'] === 'root') {
                try {
                    $rootDsn = "mysql:host=" . $config['host'] . ";charset=utf8mb4";
                    $pdoRoot = new PDO($rootDsn, $config['user'], $config['pass'], $options);
                    $pdoRoot->exec("CREATE DATABASE IF NOT EXISTS `" . $config['name'] . "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    return new PDO($dsn, $config['user'], $config['pass'], $options);
                } catch (PDOException $e2) {
                    continue;
                }
            }
        }
    }

    // Fallback response if remote database server is unavailable
    http_response_code(200);
    header('Content-Type: application/json');
    echo json_encode([
        "success" => false,
        "message" => "Database connection unavailable."
    ]);
    exit();
}
?>
