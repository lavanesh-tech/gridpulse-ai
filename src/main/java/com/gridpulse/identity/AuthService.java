package com.gridpulse.identity;

import com.gridpulse.audit.AuditLog;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Issues signed access tokens for username/password users (OAuth2 "resource owner" style, for local and demo use).
 * The API itself is a standard OAuth2 resource server: it would accept tokens from an external OIDC provider
 * (Cognito, Keycloak, Auth0) by switching the JwtDecoder configuration.
 */
@Service
public class AuthService {
    /** BCrypt hash of a random string; compared against when the user does not exist to keep timing similar. */
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z2PhQq6b1dB2YQwH5XxY1m7a";

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties props;
    private final AuditLog audit;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder,
                       SecurityProperties props, AuditLog audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.audit = audit;
    }

    public record IssuedToken(String accessToken, String tokenType, long expiresIn) {
    }

    public IssuedToken issueToken(String username, String rawPassword) {
        Optional<AppUser> found = users.findByUsername(username);
        boolean ok = encoder.matches(rawPassword, found.map(AppUser::getPasswordHash).orElse(DUMMY_HASH))
                && found.isPresent() && found.get().isEnabled();
        if (!ok) {
            throw new BadCredentialsException("invalid credentials");
        }
        AppUser user = found.get();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.tokenTtl()))
                .subject(user.getUsername())
                .claim("tenant_id", user.getTenantId().toString())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        audit.record(user.getTenantId(), user.getUsername(), "LOGIN", "app_user", user.getId().toString(), Map.of());
        return new IssuedToken(token, "Bearer", props.tokenTtl().toSeconds());
    }
}
