package com.bankflow.authservice.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.bankflow.authservice.dto.RegisterRequest;
import com.bankflow.authservice.dto.UserResponse;
import com.bankflow.authservice.entity.User;
import com.bankflow.authservice.entity.UserStatus;
import com.bankflow.authservice.repository.UserRepository;
import com.bankflow.common.exception.DuplicateResourceException;
import com.bankflow.authservice.dto.LoginRequest;
import com.bankflow.authservice.dto.LoginResponse;
import com.bankflow.authservice.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponse register(RegisterRequest request) {

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException(
                    "Username already exists"
            );
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "Email already exists"
            );
        }

        User user = new User();

        user.setUsername(request.username());
        user.setEmail(request.email());

        user.setPassword(
                passwordEncoder.encode(request.password())
        );

        user.setRole(request.role());
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        return mapToResponse(savedUser);
    }

    private UserResponse mapToResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() ->
                        new RuntimeException("Invalid username or password")
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword())) {

            throw new RuntimeException(
                    "Invalid username or password"
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException(
                    "User account is not active"
            );
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(
                token,
                "Bearer",
                3600000
        );
    }
}