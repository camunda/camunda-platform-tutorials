package com.camunda.demo.insecure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ❌ LỖI BẢO MẬT #1: SQL INJECTION
 *
 * Controller này chứa các lỗi SQL Injection nghiêm trọng.
 *
 * VẤN ĐỀ:
 * - Concatenate trực tiếp user input vào SQL query
 * - Không sử dụng Prepared Statements
 * - Không validate/sanitize input
 *
 * TẤN CÔNG:
 * - GET /search?name=admin' OR '1'='1
 * - GET /search?name='; DROP TABLE users; --
 * - GET /login?username=admin'--&password=anything
 *
 * CHUẨN BẢO MẬT VI PHẠM:
 * - OWASP Top 10 (A03:2021 - Injection)
 * - CWE-89: SQL Injection
 * - SANS Top 25
 */
@RestController
@RequestMapping("/api/insecure")
public class SqlInjectionController {

    @Autowired
    private DataSource dataSource;

    /**
     * ❌ LỖI: SQL Injection thông qua search parameter
     */
    @GetMapping("/search")
    public List<Map<String, Object>> searchUsers(@RequestParam String name) {
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // ❌ NGUY HIỂM: Concatenate trực tiếp user input vào SQL
            String sql = "SELECT * FROM users WHERE name LIKE '%" + name + "%'";

            System.out.println("⚠️  Executing vulnerable SQL: " + sql);

            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getInt("id"));
                user.put("name", rs.getString("name"));
                user.put("email", rs.getString("email"));
                results.add(user);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    /**
     * ❌ LỖI: SQL Injection trong login
     */
    @GetMapping("/login")
    public Map<String, Object> login(@RequestParam String username,
                                      @RequestParam String password) {
        Map<String, Object> response = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // ❌ NGUY HIỂM: Attacker có thể bypass authentication
            // Ví dụ: username=admin'-- sẽ comment out phần password check
            String sql = "SELECT * FROM users WHERE username='" + username +
                    "' AND password='" + password + "'";

            System.out.println("⚠️  Executing vulnerable SQL: " + sql);

            ResultSet rs = stmt.executeQuery(sql);

            if (rs.next()) {
                response.put("success", true);
                response.put("message", "Login successful");
                response.put("userId", rs.getInt("id"));
                response.put("username", rs.getString("username"));
            } else {
                response.put("success", false);
                response.put("message", "Invalid credentials");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    /**
     * ❌ LỖI: SQL Injection cho phép xóa dữ liệu
     */
    @DeleteMapping("/delete")
    public Map<String, Object> deleteUser(@RequestParam String userId) {
        Map<String, Object> response = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // ❌ NGUY HIỂM: Attacker có thể xóa toàn bộ database
            // Ví dụ: userId=1 OR 1=1
            String sql = "DELETE FROM users WHERE id=" + userId;

            System.out.println("⚠️  Executing vulnerable SQL: " + sql);

            int deleted = stmt.executeUpdate(sql);

            response.put("success", true);
            response.put("deleted", deleted);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }
}
