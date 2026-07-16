package com.koicoffee.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Tắt CSRF để không chặn request
                .cors(cors -> {
                }) // Để trống, bộ lọc CorsFilter bên dưới sẽ tự động đảm nhiệm
                .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/system/ping").permitAll()
                .anyRequest().permitAll()
                );
        return http.build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE) // 🚀 QUYỀN LỰC CAO NHẤT: Chạy trước mọi thứ
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);
        // Sử dụng setAllowedOriginPatterns để bao quát cả www và không www
        config.setAllowedOriginPatterns(List.of(
                "https://*.vercel.app",
                "https://*.koicoffeepos.id.vn",
                "https://koicoffeepos.id.vn",
                "http://localhost:5173",
                "http://localhost:3000"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Type"));

        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
