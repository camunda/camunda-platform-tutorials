package com.camunda.demo.insecure;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * ❌ LỖI BẢO MẬT #2: XSS (Cross-Site Scripting) & INPUT VALIDATION
 *
 * Controller này chứa các lỗi XSS và thiếu validation.
 *
 * VẤN ĐỀ:
 * - Return user input trực tiếp không encode/sanitize
 * - Không validate input type và format
 * - Path Traversal vulnerability
 * - Deserialization không an toàn
 *
 * TẤN CÔNG:
 * - POST /comment với body: {"comment": "<script>alert('XSS')</script>"}
 * - GET /file?path=../../etc/passwd
 * - POST /upload với file chứa malicious code
 *
 * CHUẨN BẢO MẬT VI PHẠM:
 * - OWASP Top 10 (A03:2021 - Injection / A05:2021 - Security Misconfiguration)
 * - CWE-79: XSS
 * - CWE-22: Path Traversal
 * - CWE-502: Deserialization of Untrusted Data
 */
@RestController
@RequestMapping("/api/insecure")
public class XssController {

    /**
     * ❌ LỖI: Reflected XSS - return user input không escape
     */
    @GetMapping("/hello")
    public String hello(@RequestParam String name) {
        // ❌ NGUY HIỂM: User input được return trực tiếp
        // Attacker có thể inject: ?name=<script>alert('XSS')</script>
        return "<html><body><h1>Hello " + name + "!</h1></body></html>";
    }

    /**
     * ❌ LỖI: Stored XSS - lưu user input không sanitize
     */
    @PostMapping("/comment")
    public Map<String, Object> addComment(@RequestBody Map<String, String> request) {
        String comment = request.get("comment");

        // ❌ NGUY HIỂM: Không validate hoặc sanitize input
        // Trong thực tế, comment này sẽ được lưu vào DB và hiển thị cho users khác

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Comment added: " + comment);
        // XSS payload sẽ execute khi user khác xem comment

        return response;
    }

    /**
     * ❌ LỖI: Path Traversal - đọc file không validate path
     */
    @GetMapping("/file")
    public String readFile(@RequestParam String path) {
        try {
            // ❌ NGUY HIỂM: Attacker có thể đọc bất kỳ file nào trên hệ thống
            // Ví dụ: ?path=../../etc/passwd
            // Hoặc: ?path=../../application.properties (chứa credentials)

            System.out.println("⚠️  Reading file: " + path);

            String content = new String(Files.readAllBytes(Paths.get(path)));
            return content;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * ❌ LỖI: Command Injection - execute command với user input
     */
    @GetMapping("/ping")
    public String ping(@RequestParam String host) {
        try {
            // ❌ NGUY HIỂM: User input được đưa vào command
            // Attacker có thể inject: ?host=google.com; cat /etc/passwd
            // Hoặc: ?host=google.com && rm -rf /

            String command = "ping -c 1 " + host;
            System.out.println("⚠️  Executing command: " + command);

            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            return output.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * ❌ LỖI: Insecure Deserialization
     */
    @PostMapping("/deserialize")
    public Map<String, Object> deserialize(@RequestBody String data) {
        Map<String, Object> response = new HashMap<>();

        try {
            // ❌ NGUY HIỂM: Deserialize untrusted data có thể dẫn đến RCE
            byte[] bytes = java.util.Base64.getDecoder().decode(data);
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
            Object obj = ois.readObject();

            response.put("success", true);
            response.put("object", obj.toString());
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * ❌ LỖI: Missing Input Validation
     */
    @PostMapping("/transfer")
    public Map<String, Object> transferMoney(@RequestBody Map<String, Object> request) {
        // ❌ NGUY HIỂM: Không validate input type và range
        // Attacker có thể gửi số âm, số rất lớn, hoặc non-numeric values

        Object amount = request.get("amount");
        String toAccount = (String) request.get("toAccount");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Transferred " + amount + " to " + toAccount);

        // Không check:
        // - amount > 0
        // - amount <= user balance
        // - toAccount format valid
        // - Transaction limits

        return response;
    }

    /**
     * ❌ LỖI: HTML Injection
     */
    @GetMapping("/profile")
    public String viewProfile(@RequestParam String bio) {
        // ❌ NGUY HIỂM: HTML injection có thể thay đổi page layout
        return "<html><body>" +
                "<h1>User Profile</h1>" +
                "<div class='bio'>" + bio + "</div>" +
                "</body></html>";
    }
}
