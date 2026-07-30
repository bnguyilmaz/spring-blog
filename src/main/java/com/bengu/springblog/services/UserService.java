package com.bengu.springblog.services;

import com.bengu.springblog.dto.CreateUserRequest;
import com.bengu.springblog.dto.UserResponse;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.exceptions.UsernameAlreadyExistsException;
import com.bengu.springblog.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
                    "Username is already taken: "
                            + request.getUsername()
            );
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User savedUser = new User(request.getUsername(),request.getEmail(),passwordHash);
        userRepository.save(savedUser);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getCreatedAt()
        );
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