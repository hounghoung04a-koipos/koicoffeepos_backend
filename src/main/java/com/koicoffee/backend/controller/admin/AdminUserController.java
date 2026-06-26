package com.koicoffee.backend.controller;

import com.koicoffee.backend.model.User;
import com.koicoffee.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping
    public Map<String, Object> getAllUsers() {
        List<User> users = userRepository.findAll();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", users);
        return response;
    }

    @PostMapping
    public Map<String, Object> createUser(@RequestBody User userReq) {
        if (userRepository.existsByUsername(userReq.getUsername())) {
            throw new RuntimeException("Tên đăng nhập đã tồn tại!");
        }

        userReq.setCreatedAt(LocalDateTime.now());
        if (userReq.getIsActive() == null) userReq.setIsActive(true);

        userReq.setPassword(passwordEncoder.encode("12345")); // Mặc định 12345
        User savedUser = userRepository.save(userReq);

        messagingTemplate.convertAndSend("/topic/public", "USER_LIST_CHANGED");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", savedUser);
        return response;
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody User userReq) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản!"));

        // Cấm cập nhật Username và Cấm cập nhật Password ở API này
        existingUser.setFullName(userReq.getFullName());
        existingUser.setRole(userReq.getRole());
        existingUser.setIsActive(userReq.getIsActive());

        User updatedUser = userRepository.save(existingUser);

        if (!updatedUser.getIsActive()) {
            messagingTemplate.convertAndSend("/topic/public", "USER_LOCKED:" + updatedUser.getUsername());
        }
        messagingTemplate.convertAndSend("/topic/public", "USER_LIST_CHANGED");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", updatedUser);
        return response;
    }

    // 🚀 API MỚI: CHỈ LÀM NHIỆM VỤ RESET MẬT KHẨU VỀ 12345
    @PutMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable Long id) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản!"));

        existingUser.setPassword(passwordEncoder.encode("12345"));
        userRepository.save(existingUser);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Đã reset mật khẩu về 12345");
        return response;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        User existingUser = userRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản!"));
        String deletedUsername = existingUser.getUsername();
        userRepository.delete(existingUser);
        messagingTemplate.convertAndSend("/topic/public", "USER_LOCKED:" + deletedUsername);
        messagingTemplate.convertAndSend("/topic/public", "USER_LIST_CHANGED");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }
}