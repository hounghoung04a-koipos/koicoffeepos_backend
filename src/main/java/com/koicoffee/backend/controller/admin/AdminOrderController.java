package com.koicoffee.backend.controller.admin;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koicoffee.backend.model.Order;
import com.koicoffee.backend.repository.OrderRepository;
import com.koicoffee.backend.repository.OrderSpecification;
import com.koicoffee.backend.service.NotificationService;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public Map<String, Object> getAdminOrdersForDashboard(
            @RequestParam(required = false, defaultValue = "ALL") String filterType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Specification<Order> spec = OrderSpecification.filterOrders(
                (filterType.equals("CUSTOM") && startDate != null) ? startDate : null,
                (filterType.equals("CUSTOM") && endDate != null) ? endDate : null,
                status,
                keyword
        );

        Page<Order> page = orderRepository.findAll(spec, pageable);

        // ====================================================================
        // KHỐI TÍNH TOÁN THỐNG KÊ (METRICS) ĐÃ ĐƯỢC FIX LỖI
        // ====================================================================
        List<Order> allFilteredOrders = orderRepository.findAll(spec);

        long totalRevenue = 0, cashRevenue = 0, transferRevenue = 0, pendingRevenue = 0;
        int completedOrders = 0, cancelledCount = 0;

        for (Order o : allFilteredOrders) {
            // 1. Chống NullPointerException cho giá tiền
            long price = (o.getTotalPrice() != null) ? o.getTotalPrice() : 0;
            long discount = (o.getDiscount() != null) ? o.getDiscount() : 0;
            long finalPrice = Math.max(0, price - discount);

            // 2. Chống lỗi chữ hoa/chữ thường (Trim khoảng trắng dư thừa)
            String currentStatus = (o.getStatus() != null) ? o.getStatus().trim().toUpperCase() : "";
            String paymentMethod = (o.getPaymentMethod() != null) ? o.getPaymentMethod().trim().toUpperCase() : "";

            // 3. Phân loại theo trạng thái (Tính cả trường hợp DB lưu tiếng Việt nếu có)
            if (currentStatus.equals("PAID") || currentStatus.equals("COMPLETED") || currentStatus.equals("ĐÃ THANH TOÁN")) {
                completedOrders++;
                totalRevenue += finalPrice;

                if (paymentMethod.equals("CASH") || paymentMethod.equals("TIỀN MẶT")) {
                    cashRevenue += finalPrice;
                } else if (paymentMethod.equals("TRANSFER") || paymentMethod.equals("CHUYỂN KHOẢN")) {
                    transferRevenue += finalPrice;
                }
            } else if (currentStatus.equals("PENDING") || currentStatus.equals("CHỜ THANH TOÁN")) {
                pendingRevenue += finalPrice;
            } else if (currentStatus.equals("CANCELLED") || currentStatus.equals("HỦY BỎ")) {
                cancelledCount++;
            }
        }

        // 4. Bắt buộc phải đóng gói vào Map "metrics"
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalRevenue", totalRevenue);
        metrics.put("cashRevenue", cashRevenue);
        metrics.put("transferRevenue", transferRevenue);
        metrics.put("pendingRevenue", pendingRevenue);
        metrics.put("totalOrders", allFilteredOrders.size()); // Tổng số đơn các loại
        metrics.put("completedOrders", completedOrders);
        metrics.put("cancelledCount", cancelledCount);

        // 5. Trả về đúng format cho Frontend
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", page.getContent());
        response.put("totalPages", page.getTotalPages());
        response.put("totalElements", page.getTotalElements());
        response.put("metrics", metrics); // <--- LỖI 0Đ LÀ DO THIẾU DÒNG NÀY TRƯỚC ĐÓ

        return response;
    }

    @PutMapping("/{id}")
    public Map<String, Object> updateOrder(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn"));

            String oldStatus = order.getStatus();

            if (payload.containsKey("status")) {
                order.setStatus((String) payload.get("status"));
            }
            if (payload.containsKey("note")) {
                order.setNote((String) payload.get("note"));
            }

            orderRepository.save(order);

            String newStatus = order.getStatus();
            if (oldStatus != null && newStatus != null && !oldStatus.equalsIgnoreCase(newStatus)) {
                String statusVN;
                switch (newStatus.toUpperCase()) {
                    case "PAID":
                        statusVN = "ĐÃ THANH TOÁN";
                        break;
                    case "CANCELLED":
                        statusVN = "ĐÃ HỦY BỎ";
                        break;
                    case "PENDING":
                        statusVN = "CHƯA THANH TOÁN";
                        break;
                    default:
                        statusVN = newStatus;
                }

                String message = "🧾 Hóa đơn [" + order.getCode() + "] vừa được Admin chuyển trạng thái thành: " + statusVN;
                notificationService.createNotification(message, null);
            }

            // 🚀 Bắn WebSocket
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
    public Map<String, Object> deleteOrder(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Lấy thông tin hóa đơn trước khi xóa
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn"));
            String orderCode = order.getCode();

            // Thực hiện xóa
            orderRepository.deleteById(id);

            // 🚀 Bổ sung thêm thông báo khi XÓA hóa đơn (Phần mà lúc trước bị thiếu)
            String message = "🗑️ Hóa đơn [" + orderCode + "] vừa bị Admin xóa khỏi hệ thống!";
            notificationService.createNotification(message, null);

            // 🚀 Bắn WebSocket
            messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Không thể xóa hóa đơn này do ràng buộc dữ liệu!");
        }
        return response;
    }
}
