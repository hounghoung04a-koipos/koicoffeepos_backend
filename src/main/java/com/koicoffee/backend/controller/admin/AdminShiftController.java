package com.koicoffee.backend.controller.admin;

import com.koicoffee.backend.model.Shift;
import com.koicoffee.backend.repository.ShiftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/shifts")
public class AdminShiftController {

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public Map<String, Object> getAllShifts() {
        List<Shift> shifts = shiftRepository.findAllByOrderByIdDesc();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", shifts);
        return response;
    }

    // 🚀 API Sửa: Cập nhật chi tiết doanh thu và Giờ mở ca / kết ca
    @PutMapping("/{id}")
    public Map<String, Object> updateShift(@PathVariable Long id, @RequestBody Shift req) {
        Shift existing = shiftRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ca làm việc!"));

        existing.setStaffName(req.getStaffName());

        // Cập nhật giờ mở ca và kết ca
        if (req.getStartTime() != null) {
            existing.setStartTime(req.getStartTime());
        }
        if (req.getEndTime() != null) {
            existing.setEndTime(req.getEndTime());
        }

        // Cập nhật các dòng tiền
        existing.setInitialCash(req.getInitialCash() != null ? req.getInitialCash() : 0);
        existing.setBatchCashRevenue(req.getBatchCashRevenue() != null ? req.getBatchCashRevenue() : 0);
        existing.setTransferRevenue(req.getTransferRevenue() != null ? req.getTransferRevenue() : 0);

        // Tự động tính lại Tổng doanh thu = Tiền mặt + Chuyển khoản
        existing.setTotalRevenue(existing.getBatchCashRevenue() + existing.getTransferRevenue());

        existing.setActualCash(req.getActualCash() != null ? req.getActualCash() : 0);
        existing.setNote(req.getNote());

        // Tính lại lệch két tự động
        existing.setVariance(existing.getActualCash() - (existing.getInitialCash() + existing.getTotalRevenue()));

        Shift saved = shiftRepository.save(existing);
        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", saved);
        return response;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteShift(@PathVariable Long id) {
        shiftRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy ca làm việc này!"));
        shiftRepository.deleteById(id);

        messagingTemplate.convertAndSend("/topic/public", "DATA_CHANGED");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Đã xóa lịch sử ca làm việc!");
        return response;
    }
}