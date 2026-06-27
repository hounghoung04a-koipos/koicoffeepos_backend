package com.koicoffee.backend.model;

import jakarta.persistence.*; // Nếu bạn dùng Spring Boot 2.x, đổi chữ 'jakarta' thành 'javax'
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "shift_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "initial_cash")
    private Long initialCash;

    @Column(name = "cash_revenue")
    private Long cashRevenue;

    @Column(name = "transfer_revenue")
    private Long transferRevenue;

    @Column(name = "total_revenue")
    private Long totalRevenue;

    @Column(name = "actual_cash")
    private Long actualCash;

    @Column(name = "variance")
    private Long variance;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "report_time")
    private LocalDateTime reportTime;
}