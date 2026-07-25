package com.microapproval.api.controller;

import com.microapproval.api.dto.*;
import com.microapproval.api.service.AiConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/personal/ai-configuration") @RequiredArgsConstructor
public class AiConfigurationController {
    private final AiConfigurationService service;
    @GetMapping public AiConfigurationResponse get(@AuthenticationPrincipal UserDetails user) { return service.get(user.getUsername()); }
    @PutMapping public AiConfigurationResponse save(@Valid @RequestBody AiConfigurationRequest request, @AuthenticationPrincipal UserDetails user) { return service.save(request, user.getUsername()); }
    @PostMapping("/test") public AiConnectionTestResponse test(@AuthenticationPrincipal UserDetails user) { return service.testConnection(user.getUsername()); }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@AuthenticationPrincipal UserDetails user) { service.remove(user.getUsername()); }
}
