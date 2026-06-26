package com.koicoffee.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // Điền chính xác các link Frontend trên mạng của bạn
                .allowedOrigins(
                        "https://koi-coffee-frontend.vercel.app",
                        "https://koicoffee.id.vn",
                        "https://www.koicoffee.id.vn"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}