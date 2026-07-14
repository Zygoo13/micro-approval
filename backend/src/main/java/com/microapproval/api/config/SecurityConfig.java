package com.microapproval.api.config;

import com.microapproval.api.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http

                // 1. Tắt CSRF (vì chúng ta dùng stateless JWT Token, không dùng Session Cookie)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Cấu hình phân quyền truy cập API
                .authorizeHttpRequests(auth -> auth
                        // Cho phép truy cập tự do vào các API đăng ký/đăng nhập
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Cho phép xem tài liệu API (Swagger/OpenAPI nếu có sau này)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        // Tất cả các request còn lại đều phải xác thực
                        .requestMatchers("/api/v1/webhook/**").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .anyRequest().authenticated()
                )

                // 3. Cấu hình quản lý Session là STATELESS (Không lưu trạng thái phía server)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. Đăng ký AuthenticationProvider và chèn JwtAuthenticationFilter vào trước UsernamePasswordAuthenticationFilter
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Cấu hình CORS để Frontend (React) ở cổng 3000 có thể gọi API mà không bị chặn
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Hub-Signature-256"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}