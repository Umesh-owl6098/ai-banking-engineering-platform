package com.umeshowl.banking.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        name = "auth.demo-users.enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(DemoUserProperties.class)
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(
            DemoUserSeeder.class
    );

    private static final String[] DEMO_USERNAMES = {
            "admin",
            "supervisor",
            "fraud.analyst",
            "compliance.analyst",
            "readonly",
    };

    private static final Role[] DEMO_ROLES = {
            Role.ADMIN,
            Role.SUPERVISOR,
            Role.FRAUD_ANALYST,
            Role.COMPLIANCE_ANALYST,
            Role.READ_ONLY,
    };

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoUserProperties demoUserProperties;

    public DemoUserSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DemoUserProperties demoUserProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoUserProperties = demoUserProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!demoUserProperties.hasPassword()) {
            log.warn(
                    "demo_users_skipped reason=missing_password "
                            + "set DEMO_USER_PASSWORD or auth.demo-users.password"
            );
            return;
        }

        String encodedPassword = passwordEncoder.encode(
                demoUserProperties.getPassword()
        );

        for (int index = 0; index < DEMO_USERNAMES.length; index++) {
            seedOrUpdateUser(
                    DEMO_USERNAMES[index],
                    DEMO_ROLES[index],
                    encodedPassword
            );
        }
    }

    private void seedOrUpdateUser(
            String username,
            Role role,
            String encodedPassword
    ) {
        userRepository.findByUsername(username).ifPresentOrElse(
                existing -> syncDemoUser(existing, role, encodedPassword),
                () -> createDemoUser(username, role, encodedPassword)
        );
    }

    private void createDemoUser(
            String username,
            Role role,
            String encodedPassword
    ) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(encodedPassword);
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);

        log.info(
                "demo_user_seeded username={} role={}",
                username,
                role
        );
    }

    private void syncDemoUser(
            User user,
            Role role,
            String encodedPassword
    ) {
        boolean updated = false;

        if (user.getRole() != role) {
            user.setRole(role);
            updated = true;
        }

        if (!passwordEncoder.matches(
                demoUserProperties.getPassword(),
                user.getPasswordHash()
        )) {
            user.setPasswordHash(encodedPassword);
            updated = true;
        }

        if (!user.isEnabled()) {
            user.setEnabled(true);
            updated = true;
        }

        if (updated) {
            userRepository.save(user);
            log.info(
                    "demo_user_updated username={} role={}",
                    user.getUsername(),
                    role
            );
        }
    }
}
