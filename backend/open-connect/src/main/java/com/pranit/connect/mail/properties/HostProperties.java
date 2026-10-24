package com.pranit.connect.mail.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "host")
public record HostProperties(
        String email,
        String portfolioUrl
) {
}