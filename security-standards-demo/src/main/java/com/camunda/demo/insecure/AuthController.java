package com.camunda.demo.insecure;

import org.springframework.web.bind.annotation.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.*;

/**
 * ❌ LỖI BẢO MẬT #3: INSECURE AUTHENTICATION & PASSWORD STORAGE
 *
 * Controller này chứa các lỗi về authentication và cryptography.
 *
 * VẤN ĐỀ:
 * - Lưu password dạng plaintext
 * - Sử dụng weak hashing (MD5, SHA1)
 * - Không sử dụng salt cho password hash
 * - Weak session management
 * - Hardcoded encryption keys
 * - Missing authentication checks
 *
 * TẤN CÔNG:
 * - Password được lưu plaintext hoặc weak hash → dễ crack
 * - Session fixation attacks
 * - Brute force attacks (no rate limiting)
 * - Weak cryptography → dễ decrypt
 *
 * CHUẨN BẢO MẬT VI PHẠM:
 * - OWASP Top 10 (A07:2021 - Identification and Authentication Failures)
 * - CWE-259: Hard-coded Password
 * - CWE-327: Use of Broken Cryptographic Algorithm
 * - CWE-759: Use of One-Way Hash without Salt
 */
@RestController
@RequestMapping("/api/insecure/auth")
public class AuthController {

    // ❌ NGUY HIỂM: Lưu users trong memory với password plaintext
    private static Map<String, User> users = new HashMap<>();

    // ❌ NGUY HIỂM: Sessions không secure
    private static Map<String, String> sessions = new HashMap<>();

    // ❌ NGUY HIỂM: Hardcoded encryption key
    private static final String ENCRYPTION_KEY = "MySecretKey12345";

    static {
        // Initialize với một số users (password plaintext)
        users.put("admin", new User("admin", "admin123", "admin@example.com", "admin"));
        users.put("user1", new User("user1", "password", "user1@example.com", "user"));
    }

    /**
     * ❌ LỖI: Register với plaintext password
     */
    @PostMapping("/register-plaintext")
    public Map<String, Object> registerPlaintext(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String email = request.get("email");

        // ❌ NGUY HIỂM: Lưu password dạng plaintext
        User user = new User(username, password, email, "user");
        users.put(username, user);

        System.out.println("⚠️  Stored password in plaintext: " + password);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "User registered with plaintext password!");
        return response;
    }

    /**
     * ❌ LỖI: Register với MD5 hash (weak)
     */
    @PostMapping("/register-md5")
    public Map<String, Object> registerMD5(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String email = request.get("email");

        try {
            // ❌ NGUY HIỂM: MD5 là weak algorithm, dễ crack
            // Không sử dụng salt → rainbow table attack
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(password.getBytes());
            String hashedPassword = Base64.getEncoder().encodeToString(hash);

            User user = new User(username, hashedPassword, email, "user");
            users.put(username, user);

            System.out.println("⚠️  Stored password with weak MD5 hash (no salt): " + hashedPassword);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User registered with MD5 hash!");
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * ❌ LỖI: Login không rate limiting
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        // ❌ NGUY HIỂM: Không có rate limiting → brute force attack
        // ❌ NGUY HIỂM: Không có account lockout mechanism

        User user = users.get(username);

        if (user != null && user.password.equals(password)) {
            // ❌ NGUY HIỂM: Session ID dễ đoán (sequential)
            String sessionId = "SESSION_" + System.currentTimeMillis();
            sessions.put(sessionId, username);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", sessionId);
            response.put("message", "Login successful");

            // ❌ NGUY HIỂM: Return sensitive information
            response.put("user", user);

            return response;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);

        // ❌ NGUY HIỂM: Information disclosure - cho biết username có tồn tại không
        if (user == null) {
            response.put("message", "Username does not exist");
        } else {
            response.put("message", "Invalid password");
        }

        return response;
    }

    /**
     * ❌ LỖI: Missing authentication check
     */
    @GetMapping("/admin/users")
    public List<User> getAllUsers() {
        // ❌ NGUY HIỂM: Không check authentication hoặc authorization
        // Bất kỳ ai cũng có thể truy cập endpoint này

        System.out.println("⚠️  Returning all users without authentication check!");

        return new ArrayList<>(users.values());
    }

    /**
     * ❌ LỖI: Weak session validation
     */
    @GetMapping("/profile")
    public Map<String, Object> getProfile(@RequestParam String sessionId) {
        // ❌ NGUY HIỂM: Session ID dễ đoán và không expire

        String username = sessions.get(sessionId);

        Map<String, Object> response = new HashMap<>();

        if (username != null) {
            User user = users.get(username);
            response.put("success", true);
            response.put("user", user);
        } else {
            response.put("success", false);
            response.put("message", "Invalid session");
        }

        return response;
    }

    /**
     * ❌ LỖI: Insecure password reset
     */
    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@RequestParam String email) {
        // ❌ NGUY HIỂM: Không verify user identity
        // ❌ NGUY HIỂM: Không send verification link
        // ❌ NGUY HIỂM: Generate predictable temporary password

        Map<String, Object> response = new HashMap<>();

        for (User user : users.values()) {
            if (user.email.equals(email)) {
                String tempPassword = "temp" + System.currentTimeMillis();
                user.password = tempPassword;

                response.put("success", true);
                // ❌ NGUY HIỂM: Return password trong response
                response.put("message", "Password reset! New password: " + tempPassword);
                return response;
            }
        }

        response.put("success", false);
        response.put("message", "Email not found");
        return response;
    }

    /**
     * ❌ LỖI: Weak encryption
     */
    @PostMapping("/encrypt")
    public Map<String, Object> encryptData(@RequestBody Map<String, String> request) {
        String data = request.get("data");

        try {
            // ❌ NGUY HIỂM: Hardcoded key
            // ❌ NGUY HIỂM: ECB mode (weak)
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] encrypted = cipher.doFinal(data.getBytes());
            String encodedData = Base64.getEncoder().encodeToString(encrypted);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("encrypted", encodedData);
            response.put("key", ENCRYPTION_KEY); // ❌ NGUY HIỂM: Expose key

            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    /**
     * ❌ LỖI: Missing authorization check
     */
    @DeleteMapping("/user/{username}")
    public Map<String, Object> deleteUser(@PathVariable String username,
                                           @RequestParam String sessionId) {
        // ❌ NGUY HIỂM: Chỉ check authentication, không check authorization
        // User thường có thể xóa admin account

        String currentUser = sessions.get(sessionId);

        Map<String, Object> response = new HashMap<>();

        if (currentUser == null) {
            response.put("success", false);
            response.put("message", "Not authenticated");
            return response;
        }

        // ❌ NGUY HIỂM: Không check if currentUser has permission to delete
        users.remove(username);

        response.put("success", true);
        response.put("message", "User " + username + " deleted by " + currentUser);

        return response;
    }

    // User class (insecure)
    static class User {
        public String username;
        public String password; // ❌ Should never expose password
        public String email;
        public String role;

        public User(String username, String password, String email, String role) {
            this.username = username;
            this.password = password;
            this.email = email;
            this.role = role;
        }
    }
}
