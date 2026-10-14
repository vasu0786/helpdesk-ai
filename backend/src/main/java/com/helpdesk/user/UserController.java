package com.helpdesk.user;

import com.helpdesk.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /** Agents need this to pick an assignee; admins use it to manage users. */
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public List<UserResponse> list(@RequestParam(required = false) Role role) {
        return service.list(role).stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(service.create(request.email(), request.password(), request.fullName(), request.role()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(service.update(id, request, CurrentUser.from(jwt).id()));
    }
}
