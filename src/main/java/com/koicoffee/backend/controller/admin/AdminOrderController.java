package com.koicoffee.backend.controller.admin;

import com.koicoffee.backend.model.Order;
import com.koicoffee.backend.repository.OrderRepository;
import com.koicoffee.backend.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import com.koicoffee.backend.repository.OrderSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.time.LocalTime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public Map<String, Object> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        orders.sort((o1, o2) -> o2.getId().compareTo(o1.getId()));

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", orders);
        return response;
    }

    @GetMapping
    public Map<String, Object> getAdminOrders(
            @RequestParam(defaultValue = "TODAY") String filterType, // TODAY, WEEK, MONTH, YEAR, CUSTOM
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC, size = 20) Pageable pageable
    ) {
        LocalDateTime start = null;
        LocalDateTime end = null;

        // Tính toán khoảng thời gian
        switch (filterType.toUpperCase()) {
            case "TODAY":
                start = LocalDateTime.now().with(LocalTime.MIN);
                end = LocalDateTime.now().with(LocalTime.MAX);
                break;
            case "WEEK":
                start = LocalDateTime.now().minusWeeks(1).with(LocalTime.MIN);
                break;
            case "MONTH":
                start = LocalDateTime.now().minusMonths(1).with(LocalTime.MIN);
                break;
            case "YEAR":
                start = LocalDateTime.now().minusYears(1).with(LocalTime.MIN);
                break;
            case "CUSTOM":
                start = startDate;
                end = endDate;
                break;
            case "ALL":
                // Lấy tất cả, start và end giữ nguyên là null
                break;
        }

        Specification<Order> spec = OrderSpecification.filterOrders(start, end, status, keyword);
        Page<Order> page = orderRepository.findAll(spec, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        // Trả về Meta Data cho Frontend Phân trang
        response.put("data", page.getContent());
        response.put("totalPages", page.getTotalPages());
        response.put("totalElements", page.getTotalElements());
        response.put("currentPage", page.getNumber());

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
                    case "PAID": statusVN = "ĐÃ THANH TOÁN"; break;
                    case "CANCELLED": statusVN = "ĐÃ HỦY BỎ"; break;
                    case "PENDING": statusVN = "CHƯA THANH TOÁN"; break;
                    default: statusVN = newStatus;
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