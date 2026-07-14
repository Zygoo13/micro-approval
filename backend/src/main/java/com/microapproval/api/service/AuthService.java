package com.microapproval.api.service;


import com.microapproval.api.dto.AuthResponse;
import com.microapproval.api.dto.LoginRequest;
import com.microapproval.api.dto.RegisterRequest;
import com.microapproval.api.entity.User;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    // 1. Logic Đăng Ký Tài Khoản Mới
    public AuthResponse register(RegisterRequest registerRequest){
        // Kiểm tra xem email đã tồn tại trong hệ thống chưa
        if(userRepository.existsUserByEmail(registerRequest.getEmail())){
            throw new RuntimeException("Email đã tồn tại trong hệ thống");
        }
        // Khởi tạo User Entity mới
        User user = User.builder()
                .fullName(registerRequest.getFullName())
                .email(registerRequest.getEmail())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .build();
        // Lưu user vào Database (UUID và createdAt sẽ tự tạo nhờ @PrePersist)
        User savedUser = userRepository.save(user);

        // Tạo JWT Token cho user vừa đăng ký
        String jwtToken = generateToken(savedUser);

        // Trả về thông tin user và token cho client
        return buildAuthReponse(savedUser, jwtToken);


    }

    // 2. Logic Đăng Nhập
    public AuthResponse login(LoginRequest loginRequest) {

        // Gọi AuthenticationManager đối chiếu thông tin đăng nhập
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        // Nếu thông tin đăng nhập đúng, truy xuất thông tin User từ Database
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với email: " + loginRequest.getEmail()));

        // Sinh JWT mới
        String jwtToken = generateToken(user);

        // Trả về thông tin user và token cho client
        return buildAuthReponse(user, jwtToken);

    }


    private String generateToken(User user){
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        Map<String, Object> extraClaims = Map.of(
                "userId", user.getId()
        );
        return jwtService.generateToken(extraClaims, userDetails);
    }

    private AuthResponse buildAuthReponse(User user, String jwtToken){
        return AuthResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .token(jwtToken)
                .build();
    }



}
