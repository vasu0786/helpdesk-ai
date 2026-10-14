package com.helpdesk.common;

import com.helpdesk.user.Role;
import org.springframework.security.oauth2.jwt.Jwt;

/** The logged-in user, read from the JWT. Inject with @AuthenticationPrincipal Jwt and call CurrentUser.from(jwt). */
public record CurrentUser(Long id, String email, Role role) {

    public static CurrentUser from(Jwt jwt) {
        Number uid = jwt.getClaim("uid");
        return new CurrentUser(uid.longValue(), jwt.getSubject(), Role.valueOf(jwt.getClaimAsString("role")));
    }

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }

    public boolean isStaff() {
        return role == Role.AGENT || role == Role.ADMIN;
    }
}
