-- ⚠️ INSECURE DATABASE INITIALIZATION - FOR DEMO ONLY ⚠️

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL
);

-- ❌ NGUY HIỂM: Insert users với plaintext passwords
INSERT INTO users (username, password, email, role) VALUES
('admin', 'admin123', 'admin@example.com', 'ADMIN'),
('user1', 'password', 'user1@example.com', 'USER'),
('john', 'john123', 'john@example.com', 'USER'),
('alice', 'alice2024', 'alice@example.com', 'USER'),
('bob', '123456', 'bob@example.com', 'USER');

-- ❌ NGUY HIỂM: Comments chứa sensitive information
-- Default admin password: admin123
-- Database backup location: /var/backups/db/
-- API Key: sk_test_FAKE_STRIPE_KEY_FOR_DEMO_ONLY (this is a fake example)
