package com.onze.api.group;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.onze.api.group.GroupService.PhotoStorageNotConfiguredException;
import com.onze.api.group.GroupService.PhotoUploadFailedException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CloudinaryGroupPhotoStorage implements GroupPhotoStorage {

    private final Cloudinary cloudinary;
    private final boolean configured;

    public CloudinaryGroupPhotoStorage(
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
    public String upload(UUID groupId, byte[] content) {
        if (!configured) {
            throw new PhotoStorageNotConfiguredException();
        }

        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    content,
                    ObjectUtils.asMap(
                            "folder", "onze/groups",
                            "public_id", groupId.toString(),
                            "overwrite", true,
                            "resource_type", "image"));
            Object secureUrl = result.get("secure_url");
            if (secureUrl == null || secureUrl.toString().isBlank()) {
                throw new PhotoUploadFailedException();
            }
            return secureUrl.toString();
        } catch (PhotoUploadFailedException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new PhotoUploadFailedException(exception);
        }
    }
}
