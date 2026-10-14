package com.helpdesk.auth;

import com.helpdesk.user.UserResponse;

import java.time.Instant;

public record TokenResponse(String token, String tokenType, Instant expiresAt, UserResponse user) {
}
