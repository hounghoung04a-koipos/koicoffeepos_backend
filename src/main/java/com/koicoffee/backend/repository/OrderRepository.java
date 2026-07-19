package com.koicoffee.backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.koicoffee.backend.model.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    // CỨU CÁNH HIỆU NĂNG 1: Phân trang + Chống lỗi N+1
    @EntityGraph(attributePaths = {"orderDetails", "orderDetails.product"})
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    // CỨU CÁNH HIỆU NĂNG 2: Chỉ lấy đơn hàng từ lúc mở ca (Thay cho findAll() cũ)
    @Query("SELECT o FROM Order o WHERE o.status = 'PAID' AND o.paymentTime >= :lastShiftEnd")
    List<Order> findPaidOrdersSince(@Param("lastShiftEnd") LocalDateTime lastShiftEnd);

    @Query("SELECT COALESCE(SUM(o.totalPrice - o.discount), 0) FROM Order o WHERE o.status = 'PAID' AND o.paymentMethod = :method AND o.paymentTime >= :startTime")
    Long calculateRevenueSince(@Param("method") String method, @Param("startTime") LocalDateTime startTime);
}
