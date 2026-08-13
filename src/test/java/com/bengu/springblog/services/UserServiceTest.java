package com.bengu.springblog.services;

import java.util.List;
import com.bengu.springblog.dto.CreateUserRequest;
import com.bengu.springblog.dto.UserResponse;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.exceptions.EmailAlreadyExistsException;
import com.bengu.springblog.exceptions.GoogleAccountAlreadyLinkedException;
import com.bengu.springblog.exceptions.UsernameAlreadyExistsException;
import com.bengu.springblog.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String GOOGLE_SUB = "google-123";
    private static final String EMAIL = "bengu@example.com";
    private static final String USERNAME = "bengu";

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

    /*
     * Normal kullanıcı oluşturma testi
     */

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

    /*
     * createGoogleUser validation testleri
     */

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void createGoogleUser_shouldThrowException_whenUsernameIsNullOrBlank(
            String invalidUsername
    ) {
        assertThatThrownBy(
                () -> userService.createGoogleUser(
                        GOOGLE_SUB,
                        EMAIL,
                        invalidUsername
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username cannot be empty.");

        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void createGoogleUser_shouldThrowException_whenGoogleSubIsNullOrBlank(
            String invalidGoogleSub
    ) {
        assertThatThrownBy(
                () -> userService.createGoogleUser(
                        invalidGoogleSub,
                        EMAIL,
                        USERNAME
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Google subject cannot be empty.");

        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    void createGoogleUser_shouldThrowException_whenEmailIsNullOrBlank(
            String invalidEmail
    ) {
        assertThatThrownBy(
                () -> userService.createGoogleUser(
                        GOOGLE_SUB,
                        invalidEmail,
                        USERNAME
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email cannot be empty.");

        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ab",
            "bengu yilmaz",
            "bengu!",
            "username_that_is_longer_than_fifty_characters_123456789"
    })
    void createGoogleUser_shouldThrowException_whenUsernameFormatIsInvalid(
            String invalidUsername
    ) {
        assertThatThrownBy(
                () -> userService.createGoogleUser(
                        GOOGLE_SUB,
                        EMAIL,
                        invalidUsername
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Username must be between 3 and 50 characters and may contain letters, numbers, dots, underscores, and hyphens."
                );

        verifyNoInteractions(userRepository);
    }

    /*
     * createGoogleUser duplicate kayıt testleri
     */

    @Test
    void createGoogleUser_shouldThrowException_whenGoogleAccountAlreadyExists() {
        when(userRepository.existsByGoogleSub(GOOGLE_SUB))
                .thenReturn(true);

        assertThatThrownBy(
                () -> userService.createGoogleUser(
                        GOOGLE_SUB,
                        EMAIL,
                        USERNAME
                )
        )
                .isInstanceOf(
                        GoogleAccountAlreadyLinkedException.class
                );

        verify(userRepository).existsByGoogleSub(GOOGLE_SUB);

        verify(userRepository, never())
                .existsByEmail(any());

        verify(userRepository, never())
                .existsByUsername(any());

        verify(userRepository, never())
                .save(any());
    }

    @Test
    void createGoogleUser_shouldThrowException_whenEmailAlreadyExists() {
        when(userRepository.existsByGoogleSub(GOOGLE_SUB))
                .thenReturn(false);

        when(userRepository.existsByEmail(EMAIL))
                .thenReturn(true);

        assertThatThrownBy(
                () -> userService.createGoogleUser(
                        GOOGLE_SUB,
                        EMAIL,
                        USERNAME
                )
        )
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository).existsByGoogleSub(GOOGLE_SUB);
        verify(userRepository).existsByEmail(EMAIL);

        verify(userRepository, never())
                .existsByUsername(any());

        verify(userRepository, never())
                .save(any());
    }

    @Test
    void createGoogleUser_shouldThrowException_whenUsernameAlreadyExists() {
        when(userRepository.existsByGoogleSub(GOOGLE_SUB))
                .thenReturn(false);

        when(userRepository.existsByEmail(EMAIL))
                .thenReturn(false);

        when(userRepository.existsByUsername(USERNAME))
                .thenReturn(true);

        assertThatThrownBy(
                () -> userService.createGoogleUser(
                        GOOGLE_SUB,
                        EMAIL,
                        USERNAME
                )
        )
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(userRepository).existsByGoogleSub(GOOGLE_SUB);
        verify(userRepository).existsByEmail(EMAIL);
        verify(userRepository).existsByUsername(USERNAME);

        verify(userRepository, never())
                .save(any());
    }

    /*
     * createGoogleUser başarılı senaryo
     */

    @Test
    void createGoogleUser_shouldSaveUser_whenInformationIsValid() {
        when(userRepository.existsByGoogleSub(GOOGLE_SUB))
                .thenReturn(false);

        when(userRepository.existsByEmail(EMAIL))
                .thenReturn(false);

        when(userRepository.existsByUsername(USERNAME))
                .thenReturn(false);

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createGoogleUser(
                GOOGLE_SUB,
                EMAIL,
                "  bengu  "
        );

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User userGivenToRepository = userCaptor.getValue();

        assertThat(userGivenToRepository.getUsername())
                .isEqualTo(USERNAME);

        assertThat(userGivenToRepository.getEmail())
                .isEqualTo(EMAIL);

        assertThat(userGivenToRepository.getGoogleSub())
                .isEqualTo(GOOGLE_SUB);

        assertThat(userGivenToRepository.getPasswordHash())
                .isNull();

        assertThat(result).isSameAs(userGivenToRepository);

        verifyNoInteractions(passwordEncoder);
    }
    @Test
    void createUser_shouldThrowException_whenUsernameAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("bengu");
        request.setEmail("bengu@example.com");
        request.setPassword("plain-password");

        when(userRepository.findByUsername("bengu"))
                .thenReturn(Optional.of(mock(User.class)));

        assertThatThrownBy(
                () -> userService.createUser(request)
        )
                .isInstanceOf(
                        UsernameAlreadyExistsException.class
                );

        verifyNoInteractions(passwordEncoder);

        verify(userRepository, never())
                .save(any(User.class));
    }
    @Test
    void createUser_shouldThrowException_whenEmailAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("bengu");
        request.setEmail("bengu@example.com");
        request.setPassword("plain-password");

        when(userRepository.findByUsername("bengu"))
                .thenReturn(Optional.empty());

        when(userRepository.existsByEmail("bengu@example.com"))
                .thenReturn(true);

        assertThatThrownBy(
                () -> userService.createUser(request)
        )
                .isInstanceOf(EmailAlreadyExistsException.class);

        verifyNoInteractions(passwordEncoder);

        verify(userRepository, never())
                .save(any(User.class));
    }
    @Test
    void findByGoogleSub_shouldReturnUser_whenUserExists() {
        User user = mock(User.class);

        when(userRepository.findByGoogleSub("google-123"))
                .thenReturn(Optional.of(user));

        Optional<User> result =
                userService.findByGoogleSub("google-123");

        assertThat(result).containsSame(user);

        verify(userRepository)
                .findByGoogleSub("google-123");
    }
    @Test
    void findByGoogleSub_shouldReturnEmpty_whenUserDoesNotExist() {
        when(userRepository.findByGoogleSub("google-123"))
                .thenReturn(Optional.empty());

        Optional<User> result =
                userService.findByGoogleSub("google-123");

        assertThat(result).isEmpty();

        verify(userRepository)
                .findByGoogleSub("google-123");
    }
    @Test
    void suggestUsername_shouldCreateSuggestionFromEmail() {
        when(userRepository.existsByUsername("bengu"))
                .thenReturn(false);

        String result =
                userService.suggestUsername("Bengu@example.com");

        assertThat(result).isEqualTo("bengu");

        verify(userRepository)
                .existsByUsername("bengu");
    }
    @Test
    void suggestUsername_shouldRemoveInvalidCharacters() {
        when(userRepository.existsByUsername("benguyilmaz"))
                .thenReturn(false);

        String result = userService.suggestUsername(
                "Bengu+Yilmaz@example.com"
        );

        assertThat(result).isEqualTo("benguyilmaz");
    }
    @Test
    void suggestUsername_shouldAddNumber_whenSuggestionsAreTaken() {
        when(userRepository.existsByUsername("bengu"))
                .thenReturn(true);

        when(userRepository.existsByUsername("bengu_1"))
                .thenReturn(true);

        when(userRepository.existsByUsername("bengu_2"))
                .thenReturn(false);

        String result =
                userService.suggestUsername("bengu@example.com");

        assertThat(result).isEqualTo("bengu_2");

        verify(userRepository)
                .existsByUsername("bengu");

        verify(userRepository)
                .existsByUsername("bengu_1");

        verify(userRepository)
                .existsByUsername("bengu_2");
    }
    @Test
    void suggestUsername_shouldUseUser_whenEmailPrefixBecomesEmpty() {
        when(userRepository.existsByUsername("user"))
                .thenReturn(false);

        String result =
                userService.suggestUsername("!!!@example.com");

        assertThat(result).isEqualTo("user");
    }
    @Test
    void suggestUsername_shouldLimitSuggestionToFiftyCharacters() {
        String longPrefix = "a".repeat(60);
        String expectedUsername = "a".repeat(50);

        when(userRepository.existsByUsername(expectedUsername))
                .thenReturn(false);

        String result = userService.suggestUsername(
                longPrefix + "@example.com"
        );

        assertThat(result)
                .hasSize(50)
                .isEqualTo(expectedUsername);
    }
    @Test
    void suggestUsername_shouldKeepFiftyCharacterLimit_whenAddingSuffix() {
        String longPrefix = "a".repeat(60);
        String baseUsername = "a".repeat(50);
        String expectedUsername = "a".repeat(48) + "_1";

        when(userRepository.existsByUsername(baseUsername))
                .thenReturn(true);

        when(userRepository.existsByUsername(expectedUsername))
                .thenReturn(false);

        String result = userService.suggestUsername(
                longPrefix + "@example.com"
        );

        assertThat(result)
                .hasSize(50)
                .isEqualTo(expectedUsername);
    }
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid-email"})
    void suggestUsername_shouldThrowException_whenEmailIsInvalid(
            String invalidEmail
    ) {
        assertThatThrownBy(
                () -> userService.suggestUsername(invalidEmail)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email is invalid.");

        verifyNoInteractions(userRepository);
    }
    @Test
    void getUserByUsername_shouldReturnRepositoryResult() {
        User user = mock(User.class);

        when(userRepository.findByUsername("bengu"))
                .thenReturn(Optional.of(user));

        Optional<User> result =
                userService.getUserByUsername("bengu");

        assertThat(result).containsSame(user);

        verify(userRepository).findByUsername("bengu");
    }
    @Test
    void getUserById_shouldReturnRepositoryResult() {
        User user = mock(User.class);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        Optional<User> result =
                userService.getUserById(1L);

        assertThat(result).containsSame(user);

        verify(userRepository).findById(1L);
    }
    @Test
    void getAllUsers_shouldReturnAllRepositoryUsers() {
        User firstUser = mock(User.class);
        User secondUser = mock(User.class);

        List<User> users = List.of(
                firstUser,
                secondUser
        );

        when(userRepository.findAll())
                .thenReturn(users);

        List<User> result = userService.getAllUsers();

        assertThat(result)
                .containsExactly(firstUser, secondUser);

        verify(userRepository).findAll();
    }
}