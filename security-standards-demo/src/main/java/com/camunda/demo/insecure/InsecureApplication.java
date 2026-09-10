package com.camunda.demo.insecure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ⚠️ CẢNH BÁO: ĐÂY LÀ CODE KHÔNG AN TOÀN - CHỈ DÙNG ĐỂ HỌC TẬP ⚠️
 *
 * Application này chứa nhiều lỗi bảo mật phổ biến để minh họa
 * tầm quan trọng của secure coding standards.
 *
 * KHÔNG BAO GIỜ sử dụng code này trong môi trường production!
 */
@SpringBootApplication
public class InsecureApplication {
    public static void main(String[] args) {
        SpringApplication.run(InsecureApplication.class, args);
        System.out.println("⚠️  INSECURE APPLICATION STARTED - FOR EDUCATIONAL PURPOSES ONLY ⚠️");
    }
}
