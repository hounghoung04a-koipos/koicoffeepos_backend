package com.koicoffee.backend.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.koicoffee.backend.model.User;
import com.koicoffee.backend.repository.UserRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        Map<String, Object> response = new HashMap<>();

        System.out.println("=== [DEBUG LOG] BẮT ĐẦU QUÁ TRÌNH LOGIN ===");
        System.out.println("1. Dữ liệu thô từ Frontend gửi lên: " + credentials);

        try {
            String username = credentials.get("username");
            String password = credentials.get("password");

            System.out.println("2. Username nhận được: [" + username + "]");
            System.out.println("3. Password nhận được: [" + password + "]");

            Optional<User> userOpt = userRepository.findAll().stream()
                    .filter(u -> u.getUsername().equals(username))
                    .findFirst();

            if (userOpt.isEmpty()) {
                System.out.println("❌ LỖI: Không tìm thấy username [" + username + "] trong Database!");
                response.put("status", "error");
                response.put("message", "Sai tên đăng nhập hoặc mật khẩu!");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            User foundUser = userOpt.get();
            System.out.println("✅ 4. Đã tìm thấy user trong DB. Username: " + foundUser.getUsername());
            System.out.println("👉 Chuỗi Password lưu trong DB: [" + foundUser.getPassword() + "]");

            if (foundUser.getIsActive() != null && !foundUser.getIsActive()) {
                System.out.println("❌ LỖI: Tài khoản đã bị vô hiệu hóa.");
                response.put("status", "error");
                response.put("message", "Tài khoản của bạn đã bị vô hiệu hóa bởi Quản trị viên!");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            boolean isPasswordMatch = passwordEncoder.matches(password, foundUser.getPassword());
            System.out.println("👉 Kết quả so sánh BCrypt (matches): " + isPasswordMatch);

            if (!isPasswordMatch) {
                System.out.println("❌ LỖI: Mật khẩu không khớp với mã băm trong DB!");
                response.put("status", "error");
                response.put("message", "Sai tên đăng nhập hoặc mật khẩu!");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            System.out.println("✅ 5. Đăng nhập thành công! Đang tạo Session...");

            // Sinh mã Session ngẫu nhiên và lưu trực tiếp vào Database
            String sessionId = UUID.randomUUID().toString();
            foundUser.setCurrentSessionId(sessionId);
            userRepository.save(foundUser);

            Map<String, Object> userData = new HashMap<>();
            userData.put("id", foundUser.getId());
            userData.put("username", foundUser.getUsername());
            userData.put("fullName", foundUser.getFullName());
            userData.put("role", foundUser.getRole());
            userData.put("sessionId", sessionId);

            response.put("status", "success");
            response.put("data", userData);

            // Gửi lệnh KICKOUT máy cũ
            messagingTemplate.convertAndSend("/topic/kickout/" + foundUser.getId(), sessionId);

            System.out.println("=== [DEBUG LOG] KẾT THÚC LOGIN THÀNH CÔNG ===");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ LỖI HỆ THỐNG (Exception): " + e.getMessage());
            e.printStackTrace();

            response.put("status", "error");
            response.put("message", "Lỗi hệ thống không xác định!");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/verify-session")
    public ResponseEntity<Map<String, Object>> verifySession(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long userId = Long.valueOf(payload.get("userId").toString());
            String sessionId = (String) payload.get("sessionId");

            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (sessionId != null && sessionId.equals(user.getCurrentSessionId())) {
                    response.put("status", "success");
                    response.put("message", "Session hợp lệ");
                    return ResponseEntity.ok(response);
                }
            }

            response.put("status", "error");
            response.put("message", "Session đã hết hạn hoặc được đăng nhập ở nơi khác.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);

        } catch (Exception e) {
            response.put("status", "error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
