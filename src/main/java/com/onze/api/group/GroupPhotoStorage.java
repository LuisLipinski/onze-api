package com.onze.api.group;

import java.util.UUID;

public interface GroupPhotoStorage {

    String upload(UUID groupId, byte[] content);
}
