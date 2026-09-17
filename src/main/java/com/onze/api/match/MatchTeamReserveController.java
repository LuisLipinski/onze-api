package com.onze.api.match;

import java.util.UUID;

import com.onze.api.match.MatchTeamReserveModels.MatchTeamReservesResponse;
import com.onze.api.match.MatchTeamReserveModels.UpdateMatchTeamReservesRequest;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchTeamReserveController {

    private final MatchTeamReserveService reserveService;

    public MatchTeamReserveController(MatchTeamReserveService reserveService) {
        this.reserveService = reserveService;
    }

    @GetMapping("/api/matches/{matchId}/teams/reserves")
    public MatchTeamReservesResponse get(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return reserveService.get(authentication.getName(), matchId);
    }

    @PostMapping("/api/matches/{matchId}/teams/reserves/auto")
    public MatchTeamReservesResponse autoAssign(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return reserveService.autoAssign(authentication.getName(), matchId);
    }

    @PutMapping("/api/matches/{matchId}/teams/reserves")
    public MatchTeamReservesResponse update(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody UpdateMatchTeamReservesRequest request) {
        return reserveService.update(
                authentication.getName(), matchId, request.reserveAssignmentIds());
    }
}
