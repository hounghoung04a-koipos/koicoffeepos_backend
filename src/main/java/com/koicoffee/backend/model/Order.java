package com.koicoffee.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private String note;

    @Column(name = "parent_order_id")
    private Long parentOrderId;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "payment_time")
    private LocalDateTime paymentTime;

    @Column(name = "staff_name")
    private String staffName;

    private String status;

    @Column(name = "total_price")
    private Long totalPrice;

    @Column(name = "discount", columnDefinition = "bigint default 0")
    private Long discount = 0L;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("order")
    private List<OrderDetail> orderDetails = new ArrayList<>();
}