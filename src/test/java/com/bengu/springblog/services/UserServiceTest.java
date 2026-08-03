package com.bengu.springblog.services;

import com.bengu.springblog.dto.CreateUserRequest;
import com.bengu.springblog.dto.UserResponse;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                passwordEncoder
        );
    }

    @Test
    void createUser_shouldEncodePasswordAndSaveUser_whenUsernameIsAvailable() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("bengu");
        request.setEmail("bengu@example.com");
        request.setPassword("plain-password");

        when(userRepository.findByUsername("bengu"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("plain-password"))
                .thenReturn("hashed-password");

        User persistedUser = mock(User.class);
        LocalDateTime createdAt =
                LocalDateTime.of(2026, 7, 30, 12, 0);

        when(persistedUser.getId()).thenReturn(1L);
        when(persistedUser.getUsername()).thenReturn("bengu");
        when(persistedUser.getCreatedAt()).thenReturn(createdAt);

        when(userRepository.save(any(User.class)))
                .thenReturn(persistedUser);

        UserResponse response = userService.createUser(request);

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());
        verify(passwordEncoder).encode("plain-password");

        User userGivenToRepository = userCaptor.getValue();

        assertThat(userGivenToRepository.getUsername())
                .isEqualTo("bengu");

        assertThat(userGivenToRepository.getEmail())
                .isEqualTo("bengu@example.com");

        assertThat(userGivenToRepository.getPasswordHash())
                .isEqualTo("hashed-password");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("bengu");
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    }
}