package com.onze.api.match;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.onze.api.match.MatchModels.CreateMatchRequest;
import com.onze.api.match.MatchModels.MatchResponse;
import com.onze.api.match.MatchModels.UpdateAttendanceRequest;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchController {

    private final MatchService matchService;
    private final MatchLifecycleService lifecycleService;

    public MatchController(MatchService matchService, MatchLifecycleService lifecycleService) {
        this.matchService = matchService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/api/groups/{groupId}/matches")
    public ResponseEntity<MatchResponse> create(
            Authentication authentication,
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateMatchRequest request) {
        MatchResponse response = matchService.create(authentication.getName(), groupId, request);
        return ResponseEntity.created(URI.create("/api/matches/" + response.id())).body(response);
    }

    @GetMapping("/api/matches/upcoming")
    public List<MatchResponse> listUpcoming(Authentication authentication) {
        lifecycleService.openDueAttendances();
        return matchService.listUpcoming(authentication.getName());
    }

    @GetMapping("/api/groups/{groupId}/matches")
    public List<MatchResponse> listForGroup(
            Authentication authentication,
            @PathVariable UUID groupId) {
        lifecycleService.openDueAttendances();
        return matchService.listForGroup(authentication.getName(), groupId);
    }

    @GetMapping("/api/matches/{matchId}")
    public MatchResponse get(
            Authentication authentication,
            @PathVariable UUID matchId) {
        lifecycleService.openDueAttendances();
        return matchService.get(authentication.getName(), matchId);
    }

    @PutMapping("/api/matches/{matchId}/attendance")
    public MatchResponse updateAttendance(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody UpdateAttendanceRequest request) {
        lifecycleService.openDueAttendances();
        return matchService.updateAttendance(authentication.getName(), matchId, request.status());
    }

    @PutMapping("/api/matches/{matchId}/payment/reported")
    public MatchResponse reportPayment(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return matchService.reportPayment(authentication.getName(), matchId);
    }

    @PutMapping("/api/matches/{matchId}/payments/{playerUserId}/confirm")
    public MatchResponse confirmPayment(
            Authentication authentication,
            @PathVariable UUID matchId,
            @PathVariable UUID playerUserId) {
        return matchService.confirmPayment(
                authentication.getName(),
                matchId,
                playerUserId);
    }

    @DeleteMapping("/api/matches/{matchId}")
    public ResponseEntity<Void> cancelOccurrence(
            Authentication authentication,
            @PathVariable UUID matchId) {
        matchService.cancelOccurrence(authentication.getName(), matchId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/match-series/{seriesId}")
    public ResponseEntity<Void> endSeries(
            Authentication authentication,
            @PathVariable UUID seriesId) {
        matchService.endSeries(authentication.getName(), seriesId);
        return ResponseEntity.noContent().build();
    }
}
