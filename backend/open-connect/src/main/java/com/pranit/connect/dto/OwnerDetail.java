package com.pranit.connect.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnerDetail(
        @NotBlank(message = "Username required")
        @Size(min = 1, max = 50, message = "Username must be between 1 and 50 characters")
        String username,

        @NotBlank(message = "Email required")
        @Email(message = "Invalid email format")
        @Size(max = 254, message = "Email cannot exceed 254 characters")
        String email
) {
}
