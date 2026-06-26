package com.systeam.streaks.controller;

import com.systeam.security.JwtPrincipal;
import com.systeam.streaks.dto.StreakStatusResponse;
import com.systeam.streaks.service.StreakService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/streaks")
public class StreakController {

    private final StreakService streakService;

    public StreakController(StreakService streakService) {
        this.streakService = streakService;
    }

    private Long currentUserId() {
        return ((JwtPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).userId();
    }

    @PostMapping("/check-in")
    public ResponseEntity<StreakStatusResponse> checkIn() {
        return ResponseEntity.ok(streakService.checkIn(currentUserId()));
    }

    @GetMapping("/me")
    public ResponseEntity<StreakStatusResponse> me() {
        return ResponseEntity.ok(streakService.getStatus(currentUserId()));
    }
}
