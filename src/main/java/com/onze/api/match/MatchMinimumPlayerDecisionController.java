package com.onze.api.match;

import java.util.UUID;

import com.onze.api.match.MatchMinimumPlayerDecisionModels.ExtendSignupDeadlineRequest;
import com.onze.api.match.MatchMinimumPlayerDecisionModels.MinimumPlayerDecisionResponse;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches/{matchId}/minimum-player-decision")
public class MatchMinimumPlayerDecisionController {

    private final MatchMinimumPlayerDecisionService service;

    public MatchMinimumPlayerDecisionController(MatchMinimumPlayerDecisionService service) {
        this.service = service;
    }

    @GetMapping
    public MinimumPlayerDecisionResponse status(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return service.status(authentication.getName(), matchId);
    }

    @PutMapping("/approve")
    public MinimumPlayerDecisionResponse approveBelowMinimum(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return service.approveBelowMinimum(authentication.getName(), matchId);
    }

    @PutMapping("/extend")
    public MinimumPlayerDecisionResponse extendSignupDeadline(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody ExtendSignupDeadlineRequest request) {
        return service.extendSignupDeadline(authentication.getName(), matchId, request);
    }
}
