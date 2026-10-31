package com.pranit.connect.event;

import com.pranit.connect.entity.RoomStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record RoomEvent(
        UUID roomId,
        String roomCode,
        UUID userId,
        String username,
        EventType eventType,
        String message,
        Instant timestamp,
        RoomStatus roomStatus,
        boolean isHost,
        boolean isHostOnline,
        int memberCount
) {
    public enum EventType {
        JOINED,
        LEFT,
        CLOSED,
        CHAT,
        TYPING,
        SYSTEM
    }
}
