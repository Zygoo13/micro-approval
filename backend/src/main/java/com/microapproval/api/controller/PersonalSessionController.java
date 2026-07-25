package com.microapproval.api.controller;

import com.microapproval.api.dto.CreatePersonalSessionRequest;
import com.microapproval.api.dto.DecisionVoteRequest;
import com.microapproval.api.dto.MicroDecisionResponse;
import com.microapproval.api.dto.PersonalSessionResponse;
import com.microapproval.api.service.PersonalSessionService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/personal/sessions")
@RequiredArgsConstructor
public class PersonalSessionController {

    private final PersonalSessionService sessionService;

    // API: POST http://localhost:8080/api/v1/personal/sessions
    // 1. @RequestBody: Lấy dữ liệu từ body của request (JSON) và ánh xạ vào CreatePersonalSessionRequest
    // 2. @AuthenticationPrincipal: Lấy thông tin người dùng đã xác thực từ SecurityContext (Spring Security)
    // 3. Trích xuất email thông qua userDetails.getUsername() [1]
    // 4. Trả về ResponseEntity với ReviewSession vừa được tạo
    @PostMapping
    public ResponseEntity<PersonalSessionResponse> createPersonalSession(
            @Valid @RequestBody CreatePersonalSessionRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        // Trích xuất email thông qua userDetails.getUsername() [1]
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.createSession(request, userDetails.getUsername()));
    }

    // API: GET http://localhost:8080/api/v1/personal/sessions
    // 1. @AuthenticationPrincipal: Lấy thông tin người dùng đã xác thực từ SecurityContext (Spring Security)
    // 2. Trích xuất email thông qua userDetails.getUsername() [1]
    // 3. Trả về ResponseEntity với danh sách ReviewSession của người dùng
    @GetMapping
    public ResponseEntity<List<PersonalSessionResponse>> getMyPersonalSessions(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(sessionService.getMyPersonalSessions(userDetails.getUsername()));
    }

    // API: PATCH http://localhost:8080/api/v1/personal/sessions/decisions/{decisionId}
    // 1. @PathVariable: Lấy giá trị decisionId từ URL
    // 2. @RequestBody: Lấy dữ liệu từ body của request (JSON) và ánh xạ vào DecisionVoteRequest
    // 3. @AuthenticationPrincipal: Lấy thông tin người dùng đã xác thực từ SecurityContext (Spring Security)
    // 4. Trả về ResponseEntity với MicroDecision đã được cập nhật sau khi vote
    @PatchMapping("/decisions/{decisionId}")
    public ResponseEntity<MicroDecisionResponse> voteDecision(
            @PathVariable String decisionId,
            @Valid @RequestBody DecisionVoteRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(sessionService.voteDecision(decisionId, request, userDetails.getUsername()));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<PersonalSessionResponse> getSessionDetail(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(sessionService.getSessionDetail(sessionId, userDetails.getUsername()));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        sessionService.deleteSession(sessionId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

}
