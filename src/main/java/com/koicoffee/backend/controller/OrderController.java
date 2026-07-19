package com.koicoffee.backend.controller;

import com.koicoffee.backend.model.CashRegister;
import com.koicoffee.backend.model.Order;
import com.koicoffee.backend.model.OrderDetail;
import com.koicoffee.backend.model.Product;
import com.koicoffee.backend.repository.CashRegisterRepository;
import com.koicoffee.backend.repository.OrderDetailRepository;
import com.koicoffee.backend.repository.OrderRepository;
import com.koicoffee.backend.repository.ProductRepository;
import com.koicoffee.backend.model.Shift;
import com.koicoffee.backend.repository.ShiftRepository;
import com.koicoffee.backend.model.Shift;
import com.koicoffee.backend.repository.ShiftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.koicoffee.backend.repository.OrderSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalTime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private ShiftRepository shiftRepository;
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CashRegisterRepository cashRegisterRepository;

    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private int parseIntSafe(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty() || "null".equals(String.valueOf(value))) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLongSafe(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty() || "null".equals(String.valueOf(value))) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

        @GetMapping
    public Map<String, Object> getOrdersForPOS(
            @RequestParam(defaultValue = "0") int days, // 0 = HÃ´m nay, 3 = 3 ngÃ y qua
            @RequestParam(defaultValue = "CURRENT") String shift, // CURRENT hoáº·c PREVIOUS
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC, size = 12) Pageable pageable
    ) {
        LocalDateTime lastShiftEnd = cashRegisterRepository.findById(1L)
                .map(CashRegister::getLastUpdated)
                .orElse(LocalDateTime.MIN);

        Specification<Order> spec;
        if ("CURRENT".equalsIgnoreCase(shift)) {
            // Ca hiá»‡n táº¡i: tá»« lastShiftEnd Ä‘áº¿n nay
            spec = OrderSpecification.filterOrders(lastShiftEnd, null, null, null);
        } else {
            // Ca trÆ°á»›c: tá»« startDate Ä‘áº¿n lastShiftEnd
            LocalDateTime startDate = LocalDateTime.now().minusDays(days).with(LocalTime.MIN);
            spec = OrderSpecification.filterOrders(startDate, lastShiftEnd, null, null);
        }

        Page<Order> page = orderRepository.findAll(spec, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", page.getContent());
        response.put("totalPages", page.getTotalPages());
        response.put("totalElements", page.getTotalElements());
        response.put("currentPage", page.getNumber());
        response.put("lastShiftEnd", lastShiftEnd);
        return response;
    }

    @GetMapping("/current-shift")
    public Map<String, Object> getOrdersForCurrentShift(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC, size = 12) Pageable pageable
    ) {
        LocalDateTime shiftStart = shiftRepository.findFirstByStatusOrderByStartTimeDesc("OPEN")
                .map(Shift::getStartTime)
                .orElseGet(() -> cashRegisterRepository.findById(1L)
                .map(CashRegister::getLastUpdated)
                .orElse(LocalDateTime.MIN));

        Specification<Order> spec = OrderSpecification.filterOrders(shiftStart, null, status, keyword);
        Page<Order> page = orderRepository.findAll(spec, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", page.getContent());
        response.put("totalPages", page.getTotalPages());
        response.put("totalElements", page.getTotalElements());
        response.put("currentPage", page.getNumber());
        response.put("shiftStart", shiftStart);

        return response;
    }

    // SỬA LỖI TỬ HUYỆT: Không dùng orderRepository.findAll() nữa
    @GetMapping("/shift-summary")
    public Map<String, Object> getShiftSummary() {
        LocalDateTime lastShiftEnd = cashRegisterRepository.findById(1L)
                .map(CashRegister::getLastUpdated)
                .orElse(LocalDateTime.MIN);

        // Chỉ lấy những đơn hàng ĐÃ THANH TOÁN sau thời gian chốt ca
        List<Order> shiftOrders = orderRepository.findPaidOrdersSince(lastShiftEnd);

        long totalCash = 0;
        long totalTransfer = 0;

        for (Order o : shiftOrders) {
            long finalPrice = Math.max(0, o.getTotalPrice() - (o.getDiscount() != null ? o.getDiscount() : 0));
            if ("CASH".equalsIgnoreCase(o.getPaymentMethod())) {
                totalCash += finalPrice;
            } else if ("TRANSFER".equalsIgnoreCase(o.getPaymentMethod())) {
                totalTransfer += finalPrice;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("lastShiftEnd", lastShiftEnd);
        response.put("totalCash", totalCash);
        response.put("totalTransfer", totalTransfer);
        response.put("totalRevenue", totalCash + totalTransfer);
        response.put("totalOrders", shiftOrders.size());

        return response;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> payload) {
        Order order = new Order();
        String timeCode = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMHHmmss"));
        order.setCode("HD" + timeCode);
        order.setStaffName((String) payload.get("staffName"));

        order.setTotalPrice(parseLongSafe(payload.get("totalPrice")));
        order.setDiscount(parseLongSafe(payload.get("discount")));

        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("PENDING");

        order = orderRepository.save(order);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        for (Map<String, Object> itemData : items) {
            Long productId = Long.valueOf(itemData.get("id").toString());
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại!"));

            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setProduct(product);
            detail.setQuantity(parseIntSafe(itemData.get("quantity")));
            detail.setPrice(parseIntSafe(itemData.get("price")));
            if (itemData.get("note") != null) {
                detail.setNote(String.valueOf(itemData.get("note")));
            }
            orderDetailRepository.save(detail);
            order.getOrderDetails().add(detail);
        }

        Order savedOrder = orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", savedOrder);
        return response;
    }

    @PutMapping("/{id}/pay")
    @Transactional
    public Map<String, Object> payOrder(@PathVariable Long id, @RequestParam String method) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        order.setStatus("PAID");
        order.setPaymentMethod(method);
        order.setPaymentTime(LocalDateTime.now());
        orderRepository.save(order);

        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> updateOrder(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        orderDetailRepository.deleteAll(order.getOrderDetails());
        order.getOrderDetails().clear();

        order.setTotalPrice(parseLongSafe(payload.get("totalPrice")));
        order.setDiscount(parseLongSafe(payload.get("discount")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        for (Map<String, Object> itemData : items) {
            Long productId = Long.valueOf(itemData.get("id").toString());
            Product product = productRepository.findById(productId).orElseThrow();

            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setProduct(product);
            detail.setQuantity(parseIntSafe(itemData.get("quantity")));
            detail.setPrice(parseIntSafe(itemData.get("price")));
            detail.setNote(itemData.get("note") != null ? String.valueOf(itemData.get("note")) : null);

            orderDetailRepository.save(detail);
            order.getOrderDetails().add(detail);
        }

        orderRepository.save(order);
        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }

    // --- CÁC ENDPOINT THAO TÁC NHANH ---
    @PutMapping("/{id}/status")
    @Transactional
    public Map<String, Object> changeOrderStatus(@PathVariable Long id, @RequestParam String status) {
        Order order = orderRepository.findById(id).orElseThrow();
        order.setStatus(status);
        orderRepository.save(order);

        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }

    @PutMapping("/{id}/payment-method")
    @Transactional
    public Map<String, Object> changePaymentMethod(@PathVariable Long id, @RequestParam String method) {
        Order order = orderRepository.findById(id).orElseThrow();
        order.setPaymentMethod(method);
        orderRepository.save(order);

        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }

    @PutMapping("/{id}/cancel")
    @Transactional
    public Map<String, Object> cancelOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id).orElseThrow();
        order.setStatus("CANCELLED");
        orderRepository.save(order);

        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }

    // API MỚI: KHÔI PHỤC ĐƠN HÀNG SAU KHI HỦY
    @PutMapping("/{id}/restore")
    @Transactional
    public Map<String, Object> restoreOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id).orElseThrow();
        order.setStatus("PENDING");
        order.setPaymentMethod(null);
        order.setPaymentTime(null);
        orderRepository.save(order);

        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> deleteOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        orderRepository.delete(order);
        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Đã xóa đơn hàng thành công");
        return response;
    }

    @PostMapping("/{id}/split")
    @Transactional
    public Map<String, Object> splitOrder(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Order parentOrder = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng gốc"));

        String method = (String) payload.get("paymentMethod");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> splitItems = (List<Map<String, Object>>) payload.get("items");

        int totalParentQty = parentOrder.getOrderDetails().stream().mapToInt(OrderDetail::getQuantity).sum();
        int totalSplitQty = splitItems.stream().mapToInt(item -> parseIntSafe(item.get("quantity"))).sum();

        if (totalParentQty <= 1) {
            throw new RuntimeException("Đơn hàng chỉ có 1 sản phẩm, không thể tách!");
        }
        if (totalSplitQty >= totalParentQty) {
            throw new RuntimeException("Không thể tách toàn bộ đơn hàng!");
        }

        long splitCount = orderRepository.findAll().stream()
                .filter(o -> parentOrder.getId().equals(o.getParentOrderId()))
                .count();

        Order splitOrder = new Order();
        splitOrder.setCode(parentOrder.getCode() + "-T" + (splitCount + 1));
        splitOrder.setStaffName(parentOrder.getStaffName());
        splitOrder.setCreatedAt(LocalDateTime.now());
        splitOrder.setStatus("PAID");
        splitOrder.setPaymentMethod(method);
        splitOrder.setPaymentTime(LocalDateTime.now());
        splitOrder.setParentOrderId(parentOrder.getId());
        splitOrder.setNote("Tách từ hóa đơn " + parentOrder.getCode());
        splitOrder.setTotalPrice(0L);
        splitOrder.setDiscount(0L);

        splitOrder = orderRepository.save(splitOrder);

        long splitTotalPrice = 0;

        for (Map<String, Object> itemReq : splitItems) {
            Long productId = Long.valueOf(itemReq.get("productId").toString());
            int splitQty = parseIntSafe(itemReq.get("quantity"));
            int price = parseIntSafe(itemReq.get("price"));
            String reqNote = itemReq.get("note") != null ? String.valueOf(itemReq.get("note")) : null;

            OrderDetail parentDetail = parentOrder.getOrderDetails().stream()
                    .filter(od -> od.getProduct().getId().equals(productId))
                    .filter(od -> {
                        String odNote = od.getNote();
                        if (reqNote == null || reqNote.trim().isEmpty() || "null".equals(reqNote)) {
                            return odNote == null || odNote.trim().isEmpty();
                        }
                        return reqNote.equals(odNote);
                    })
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Món ăn không tồn tại trong hóa đơn gốc"));

            OrderDetail splitDetail = new OrderDetail();
            splitDetail.setOrder(splitOrder);
            splitDetail.setProduct(parentDetail.getProduct());
            splitDetail.setQuantity(splitQty);
            splitDetail.setPrice(price);
            splitDetail.setNote(parentDetail.getNote());

            orderDetailRepository.save(splitDetail);
            splitOrder.getOrderDetails().add(splitDetail);

            splitTotalPrice += (splitQty * price);

            int remainingQty = parentDetail.getQuantity() - splitQty;
            if (remainingQty <= 0) {
                orderDetailRepository.delete(parentDetail);
                parentOrder.getOrderDetails().remove(parentDetail);
            } else {
                parentDetail.setQuantity(remainingQty);
                orderDetailRepository.save(parentDetail);
            }
        }

        splitOrder.setTotalPrice(splitTotalPrice);
        orderRepository.save(splitOrder);

        long newParentTotal = parentOrder.getOrderDetails().stream()
                .mapToInt(od -> od.getQuantity() * od.getPrice())
                .sum();

        parentOrder.setTotalPrice(newParentTotal);

        if (parentOrder.getDiscount() != null && parentOrder.getDiscount() > newParentTotal) {
            parentOrder.setDiscount(newParentTotal);
        }

        orderRepository.save(parentOrder);
        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }

    @RequestMapping(value = "/close-shift", method = {RequestMethod.PUT, RequestMethod.POST})
    @Transactional
    public Map<String, Object> closeShift(@RequestBody(required = false) Map<String, Object> payload) {
        CashRegister cashRegister = cashRegisterRepository.findById(1L)
                .orElse(new CashRegister());

        cashRegister.setId(1L);
        cashRegister.setLastUpdated(LocalDateTime.now());

        cashRegisterRepository.save(cashRegister);
        messagingTemplate.convertAndSend("/topic/public", "SHIFT_CLOSED");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Đã chốt ca thành công!");
        return response;
    }
}

