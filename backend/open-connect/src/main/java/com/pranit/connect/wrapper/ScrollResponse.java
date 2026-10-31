package com.pranit.connect.wrapper;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Builder
public record ScrollResponse<T>(
        List<T> contents,
        @Nullable String nextScrollId,
        @Nullable String prevScrollId,
        boolean hasNext,
        boolean hasPrevious,
        int pageSize
) {
}