package com.camunda.demo.insecure;

import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ❌ LỖI BẢO MẬT #4: HARDCODED CREDENTIALS & SENSITIVE DATA EXPOSURE
 *
 * Controller này chứa các lỗi về quản lý credentials và sensitive data.
 *
 * VẤN ĐỀ:
 * - Hardcoded credentials trong source code
 * - API keys exposed
 * - Sensitive data trong logs
 * - Detailed error messages
 * - Missing HTTPS enforcement
 * - Insecure configuration
 *
 * TẤN CÔNG:
 * - Source code leak → attacker có credentials
 * - Log files leak → expose sensitive data
 * - Error messages → information disclosure
 *
 * CHUẨN BẢO MẬT VI PHẠM:
 * - OWASP Top 10 (A05:2021 - Security Misconfiguration)
 * - CWE-798: Use of Hard-coded Credentials
 * - CWE-209: Information Exposure Through Error Message
 * - CWE-532: Information Exposure Through Log Files
 */
@RestController
@RequestMapping("/api/insecure/data")
public class SensitiveDataController {

    private static final Logger logger = Logger.getLogger(SensitiveDataController.class.getName());

    // ❌ NGUY HIỂM: Hardcoded credentials
    private static final String DB_USERNAME = "admin";
    private static final String DB_PASSWORD = "SuperSecret123!";
    private static final String DB_URL = "jdbc:postgresql://production-db.example.com:5432/maindb";

    // ❌ NGUY HIỂM: Hardcoded API keys (THESE ARE FAKE EXAMPLES FOR DEMO)
    private static final String AWS_ACCESS_KEY = "AKIA_FAKE_EXAMPLE_KEY_1234567890";
    private static final String AWS_SECRET_KEY = "FAKE_AWS_SECRET_KEY_EXAMPLE_FOR_DEMO_ONLY_1234567890";
    private static final String STRIPE_API_KEY = "sk_test_FAKE_STRIPE_KEY_FOR_DEMO_ONLY_12345";

    // ❌ NGUY HIỂM: Hardcoded encryption key
    private static final String MASTER_ENCRYPTION_KEY = "MyVerySecretEncryptionKey2024!";

    // ❌ NGUY HIỂM: Hardcoded JWT secret
    private static final String JWT_SECRET = "ThisIsMyJWTSecretKey123456789";

    /**
     * ❌ LỖI: Expose database credentials
     */
    @GetMapping("/db-config")
    public Map<String, Object> getDatabaseConfig() {
        Map<String, Object> config = new HashMap<>();

        // ❌ NGUY HIỂM: Return database credentials
        config.put("url", DB_URL);
        config.put("username", DB_USERNAME);
        config.put("password", DB_PASSWORD);
        config.put("driver", "org.postgresql.Driver");

        System.out.println("⚠️  Exposing database credentials via API!");

        return config;
    }

    /**
     * ❌ LỖI: Expose API keys
     */
    @GetMapping("/api-keys")
    public Map<String, String> getApiKeys() {
        Map<String, String> keys = new HashMap<>();

        // ❌ NGUY HIỂM: Return sensitive API keys
        keys.put("aws_access_key", AWS_ACCESS_KEY);
        keys.put("aws_secret_key", AWS_SECRET_KEY);
        keys.put("stripe_key", STRIPE_API_KEY);
        keys.put("jwt_secret", JWT_SECRET);

        System.out.println("⚠️  Exposing API keys via endpoint!");

        return keys;
    }

    /**
     * ❌ LỖI: Detailed error messages
     */
    @PostMapping("/process-payment")
    public Map<String, Object> processPayment(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String cardNumber = (String) request.get("cardNumber");
            String cvv = (String) request.get("cvv");

            // ❌ NGUY HIỂM: Log sensitive data
            logger.info("Processing payment for card: " + cardNumber + ", CVV: " + cvv);
            System.out.println("⚠️  Logging credit card details!");

            // Simulate payment processing
            if (cardNumber.length() < 16) {
                throw new Exception("Invalid card number. Received: " + cardNumber);
            }

            response.put("success", true);
            response.put("message", "Payment processed");

        } catch (Exception e) {
            // ❌ NGUY HIỂM: Expose detailed error với sensitive data
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("stackTrace", e.getStackTrace());
            response.put("requestData", request); // ❌ Include original request
        }

        return response;
    }

    /**
     * ❌ LỖI: Connect to database với hardcoded credentials
     */
    @GetMapping("/test-db-connection")
    public Map<String, Object> testDatabaseConnection() {
        Map<String, Object> response = new HashMap<>();

        try {
            // ❌ NGUY HIỂM: Sử dụng hardcoded credentials
            System.out.println("⚠️  Connecting with hardcoded credentials:");
            System.out.println("    URL: " + DB_URL);
            System.out.println("    Username: " + DB_USERNAME);
            System.out.println("    Password: " + DB_PASSWORD);

            Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);

            response.put("success", true);
            response.put("message", "Database connected successfully");
            response.put("connectionString", DB_URL); // ❌ Expose connection string

            conn.close();

        } catch (Exception e) {
            response.put("success", false);
            // ❌ NGUY HIỂM: Detailed error message
            response.put("error", e.getMessage());
            response.put("url", DB_URL);
            response.put("username", DB_USERNAME);
        }

        return response;
    }

    /**
     * ❌ LỖI: Logging sensitive information
     */
    @PostMapping("/user-activity")
    public Map<String, Object> logUserActivity(@RequestBody Map<String, Object> activity) {
        // ❌ NGUY HIỂM: Log toàn bộ user data (có thể chứa sensitive info)
        logger.info("User activity: " + activity.toString());

        System.out.println("⚠️  Full user activity logged:");
        System.out.println(activity);

        // Log có thể chứa:
        // - Passwords
        // - Credit card numbers
        // - SSN
        // - Personal information
        // - Session tokens

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Activity logged");
        return response;
    }

    /**
     * ❌ LỖI: Return sensitive user data
     */
    @GetMapping("/user/{userId}")
    public Map<String, Object> getUserDetails(@PathVariable String userId) {
        // ❌ NGUY HIỂM: Không check authorization
        // ❌ NGUY HIỂM: Return tất cả sensitive data

        Map<String, Object> user = new HashMap<>();
        user.put("id", userId);
        user.put("name", "John Doe");
        user.put("email", "john@example.com");

        // ❌ NGUY HIỂM: Return sensitive information
        user.put("ssn", "123-45-6789");
        user.put("creditCard", "4532-1234-5678-9012");
        user.put("cvv", "123");
        user.put("salary", 75000);
        user.put("password", "user_password_123");
        user.put("securityQuestion", "What is your pet's name?");
        user.put("securityAnswer", "Fluffy");

        System.out.println("⚠️  Returning all sensitive user data without authorization!");

        return user;
    }

    /**
     * ❌ LỖI: Debug endpoint trong production
     */
    @GetMapping("/debug/config")
    public Map<String, Object> getDebugConfig() {
        // ❌ NGUY HIỂM: Debug endpoint không nên có trong production
        Map<String, Object> config = new HashMap<>();

        // ❌ NGUY HIỂM: Expose toàn bộ system configuration
        config.put("environment", "production");
        config.put("database", Map.of(
                "url", DB_URL,
                "username", DB_USERNAME,
                "password", DB_PASSWORD
        ));
        config.put("aws", Map.of(
                "accessKey", AWS_ACCESS_KEY,
                "secretKey", AWS_SECRET_KEY,
                "region", "us-east-1"
        ));
        config.put("stripe", Map.of(
                "apiKey", STRIPE_API_KEY
        ));
        config.put("encryption", Map.of(
                "masterKey", MASTER_ENCRYPTION_KEY
        ));
        config.put("jwt", Map.of(
                "secret", JWT_SECRET,
                "expiration", "24h"
        ));

        // ❌ NGUY HIỂM: System properties có thể chứa sensitive info
        config.put("systemProperties", System.getProperties());
        config.put("environmentVariables", System.getenv());

        System.out.println("⚠️  Exposing complete system configuration!");

        return config;
    }

    /**
     * ❌ LỖI: Missing security headers
     */
    @GetMapping("/insecure-page")
    public String getInsecurePage() {
        // ❌ NGUY HIỂM: Không set security headers:
        // - X-Frame-Options (clickjacking)
        // - X-Content-Type-Options (MIME sniffing)
        // - Content-Security-Policy (XSS)
        // - Strict-Transport-Security (HTTPS)

        return "<html><body><h1>Insecure Page</h1></body></html>";
    }

    /**
     * ❌ LỖI: Backup files accessible
     */
    @GetMapping("/backup")
    public Map<String, Object> getBackupInfo() {
        Map<String, Object> backup = new HashMap<>();

        // ❌ NGUY HIỂM: Expose backup file locations
        backup.put("databaseBackup", "/backups/db_backup_2024.sql");
        backup.put("configBackup", "/backups/config.backup");
        backup.put("credentials", "/backups/credentials.txt");

        // ❌ NGUY HIỂM: Provide download links
        backup.put("downloadUrl", "http://example.com/backups/full_backup.zip");

        return backup;
    }
}
