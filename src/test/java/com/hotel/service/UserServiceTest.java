package com.hotel.service;

import com.hotel.entity.User;
import com.hotel.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    private User adminUser;
    private User staffUser;
    private static final String RAW_PASSWORD = "admin123";
    private static final String HASHED_PASSWORD = "$2a$10$abcdefghij";

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L).username("admin").password(HASHED_PASSWORD)
                .fullName("Administrator").role(User.Role.ADMIN).active(true).build();

        staffUser = User.builder()
                .id(2L).username("staff").password(HASHED_PASSWORD)
                .fullName("Staff Member").role(User.Role.STAFF).active(true).build();
    }

    // -----------------------------------------------------------------------
    // login
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("login")
    class Login {

        @Test @DisplayName("returns user when credentials are valid")
        void validCredentials_returnsUser() {
            when(userRepository.findByUsernameAndActiveTrue("admin"))
                    .thenReturn(Optional.of(adminUser));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
            when(userRepository.save(any())).thenReturn(adminUser);

            Optional<User> result = userService.login("admin", RAW_PASSWORD);
            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("admin");
        }

        @Test @DisplayName("returns empty when password is wrong")
        void wrongPassword_returnsEmpty() {
            when(userRepository.findByUsernameAndActiveTrue("admin"))
                    .thenReturn(Optional.of(adminUser));
            when(passwordEncoder.matches("wrongpass", HASHED_PASSWORD)).thenReturn(false);

            Optional<User> result = userService.login("admin", "wrongpass");
            assertThat(result).isEmpty();
        }

        @Test @DisplayName("returns empty when username does not exist")
        void unknownUser_returnsEmpty() {
            when(userRepository.findByUsernameAndActiveTrue("ghost"))
                    .thenReturn(Optional.empty());

            Optional<User> result = userService.login("ghost", RAW_PASSWORD);
            assertThat(result).isEmpty();
        }

        @Test @DisplayName("sets currentUser on successful login")
        void setsCurrentUser_onSuccess() {
            when(userRepository.findByUsernameAndActiveTrue("admin"))
                    .thenReturn(Optional.of(adminUser));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
            when(userRepository.save(any())).thenReturn(adminUser);

            userService.login("admin", RAW_PASSWORD);
            assertThat(userService.getCurrentUser()).isEqualTo(adminUser);
        }

        @Test @DisplayName("does not set currentUser on failed login")
        void doesNotSetCurrentUser_onFailure() {
            when(userRepository.findByUsernameAndActiveTrue("admin"))
                    .thenReturn(Optional.of(adminUser));
            when(passwordEncoder.matches("bad", HASHED_PASSWORD)).thenReturn(false);

            userService.login("admin", "bad");
            assertThat(userService.getCurrentUser()).isNull();
        }

        @Test @DisplayName("updates lastLogin timestamp on success")
        void updatesLastLogin() {
            when(userRepository.findByUsernameAndActiveTrue("admin"))
                    .thenReturn(Optional.of(adminUser));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
            when(userRepository.save(any())).thenReturn(adminUser);

            userService.login("admin", RAW_PASSWORD);
            assertThat(adminUser.getLastLogin()).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // logout
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test @DisplayName("clears currentUser")
        void clearsCurrentUser() {
            when(userRepository.findByUsernameAndActiveTrue("staff"))
                    .thenReturn(Optional.of(staffUser));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(userRepository.save(any())).thenReturn(staffUser);

            userService.login("staff", "staff123");
            assertThat(userService.getCurrentUser()).isNotNull();

            userService.logout();
            assertThat(userService.getCurrentUser()).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // isAdmin
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("isAdmin")
    class IsAdmin {

        @Test @DisplayName("returns true for ADMIN role")
        void trueForAdmin() {
            when(userRepository.findByUsernameAndActiveTrue("admin"))
                    .thenReturn(Optional.of(adminUser));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
            when(userRepository.save(any())).thenReturn(adminUser);

            userService.login("admin", RAW_PASSWORD);
            assertThat(userService.isAdmin()).isTrue();
        }

        @Test @DisplayName("returns false for STAFF role")
        void falseForStaff() {
            when(userRepository.findByUsernameAndActiveTrue("staff"))
                    .thenReturn(Optional.of(staffUser));
            when(passwordEncoder.matches(RAW_PASSWORD, HASHED_PASSWORD)).thenReturn(true);
            when(userRepository.save(any())).thenReturn(staffUser);

            userService.login("staff", RAW_PASSWORD);
            assertThat(userService.isAdmin()).isFalse();
        }

        @Test @DisplayName("returns false when no user is logged in")
        void falseWhenNotLoggedIn() {
            assertThat(userService.isAdmin()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // createUser
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test @DisplayName("encodes the raw password before saving")
        void encodesPassword() {
            when(passwordEncoder.encode("secret")).thenReturn("$2a$hashed");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.createUser("newuser", "secret", "New User", User.Role.STAFF);
            assertThat(result.getPassword()).isEqualTo("$2a$hashed");
            verify(passwordEncoder).encode("secret");
        }

        @Test @DisplayName("stores plain-text password is never saved")
        void plainTextPasswordNotSaved() {
            when(passwordEncoder.encode("secret")).thenReturn("$2a$hashed");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.createUser("newuser", "secret", "New User", User.Role.STAFF);
            assertThat(result.getPassword()).doesNotContain("secret");
        }

        @Test @DisplayName("new user is active by default")
        void newUserIsActive() {
            when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.createUser("u", "p", "Name", User.Role.STAFF);
            assertThat(result.isActive()).isTrue();
        }

        @Test @DisplayName("stores correct role")
        void storesRole() {
            when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.createUser("mgr", "pass", "Manager", User.Role.ADMIN);
            assertThat(result.getRole()).isEqualTo(User.Role.ADMIN);
        }
    }

    // -----------------------------------------------------------------------
    // initializeDefaultUsers
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("initializeDefaultUsers")
    class InitDefaultUsers {

        @Test @DisplayName("creates admin when admin does not exist")
        void createsAdmin_whenMissing() {
            when(userRepository.existsByUsername("admin")).thenReturn(false);
            when(userRepository.existsByUsername("staff")).thenReturn(true);
            when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.initializeDefaultUsers();
            verify(userRepository, times(1)).save(argThat(u ->
                    u.getUsername().equals("admin")));
        }

        @Test @DisplayName("creates staff when staff does not exist")
        void createsStaff_whenMissing() {
            when(userRepository.existsByUsername("admin")).thenReturn(true);
            when(userRepository.existsByUsername("staff")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("$2a$hashed");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.initializeDefaultUsers();
            verify(userRepository, times(1)).save(argThat(u ->
                    u.getUsername().equals("staff")));
        }

        @Test @DisplayName("skips creation when both users already exist")
        void skipsAll_whenBothExist() {
            when(userRepository.existsByUsername("admin")).thenReturn(true);
            when(userRepository.existsByUsername("staff")).thenReturn(true);

            userService.initializeDefaultUsers();
            verify(userRepository, never()).save(any());
        }
    }
}
