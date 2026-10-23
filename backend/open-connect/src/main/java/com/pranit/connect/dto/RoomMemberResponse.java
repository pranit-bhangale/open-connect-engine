package com.pranit.connect.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record RoomMemberResponse(
        UUID roomId,
        String roomName,
        UUID userId,
        String userName,
        Instant joinedAt
) {
}