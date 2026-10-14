package com.helpdesk.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** Public self-registration. Always creates a CUSTOMER. */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 120) String fullName
) {
    public RegisterRequest {
        if (email != null) email = email.strip().toLowerCase(Locale.ROOT);
    }
}
