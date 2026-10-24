package com.pranit.connect.dto;

import com.pranit.connect.entity.ChatRoom;
import com.pranit.connect.entity.Message;
import com.pranit.connect.entity.MessageType;
import com.pranit.connect.entity.RoomStatus;
import com.pranit.connect.entity.User;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MessageResponse(
        UUID id,
        RoomDetail roomDetail,
        UserDetail userDetail,
        MessageContent content,
        Instant sentAt
) {

    public static MessageResponse from(final Message message) {
        final ChatRoom room = message.getRoom();
        final User sender = message.getSender();
        return MessageResponse.builder()
                .id(message.getId())
                .roomDetail(RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(room.getOwner() != null
                                ? room.getOwner().getUsername() : null)
                        .roomStatus(room.getStatus())
                        .build())
                .userDetail(UserDetail.builder()
                        .id(sender.getId())
                        .username(sender.getUsername())
                        .build())
                .content(MessageContent.builder()
                        .message(message.getContent())
                        .messageType(message.getType())
                        .build())
                .sentAt(message.getCreatedAt())
                .build();
    }

    @Builder
    public record RoomDetail(
            UUID id,
            String roomCode,
            String roomName,
            String description,
            String owner,
            RoomStatus roomStatus
    ) {
    }

    @Builder
    public record UserDetail(
            UUID id,
            String username
    ) {
    }

    @Builder
    public record MessageContent(
            String message,
            MessageType messageType
    ) {
    }
}