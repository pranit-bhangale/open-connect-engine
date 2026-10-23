package com.pranit.connect.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateRoom(
        @Valid
        UserRequest user,
        @Valid
        RoomRequest room
) {
    public record UserRequest(
            @NotBlank(message = "Name required")
            String username,
            @NotBlank(message = "Email required")
            @Email(message = "Invalid email format")
            String email
    ) {
    }

    public record RoomRequest(
            String roomName,
            String description
    ) {
    }
}
