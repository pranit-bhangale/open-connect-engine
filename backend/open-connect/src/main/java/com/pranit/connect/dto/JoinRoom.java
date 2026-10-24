package com.pranit.connect.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JoinRoom(
        @NotNull(message = "User details required")
        @Valid
        UserRequest user,

        @NotNull(message = "Room details required")
        @Valid
        RoomDetail detail
) {
    public record UserRequest(
            @NotBlank(message = "Name required")
            @Size(min = 1, max = 50, message = "Name must be between 1 and 50 characters")
            String username,

            @NotBlank(message = "Email required")
            @Email(message = "Invalid email format")
            @Size(max = 254, message = "Email cannot exceed 254 characters")
            String email
    ) {
    }

    public record RoomDetail(
            @NotBlank(message = "Room code required")
            @Size(min = 1, max = 36, message = "Room code must be between 1 and 36 characters")
            String roomCode
    ) {
    }
}
