package com.helpdesk.user;

import java.time.Instant;

public record UserResponse(Long id, String email, String fullName, Role role, boolean active, Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.isActive(), u.getCreatedAt());
    }
}
