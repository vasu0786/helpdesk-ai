package com.helpdesk.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** Admin-only: create a user with any role (normally an agent). */
public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotNull Role role
) {
    public CreateUserRequest {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }
}
