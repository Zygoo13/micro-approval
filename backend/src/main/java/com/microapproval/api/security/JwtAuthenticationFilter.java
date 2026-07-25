package com.microapproval.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import io.jsonwebtoken.JwtException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 1. Kiểm tra xem request có chứa Authorization Header dạng Bearer hay không
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Trích xuất Token JWT từ chuỗi "Bearer <token>"
        jwt = authHeader.substring(7);
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (JwtException | IllegalArgumentException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT không hợp lệ hoặc đã hết hạn");
            return;
        }

        // 3. Nếu tìm thấy email trong token và request này chưa được xác thực trong Session hiện tại
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

            // 4. Nếu token hợp lệ, tiến hành xác thực người dùng
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Đặt thông tin xác thực vào Security Context
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 5. Chuyển request đi tiếp đến Filter tiếp theo trong chuỗi FilterChain
        filterChain.doFilter(request, response);
    }
}
