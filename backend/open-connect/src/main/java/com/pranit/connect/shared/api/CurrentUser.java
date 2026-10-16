package com.pranit.connect.shared.api;

import java.util.UUID;

public interface CurrentUser {

    UUID userId();

    String tenantId();
}