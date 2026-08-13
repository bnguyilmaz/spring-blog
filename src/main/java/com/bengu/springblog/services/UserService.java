package com.bengu.springblog.services;

import com.bengu.springblog.dto.CreateUserRequest;
import com.bengu.springblog.dto.UserResponse;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.exceptions.EmailAlreadyExistsException;
import com.bengu.springblog.exceptions.GoogleAccountAlreadyLinkedException;
import com.bengu.springblog.exceptions.UsernameAlreadyExistsException;
import com.bengu.springblog.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse createUser(CreateUserRequest request) {
        Optional<User> existingUser =
                userRepository.findByUsername(request.getUsername());

        if (existingUser.isPresent()) {
            throw new UsernameAlreadyExistsException(
                    request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(
                    request.getEmail()
            );
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = new User(request.getUsername(),request.getEmail(),passwordHash);
        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Optional<User> findByGoogleSub(String googleSub) {
        return userRepository.findByGoogleSub(googleSub);
    }
    @Transactional(readOnly = true)
    public String suggestUsername(String email) {

        if (email == null
                || email.isBlank()
                || !email.contains("@")) {
            throw new IllegalArgumentException(
                    "Email is invalid."
            );
        }

        String baseUsername = email
                .substring(0, email.indexOf("@"))
                .replaceAll("[^a-zA-Z0-9._-]", "")
                .toLowerCase();

        if (baseUsername.isBlank()) {
            baseUsername = "user";
        }

        if (baseUsername.length() > 50) {
            baseUsername = baseUsername.substring(0, 50);
        }

        String suggestion = baseUsername;
        int number = 1;

        while (userRepository.existsByUsername(suggestion)) {
            String suffix = "_" + number;
            int maximumBaseLength = 50 - suffix.length();

            String shortenedBase = baseUsername.substring(
                    0,
                    Math.min(baseUsername.length(), maximumBaseLength)
            );

            suggestion = shortenedBase + suffix;
            number++;
        }

        return suggestion;
    }
    @Transactional
    public User createGoogleUser(
            String googleSub,
            String email,
            String selectedUsername
    ) {

        if (selectedUsername == null || selectedUsername.isBlank()) {
            throw new IllegalArgumentException(
                    "Username cannot be empty."
            );
        }

        String username = selectedUsername.trim();


        if (googleSub == null || googleSub.isBlank()) {
            throw new IllegalArgumentException(
                    "Google subject cannot be empty."
            );
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email cannot be empty."
            );
        }

        if (!username.matches("^[a-zA-Z0-9._-]{3,50}$")) {
            throw new IllegalArgumentException(
                    "Username must be between 3 and 50 characters and may contain letters, numbers, dots, underscores, and hyphens."
            );
        }

        if (userRepository.existsByGoogleSub(googleSub)) {
            throw new GoogleAccountAlreadyLinkedException();
        }

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }

        User user = new User(
                username,
                email,
                null
        );

        user.setGoogleSub(googleSub);

        return userRepository.save(user);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}