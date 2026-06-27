package com.koicoffee.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "shifts")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "status")
    private String status = "OPEN";

    @Column(name = "initial_cash")
    private Long initialCash = 0L;

    @Column(name = "batch_cash_revenue")
    private Long batchCashRevenue = 0L;

    @Column(name = "transfer_revenue")
    private Long transferRevenue = 0L;

    @Column(name = "actual_cash")
    private Long actualCash = 0L;

    @Column(name = "variance")
    private Long variance = 0L;

    @Column(name = "total_revenue")
    private Long totalRevenue = 0L;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;
}