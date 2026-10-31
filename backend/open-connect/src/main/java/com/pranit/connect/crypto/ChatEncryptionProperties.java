package com.pranit.connect.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.chat.encryption")
public record ChatEncryptionProperties(
        String secretKey
) {
}
