package com.koicoffee.backend.controller;

import com.koicoffee.backend.model.User;
import com.koicoffee.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

        try {
            String username = credentials.get("username");
            String password = credentials.get("password");

            // Lấy toàn bộ danh sách để kiểm tra
            List<User> allUsers = userRepository.findAll();
            System.out.println("DEBUG: Số lượng user trong DB: " + allUsers.size());
            for (User u : allUsers) {
                System.out.println("DEBUG: User trong DB là: '" + u.getUsername() + "'");
            }

            // Lọc lại
            Optional<User> userOpt = allUsers.stream()
                    .filter(u -> u.getUsername().trim().equals(username.trim()))
                    .findFirst();

            if (userOpt.isEmpty()) {
                System.out.println("❌ LỖI: Không tìm thấy username '" + username + "' trong DB.");
                response.put("status", "error");
                response.put("message", "Sai tên đăng nhập hoặc mật khẩu!");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            User foundUser = userOpt.get();

            if (foundUser.getIsActive() != null && !foundUser.getIsActive()) {
                response.put("status", "error");
                response.put("message", "Tài khoản của bạn đã bị vô hiệu hóa bởi Quản trị viên!");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            if (!passwordEncoder.matches(password, foundUser.getPassword())) {
                response.put("status", "error");
                response.put("message", "Sai tên đăng nhập hoặc mật khẩu!");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

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

            return ResponseEntity.ok(response);

        } catch (Exception e) {
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

                // Kiểm tra xem sessionId gửi lên có khớp với sessionId đang lưu trong DB không
                if (sessionId != null && sessionId.equals(user.getCurrentSessionId())) {
                    response.put("status", "success");
                    response.put("message", "Session hợp lệ");
                    return ResponseEntity.ok(response);
                }
            }

            // Nếu không khớp hoặc user không tồn tại -> Trả về lỗi 401 Unauthorized
            response.put("status", "error");
            response.put("message", "Session đã hết hạn hoặc được đăng nhập ở nơi khác.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Lỗi hệ thống");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    
 
  }
}