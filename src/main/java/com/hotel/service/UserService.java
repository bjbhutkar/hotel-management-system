package com.hotel.service;

import com.hotel.entity.User;
import com.hotel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /** Holds the currently logged-in user for this session. */
    private User currentUser;

    @Transactional
    public Optional<User> login(String username, String rawPassword) {
        Optional<User> userOpt = userRepository.findByUsernameAndActiveTrue(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(rawPassword, user.getPassword())) {
                user.setLastLogin(LocalDateTime.now());
                userRepository.save(user);
                this.currentUser = user;
                log.info("User '{}' logged in successfully.", username);
                return Optional.of(user);
            }
        }
        log.warn("Failed login attempt for username '{}'.", username);
        return Optional.empty();
    }

    public void logout() {
        log.info("User '{}' logged out.", currentUser != null ? currentUser.getUsername() : "unknown");
        this.currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == User.Role.ADMIN;
    }

    @Transactional
    public User createUser(String username, String rawPassword, String fullName, User.Role role) {
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .role(role)
                .active(true)
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public User updatePassword(Long userId, String newRawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setPassword(passwordEncoder.encode(newRawPassword));
        return userRepository.save(user);
    }

    @Transactional
    public void deactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setActive(false);
        userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /** Seeds default admin and staff accounts on first run. */
    @Transactional
    public void initializeDefaultUsers() {
        if (!userRepository.existsByUsername("admin")) {
            createUser("admin", "admin123", "Administrator", User.Role.ADMIN);
            log.info("Default admin user created.");
        }
        if (!userRepository.existsByUsername("staff")) {
            createUser("staff", "staff123", "Staff Member", User.Role.STAFF);
            log.info("Default staff user created.");
        }
    }
}
