package com.gridpulse.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Runs after JWT authentication: limits per user when authenticated, per client IP otherwise; login is stricter. */
final class RateLimitFilter extends OncePerRequestFilter {
    static final String LOGIN_PATH = "/api/v1/auth/token";

    private final RateLimiter apiLimiter;
    private final RateLimiter loginLimiter;

    RateLimitFilter(RateLimiter apiLimiter, RateLimiter loginLimiter) {
        this.apiLimiter = apiLimiter;
        this.loginLimiter = loginLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean login = LOGIN_PATH.equals(request.getRequestURI());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        String key = (!login && authenticated) ? "user:" + auth.getName() : "ip:" + request.getRemoteAddr();
        RateLimiter limiter = login ? loginLimiter : apiLimiter;
        if (!limiter.tryAcquire(key)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429}");
            return;
        }
        chain.doFilter(request, response);
    }
}
