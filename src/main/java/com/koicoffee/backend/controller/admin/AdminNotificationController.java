package com.koicoffee.backend.controller.admin;

import com.koicoffee.backend.model.Notification;
import com.koicoffee.backend.repository.NotificationRepository;
import com.koicoffee.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/send")
    public Map<String, Object> sendCustomNotification(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String content = payload.get("content");
            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException("Nội dung thông báo không được để trống!");
            }

            String message = "📢 [TB Quản Lý]: " + content.trim();
            notificationService.createNotification(message, null);
            messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

            response.put("status", "success");
            response.put("message", "Đã phát thông báo thành công!");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @GetMapping
    public Map<String, Object> getAllNotifications() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Notification> all = notificationRepository.findAll();

            // Chỉ hiển thị các thông báo của chính Admin (user_id = 1) lên bảng
            List<Notification> adminNotifications = all.stream()
                    .filter(n -> n.getUserId() != null && n.getUserId().equals(1L))
                    .sorted((n1, n2) -> n2.getCreatedAt().compareTo(n1.getCreatedAt()))
                    .collect(Collectors.toList());

            response.put("status", "success");
            response.put("data", adminNotifications);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    // 🚀 CHỈNH SỬA ĐÚNG 1 ĐỢT THÔNG BÁO (Lọc theo ID và Thời gian)
    @PutMapping("/{id}")
    public Map<String, Object> editExactNotification(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String newContent = payload.get("newContent");
            if (newContent == null || newContent.trim().isEmpty()) {
                throw new RuntimeException("Nội dung không được để trống!");
            }

            Notification target = notificationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo"));

            String oldContent = target.getContent();
            LocalDateTime time = target.getCreatedAt();

            // Lọc ra các thông báo sinh ra cùng đợt (sai số thời gian <= 2 giây)
            List<Notification> toUpdate = notificationRepository.findAll().stream()
                    .filter(n -> oldContent.equals(n.getContent()))
                    .filter(n -> {
                        if (n.getCreatedAt() == null || time == null) return false;
                        long diffMillis = Duration.between(n.getCreatedAt(), time).abs().toMillis();
                        return diffMillis <= 2000;
                    })
                    .collect(Collectors.toList());

            for (Notification n : toUpdate) {
                n.setContent(newContent);
            }
            notificationRepository.saveAll(toUpdate);

            messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    // 🚀 THU HỒI ĐÚNG 1 ĐỢT THÔNG BÁO (Lọc theo ID và Thời gian)
    @DeleteMapping("/{id}")
    public Map<String, Object> recallExactNotification(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Notification target = notificationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thông báo"));

            String content = target.getContent();
            LocalDateTime time = target.getCreatedAt();

            // Chỉ xóa các thông báo sinh ra cùng 1 đợt với ID được bấm (sai số <= 2 giây)
            List<Notification> toDelete = notificationRepository.findAll().stream()
                    .filter(n -> content.equals(n.getContent()))
                    .filter(n -> {
                        if (n.getCreatedAt() == null || time == null) return false;
                        long diffMillis = Duration.between(n.getCreatedAt(), time).abs().toMillis();
                        return diffMillis <= 900;
                    })
                    .collect(Collectors.toList());

            notificationRepository.deleteAll(toDelete);

            messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Lỗi khi thu hồi!");
        }
        return response;
    }
}