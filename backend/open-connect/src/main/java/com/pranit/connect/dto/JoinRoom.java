package com.pranit.connect.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
            @Size(max = 36, message = "Room code must not exceed 36 characters")
            String roomCode,

            @Size(max = 36, message = "Room ID must not exceed 36 characters")
            String roomId
    ) {
        public RoomDetail(String roomCode) {
            this(roomCode, null);
        }

        @AssertTrue(message = "Either roomCode or roomId must be provided")
        @JsonIgnore
        public boolean hasIdentifier() {
            return (roomCode != null && !roomCode.isBlank()) || (roomId != null && !roomId.isBlank());
        }

        @JsonIgnore
        public String getIdentifier() {
            if (roomId != null && !roomId.isBlank()) {
                return roomId.trim();
            }
            if (roomCode != null && !roomCode.isBlank()) {
                return roomCode.trim();
            }
            return null;
        }
    }
}
