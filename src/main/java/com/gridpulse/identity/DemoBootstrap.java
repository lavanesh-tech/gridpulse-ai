package com.gridpulse.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Local convenience: if GRIDPULSE_ADMIN_PASSWORD is set and there are no users yet, create a demo tenant + ADMIN. */
@Component
class DemoBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoBootstrap.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final String username;
    private final String password;

    DemoBootstrap(AppUserRepository users, PasswordEncoder encoder, JdbcTemplate jdbc,
                  @Value("${gridpulse.bootstrap.admin-username:admin}") String username,
                  @Value("${gridpulse.bootstrap.admin-password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank() || users.count() > 0) {
            return;
        }
        if (password.length() < 12) {
            throw new IllegalStateException("GRIDPULSE_ADMIN_PASSWORD must be at least 12 characters");
        }
        UUID tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenant (id, name) VALUES (?, ?)", tenant, "Demo Fleet Co (synthetic)");
        users.save(new AppUser(tenant, username, encoder.encode(password), Role.ADMIN));
        log.info("Created demo tenant {} and ADMIN user '{}'", tenant, username);
    }
}
