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

            // Lọc theo khoảng thời gian chuẩn xác
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            // Lọc theo trạng thái
            if (status != null && !status.isEmpty() && !"ALL".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Lọc theo từ khóa (Mã đơn hàng)
            if (keyword != null && !keyword.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + keyword.toLowerCase() + "%"));
            }

            query.distinct(true);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
