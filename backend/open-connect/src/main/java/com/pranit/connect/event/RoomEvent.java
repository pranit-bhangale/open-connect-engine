package com.pranit.connect.event;


import com.pranit.connect.entity.RoomStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record RoomEvent(
        UUID roomId,
        UUID userId,
        String username,
        EventType eventType,
        Instant timestamp,
        RoomStatus roomStatus
) {
    public enum EventType {
        JOINED, LEFT, CLOSED
    }
}
