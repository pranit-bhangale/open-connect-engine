package com.pranit.connect.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OwnerDetail(
        @NotBlank(message = "Username required")
        String username,
        @NotBlank(message = "Email required")
        @Email(message = "Invalid email format")
        String email
) {
}