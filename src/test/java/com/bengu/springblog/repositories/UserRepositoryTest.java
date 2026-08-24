package com.bengu.springblog.repositories;

import com.bengu.springblog.entities.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByUsername_shouldReturnSavedUser() {
        User user = new User(
                "bengu",
                "bengu@example.com",
                "hashed-password"
        );

        userRepository.saveAndFlush(user);

        entityManager.clear();

        Optional<User> foundUser =
                userRepository.findByUsername("bengu");

        assertThat(foundUser).isPresent();

        User retrievedUser = foundUser.get();

        assertThat(retrievedUser.getId()).isNotNull();
        assertThat(retrievedUser.getUsername())
                .isEqualTo("bengu");
        assertThat(retrievedUser.getEmail())
                .isEqualTo("bengu@example.com");
        assertThat(retrievedUser.getPasswordHash())
                .isEqualTo("hashed-password");
        assertThat(retrievedUser.getCreatedAt())
                .isNotNull();
    }

    @Test
    void findByEmail_shouldReturnSavedUser() {
        persistUser("bengu", "bengu@example.com", "google-123");

        Optional<User> foundUser = userRepository.findByEmail("bengu@example.com");

        assertThat(foundUser)
                .isPresent()
                .get()
                .extracting(User::getUsername, User::getGoogleSub)
                .containsExactly("bengu", "google-123");
    }

    @Test
    void findByGoogleSub_shouldReturnSavedUser() {
        persistUser("bengu", "bengu@example.com", "google-123");

        Optional<User> foundUser = userRepository.findByGoogleSub("google-123");

        assertThat(foundUser)
                .isPresent()
                .get()
                .extracting(User::getUsername, User::getEmail)
                .containsExactly("bengu", "bengu@example.com");
    }

    @Test
    void existsByUsername_shouldReflectStoredUsername() {
        persistUser("bengu", "bengu@example.com", "google-123");

        assertThat(userRepository.existsByUsername("bengu")).isTrue();
        assertThat(userRepository.existsByUsername("missing")).isFalse();
    }

    @Test
    void existsByEmail_shouldReflectStoredEmail() {
        persistUser("bengu", "bengu@example.com", "google-123");

        assertThat(userRepository.existsByEmail("bengu@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("missing@example.com")).isFalse();
    }

    @Test
    void existsByGoogleSub_shouldReflectLinkedGoogleAccount() {
        persistUser("bengu", "bengu@example.com", "google-123");

        assertThat(userRepository.existsByGoogleSub("google-123")).isTrue();
        assertThat(userRepository.existsByGoogleSub("missing-google-sub")).isFalse();
    }

    private void persistUser(String username, String email, String googleSub) {
        User user = new User(username, email, null);
        user.setGoogleSub(googleSub);
        userRepository.saveAndFlush(user);
        entityManager.clear();
    }
}
