-- CITEC Scheduling System Database Schema
-- Run this script in MySQL Workbench or phpMyAdmin

CREATE DATABASE IF NOT EXISTS citec_scheduling;

USE citec_scheduling;

-- Student users table
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    student_id VARCHAR(50) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'STUDENT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Faculty / Instructors table
CREATE TABLE IF NOT EXISTS instructors (
    instructor_id INT AUTO_INCREMENT PRIMARY KEY,
    faculty_id VARCHAR(50) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    department VARCHAR(100) DEFAULT NULL,
    role VARCHAR(20) DEFAULT 'FACULTY',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Consultation slots posted by faculty and booked by students.
-- (schedules.php also creates these automatically on first use.)
CREATE TABLE IF NOT EXISTS schedules (
    schedule_id INT AUTO_INCREMENT PRIMARY KEY,
    faculty_id INT NOT NULL,
    schedule_date DATE NOT NULL,
    time_slot VARCHAR(40) NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    category VARCHAR(100) NOT NULL,
    location VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    student_id INT DEFAULT NULL,
    purpose VARCHAR(255) DEFAULT NULL,
    transfer_reason VARCHAR(255) DEFAULT NULL,
    decline_reason VARCHAR(255) DEFAULT NULL,
    cancel_reason VARCHAR(255) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_faculty_slot (faculty_id, schedule_date, time_slot),
    KEY idx_student (student_id),
    KEY idx_start (start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    user_role VARCHAR(10) NOT NULL,
    message VARCHAR(255) NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user (user_id, user_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Chat messages (chat.php also creates/upgrades this automatically).
-- is_read = 0 means the recipient has not opened the conversation yet.
CREATE TABLE IF NOT EXISTS chat_messages (
    message_id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id VARCHAR(50) NOT NULL,
    sender_name VARCHAR(100) NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    recipient_id VARCHAR(50) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    message_text TEXT NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_recipient (recipient_id, is_read),
    KEY idx_pair (sender_id, recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Upgrade for existing installs (schedules.php also adds this automatically):
-- ALTER TABLE schedules ADD COLUMN cancel_reason VARCHAR(255) DEFAULT NULL;
