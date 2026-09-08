package com.onze.api.match;

import java.util.UUID;

import com.onze.api.group.GroupService;
import com.onze.api.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushDeviceService {

    private final PushDeviceRepository pushDeviceRepository;
    private final UserRepository userRepository;

    public PushDeviceService(
            PushDeviceRepository pushDeviceRepository,
            UserRepository userRepository) {
        this.pushDeviceRepository = pushDeviceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void register(String authenticatedUserId, String token) {
        UUID userId = parseUserId(authenticatedUserId);
        if (!userRepository.existsById(userId)) {
            throw new GroupService.GroupUserNotFoundException();
        }

        PushDevice device = pushDeviceRepository.findByExpoPushToken(token)
                .orElseGet(() -> new PushDevice(userId, token));
        device.registerFor(userId);
        pushDeviceRepository.save(device);
    }

    @Transactional
    public void unregister(String authenticatedUserId, String token) {
        UUID userId = parseUserId(authenticatedUserId);
        pushDeviceRepository.findByExpoPushToken(token)
                .filter(device -> device.getUserId().equals(userId))
                .ifPresent(PushDevice::deactivate);
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupService.GroupUserNotFoundException();
        }
    }
}
