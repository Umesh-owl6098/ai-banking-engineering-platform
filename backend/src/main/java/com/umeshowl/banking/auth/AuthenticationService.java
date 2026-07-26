package com.umeshowl.banking.auth;

import com.umeshowl.banking.auth.dto.CurrentUserResponse;
import com.umeshowl.banking.auth.dto.LoginRequest;
import com.umeshowl.banking.auth.dto.LoginResponse;
import com.umeshowl.banking.observability.BankingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(
            AuthenticationService.class
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final BankingMetrics bankingMetrics;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CurrentUserService currentUserService,
            BankingMetrics bankingMetrics
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
        this.bankingMetrics = bankingMetrics;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> {
                    bankingMetrics.recordAuthenticationFailure();
                    log.warn("authentication_failed reason=user_not_found");
                    return new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "Invalid username or password"
                    );
                });

        if (!user.isEnabled()
                || !passwordEncoder.matches(
                        request.password(),
                        user.getPasswordHash()
                )) {
            bankingMetrics.recordAuthenticationFailure();
            log.warn(
                    "authentication_failed reason=invalid_credentials role={}",
                    user.getRole()
            );
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid username or password"
            );
        }

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getRole()
        );

        bankingMetrics.recordAuthenticationSuccess();
        log.info(
                "authentication_success role={}",
                user.getRole()
        );

        return LoginResponse.from(
                jwtService.generateToken(user),
                authenticatedUser
        );
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser() {
        return CurrentUserResponse.from(
                currentUserService.requireCurrentUser()
        );
    }
}
