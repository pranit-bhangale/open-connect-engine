package com.pranit.connect.dto;

import com.pranit.connect.entity.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MessageRequest(

        @NotNull(message = "Room ID is required")
        UUID roomId,

        @NotNull(message = "Sender ID is required")
        UUID senderId,

        @NotBlank(message = "Message content cannot be empty")
        @Size(max = 10000, message = "Message cannot exceed 10000 characters")
        String content,

        @NotNull(message = "Message type is required")
        MessageType type
) {
}
