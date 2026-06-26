package com.koicoffee.backend.controller.admin;

import com.koicoffee.backend.model.Category;
import com.koicoffee.backend.model.Product;
import com.koicoffee.backend.repository.CategoryRepository;
import com.koicoffee.backend.repository.ProductRepository;
import com.koicoffee.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public Map<String, Object> getAllProductsForAdmin() {
        List<Product> products = productRepository.findAll();
        products.sort((p1, p2) -> p2.getId().compareTo(p1.getId()));

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", products);
        return response;
    }

    @PostMapping
    public Map<String, Object> createProduct(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Product product = new Product();
            product.setName((String) payload.get("name"));
            product.setPrice(Integer.parseInt(payload.get("price").toString()));
            product.setStatus((String) payload.get("status"));

            Long categoryId = Long.valueOf(payload.get("categoryId").toString());
            Category cat = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục!"));
            product.setCategory(cat);

            Product savedProduct = productRepository.save(product);

            String message = "🆕 Món mới [" + savedProduct.getName() + "] vừa được Admin thêm vào thực đơn!";
            notificationService.createNotification(message, null);

            // 🚀 Bắn thông báo lên Frontend
            messagingTemplate.convertAndSend("/topic/public", "MENU_NOTIFY:" + message);
            messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

            response.put("status", "success");
            response.put("data", savedProduct);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));

            String oldStatus = product.getStatus();

            if (payload.containsKey("name")) product.setName((String) payload.get("name"));
            if (payload.containsKey("price")) product.setPrice(Integer.parseInt(payload.get("price").toString()));
            if (payload.containsKey("status")) product.setStatus((String) payload.get("status"));

            Long categoryId = Long.valueOf(payload.get("categoryId").toString());
            Category cat = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục!"));
            product.setCategory(cat);

            productRepository.save(product);

            String newStatus = product.getStatus();
            String message;

            if (oldStatus != null && newStatus != null && !oldStatus.equalsIgnoreCase(newStatus)) {
                String statusVN = newStatus.equalsIgnoreCase("INACTIVE") ? "HẾT HÀNG" : "ĐANG BÁN";
                message = "🍔 Món [" + product.getName() + "] vừa được Admin chuyển trạng thái thành: " + statusVN;
            } else {
                message = "📝 Món [" + product.getName() + "] vừa được Admin cập nhật thông tin!";
            }

            notificationService.createNotification(message, null);

            // 🚀 Bắn thông báo lên Frontend
            messagingTemplate.convertAndSend("/topic/public", "MENU_NOTIFY:" + message);
            messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

            response.put("status", "success");
        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        return response;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteProduct(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Product product = productRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));
            String productName = product.getName();

            productRepository.deleteById(id);

            String message = "🗑️ Món [" + productName + "] vừa bị Admin xóa khỏi hệ thống!";
            notificationService.createNotification(message, null);

            // 🚀 Bắn thông báo lên Frontend
            messagingTemplate.convertAndSend("/topic/public", "MENU_NOTIFY:" + message);
            messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Không thể xóa vì sản phẩm này đã nằm trong hóa đơn cũ!");
        }
        return response;
    }
}