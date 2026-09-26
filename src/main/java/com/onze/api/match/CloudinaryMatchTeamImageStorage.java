package com.onze.api.match;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.onze.api.match.MatchTeamImageService.TeamImageStorageNotConfiguredException;
import com.onze.api.match.MatchTeamImageService.TeamImageUploadFailedException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CloudinaryMatchTeamImageStorage implements MatchTeamImageStorage {
    private final Cloudinary cloudinary;
    private final boolean configured;

    public CloudinaryMatchTeamImageStorage(
            @Value("${media.cloudinary.cloud-name:}") String cloudName,
            @Value("${media.cloudinary.api-key:}") String apiKey,
            @Value("${media.cloudinary.api-secret:}") String apiSecret) {
        configured = !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }

    @Override
    public String upload(UUID groupId, MatchType matchType, int teamNumber, byte[] content) {
        if (!configured) throw new TeamImageStorageNotConfiguredException();
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    content,
                    ObjectUtils.asMap(
                            "folder", "onze/groups/" + groupId + "/team-identities/"
                                    + matchType.name().toLowerCase(java.util.Locale.ROOT),
                            "public_id", "team-" + teamNumber,
                            "overwrite", true,
                            "invalidate", true,
                            "resource_type", "image"));
            Object secureUrl = result.get("secure_url");
            if (secureUrl == null || secureUrl.toString().isBlank()) {
                throw new TeamImageUploadFailedException();
            }
            return secureUrl.toString();
        } catch (TeamImageUploadFailedException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new TeamImageUploadFailedException(exception);
        }
    }
}
