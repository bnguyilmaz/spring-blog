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
}