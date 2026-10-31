package com.pranit.connect.dto;

import com.pranit.connect.entity.RoomStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record RoomResponse(
        UserResponse user,
        RoomDetail room
) {
    @Builder
    public record UserResponse(
            UUID id,
            String username,
            boolean isHost
    ) {
    }

    @Builder
    public record RoomDetail(
            UUID id,
            String roomCode,
            String roomName,
            String description,
            String owner,
            String closedBy,
            RoomStatus roomStatus,
            boolean isHostOnline,
            List<RoomMemberResponse> roomMembers,
            Instant createdAt,
            Instant closedAt
    ) {
    }
}
