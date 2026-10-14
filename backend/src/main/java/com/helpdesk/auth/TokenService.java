package com.helpdesk.auth;

import com.helpdesk.config.HelpdeskProperties;
import com.helpdesk.user.User;
import com.helpdesk.user.UserResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final HelpdeskProperties props;

    public TokenService(JwtEncoder encoder, HelpdeskProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public TokenResponse issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.jwt().ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("helpdesk-ai")
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("uid", user.getId())
                .claim("name", user.getFullName())
                .claim("role", user.getRole().name())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", expiresAt, UserResponse.from(user));
    }
}
