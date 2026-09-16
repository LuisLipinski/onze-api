package com.onze.api.devtools;

import java.util.UUID;

import com.onze.api.devtools.DevTestDataModels.ApplyScenarioRequest;
import com.onze.api.devtools.DevTestDataModels.ApplyScenarioResponse;
import com.onze.api.devtools.DevTestDataModels.GeneratePlayersRequest;
import com.onze.api.devtools.DevTestDataModels.GeneratePlayersResponse;
import com.onze.api.devtools.DevTestDataModels.MatchAttendanceResponse;
import com.onze.api.devtools.DevTestDataModels.StatusResponse;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dev/test-data")
@ConditionalOnProperty(prefix = "dev.test-data", name = "enabled", havingValue = "true")
public class DevTestDataController {

    private final DevTestDataService service;

    public DevTestDataController(DevTestDataService service) {
        this.service = service;
    }

    @GetMapping("/matches/{matchId}")
    public StatusResponse status(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return service.status(authentication.getName(), matchId);
    }

    @PostMapping("/matches/{matchId}/generate")
    public GeneratePlayersResponse generate(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody GeneratePlayersRequest request) {
        return service.generate(authentication.getName(), matchId, request.count());
    }

    @PutMapping("/matches/{matchId}/scenario")
    public ApplyScenarioResponse applyScenario(
            Authentication authentication,
            @PathVariable UUID matchId,
            @Valid @RequestBody ApplyScenarioRequest request) {
        return service.applyScenario(authentication.getName(), matchId, request.scenario());
    }

    @PostMapping("/matches/{matchId}/attendance")
    public MatchAttendanceResponse addToMatch(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return service.addToMatch(authentication.getName(), matchId);
    }

    @DeleteMapping("/matches/{matchId}/attendance")
    public MatchAttendanceResponse removeFromMatch(
            Authentication authentication,
            @PathVariable UUID matchId) {
        return service.removeFromMatch(authentication.getName(), matchId);
    }
}
