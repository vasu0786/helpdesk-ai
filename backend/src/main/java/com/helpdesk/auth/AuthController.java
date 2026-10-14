package com.helpdesk.auth;

import com.helpdesk.common.CurrentUser;
import com.helpdesk.user.Role;
import com.helpdesk.user.User;
import com.helpdesk.user.UserRepository;
import com.helpdesk.user.UserResponse;
import com.helpdesk.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthController(UserRepository userRepository, UserService userService,
                          PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().strip())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!user.isActive()) {
            throw new BadCredentialsException("Account is deactivated");
        }
        return tokenService.issue(user);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.create(request.email(), request.password(), request.fullName(), Role.CUSTOMER);
        return tokenService.issue(user);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userService.get(CurrentUser.from(jwt).id()));
    }
}
