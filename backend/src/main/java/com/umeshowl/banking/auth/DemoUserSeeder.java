package com.umeshowl.banking.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        name = "auth.demo-users.enabled",
        havingValue = "true"
)
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(
            DemoUserSeeder.class
    );

    private static final String DEMO_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoUserSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser("admin", Role.ADMIN);
        seedUser("supervisor", Role.SUPERVISOR);
        seedUser("fraud.analyst", Role.FRAUD_ANALYST);
        seedUser("compliance.analyst", Role.COMPLIANCE_ANALYST);
        seedUser("readonly", Role.READ_ONLY);
    }

    private void seedUser(String username, Role role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);

        log.info(
                "demo_user_seeded username={} role={}",
                username,
                role
        );
    }
}
