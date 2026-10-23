package com.pranit.connect.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoom(
        @NotNull(message = "User details required")
        @Valid
        UserRequest user,

        @NotNull(message = "Room details required")
        @Valid
        RoomRequest room
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

    public record RoomRequest(
            @NotBlank(message = "Room name is required")
            @Size(min = 1, max = 100, message = "Room name must be between 1 and 100 characters")
            String roomName,

            @NotBlank(message = "Description is required")
            @Size(max = 1000, message = "Description cannot exceed 1000 characters")
            String description
    ) {
    }
}
