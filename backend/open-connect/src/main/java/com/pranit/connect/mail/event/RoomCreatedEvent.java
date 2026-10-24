package com.pranit.connect.mail.event;

import lombok.Builder;

@Builder
public record RoomCreatedEvent(
        String creatorName,
        String roomName,
        String creatorEmail,
        String roomCode
) {
}
