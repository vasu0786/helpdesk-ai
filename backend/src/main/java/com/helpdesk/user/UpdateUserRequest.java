package com.helpdesk.user;

import jakarta.validation.constraints.Size;

/** Admin-only partial update: only non-null fields are applied. */
public record UpdateUserRequest(
        @Size(min = 1, max = 120) String fullName,
        Role role,
        Boolean active
) {
}
