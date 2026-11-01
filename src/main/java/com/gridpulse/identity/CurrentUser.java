package com.gridpulse.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

/**
 * The authenticated caller, read from the validated JWT. The tenant id ALWAYS comes from here,
 * never from a request parameter, which is what makes tenant isolation hold.
 */
public record CurrentUser(String username, UUID tenantId, Role role) {

    public static CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            List<String> roles = jwt.getClaimAsStringList("roles");
            String tenant = jwt.getClaimAsString("tenant_id");
            if (tenant == null || roles == null || roles.isEmpty()) {
                throw new IllegalStateException("token is missing tenant or role claims");
            }
            return new CurrentUser(jwt.getSubject(), UUID.fromString(tenant), Role.valueOf(roles.get(0)));
        }
        throw new IllegalStateException("no authenticated user");
    }
}
