package com.pranit.connect.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record JoinRoom(
        @Valid
        UserRequest user,
        @Valid
        RoomDetail detail
) {
    public record UserRequest(
            @NotBlank(message = "Name required")
            String username,
            @NotBlank(message = "Email required")
            @Email(message = "Invalid email format")
            String email
    ) {
    }

    public record RoomDetail(
            @NotBlank(message = "Room code required")
            String roomCode
    ) {
    }
}
