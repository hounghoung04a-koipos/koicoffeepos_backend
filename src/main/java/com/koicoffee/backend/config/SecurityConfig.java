package com.koicoffee.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 🚀 QUAN TRỌNG: Bật tính năng CORS của Spring Security lên
                .cors(Customizer.withDefaults())

                // Tắt bảo vệ CSRF để Frontend (React) có thể gọi các API POST, PUT, DELETE bình thường
                .csrf(csrf -> csrf.disable())

                // Cho phép tất cả các API đi qua mà không cần báo lỗi 401 Unauthorized
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}