package com.koicoffee.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password; // Đảm bảo đã có trong DB

    @Column(name = "full_name")
    private String fullName;

    @Column(nullable = false)
    private String role; // Chỉ nhận: 'ADMIN' hoặc 'STAFF'

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_active", columnDefinition = "boolean default true")
    private Boolean isActive = true;

    @Column(name = "current_session_id")
    private String currentSessionId;
}