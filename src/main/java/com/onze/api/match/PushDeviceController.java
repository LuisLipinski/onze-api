package com.onze.api.match;

import com.onze.api.match.MatchModels.PushTokenRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    public PushDeviceController(PushDeviceService pushDeviceService) {
        this.pushDeviceService = pushDeviceService;
    }

    @PutMapping("/api/devices/push-token")
    public ResponseEntity<Void> register(
            Authentication authentication,
            @Valid @RequestBody PushTokenRequest request) {
        pushDeviceService.register(authentication.getName(), request.token());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/devices/push-token")
    public ResponseEntity<Void> unregister(
            Authentication authentication,
            @RequestParam
            @Pattern(regexp = "^(Expo(nent)?PushToken)\\[[A-Za-z0-9_-]+]$")
            String token) {
        pushDeviceService.unregister(authentication.getName(), token);
        return ResponseEntity.noContent().build();
    }
}
