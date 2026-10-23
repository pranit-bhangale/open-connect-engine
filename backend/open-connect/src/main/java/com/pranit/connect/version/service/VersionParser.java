package com.pranit.connect.version.service;

import org.jspecify.annotations.NullMarked;
import org.springframework.web.accept.ApiVersionParser;

import java.util.Locale;

@NullMarked
public final class VersionParser implements ApiVersionParser<String> {

    @Override
    public String parseVersion(String version) {
        if (!version.matches("(?i)v\\d+")) {
            throw new IllegalArgumentException("Invalid API version: " + version);
        }
        return version.toLowerCase(Locale.ROOT);
    }
}
