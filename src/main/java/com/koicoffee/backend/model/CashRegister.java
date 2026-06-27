package com.koicoffee.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_registers")
@Data
public class CashRegister {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long balance = 0L;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}