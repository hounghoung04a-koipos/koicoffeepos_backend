package com.koicoffee.backend.controller.admin;

import com.koicoffee.backend.model.Category;
import com.koicoffee.backend.repository.CategoryRepository;
import com.koicoffee.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public Map<String, Object> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", categories);
        return response;
    }

    @PostMapping
    public Map<String, Object> createCategory(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Category category = new Category();
            category.setName((String) payload.get("name"));
            Category savedCategory = categoryRepository.save(category);

            // 🚀 Lưu thông báo vào DB
            String message = "🆕 Danh mục mới [" + savedCategory.getName() + "] vừa được Admin thêm vào!";
            notificationService.createNotification(message, null);

            // 🚀 Gửi tín hiệu báo Frontend hiển thị Popup
            messagingTemplate.convertAndSend("/topic/public", "MENU_NOTIFY:" + message);
            messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

            response.put("status", "success");
            response.put("data", savedCategory);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục!"));

            category.setName((String) payload.get("name"));
            categoryRepository.save(category);

            // 🚀 Lưu thông báo vào DB
            String message = "📝 Danh mục [" + category.getName() + "] vừa được Admin cập nhật!";
            notificationService.createNotification(message, null);

            // 🚀 Gửi tín hiệu báo Frontend hiển thị Popup
            messagingTemplate.convertAndSend("/topic/public", "MENU_NOTIFY:" + message);
            messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteCategory(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Category category = categoryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục!"));
            String catName = category.getName();

            categoryRepository.deleteById(id);

            // 🚀 Lưu thông báo vào DB
            String message = "🗑️ Danh mục [" + catName + "] vừa bị Admin xóa!";
            notificationService.createNotification(message, null);

            // 🚀 Gửi tín hiệu báo Frontend hiển thị Popup
            messagingTemplate.convertAndSend("/topic/public", "MENU_NOTIFY:" + message);
            messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Không thể xóa vì danh mục này đang chứa món ăn! Hãy xóa hoặc chuyển danh mục cho các món ăn đó trước.");
        }
        return response;
    }
}