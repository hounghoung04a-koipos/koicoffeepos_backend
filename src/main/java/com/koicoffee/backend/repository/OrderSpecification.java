package com.koicoffee.backend.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.koicoffee.backend.model.Order;

import jakarta.persistence.criteria.Predicate;

public class OrderSpecification {

    public static Specification<Order> filterOrders(LocalDateTime startDate, LocalDateTime endDate, String status, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Lọc theo khoảng thời gian (startDate và endDate truyền từ Controller)
            if (startDate != null && endDate != null) {
                predicates.add(cb.between(root.get("createdAt"), startDate, endDate));
            } else if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            } else if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            // 2. Lọc theo trạng thái
            if (status != null && !status.isEmpty() && !status.equals("ALL")) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 3. Lọc theo từ khóa (Mã đơn hoặc Tên nhân viên)
            if (keyword != null && !keyword.trim().isEmpty()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                Predicate pCode = cb.like(cb.lower(root.get("code")), pattern);
                Predicate pStaff = cb.like(cb.lower(root.get("staffName")), pattern);
                predicates.add(cb.or(pCode, pStaff));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
