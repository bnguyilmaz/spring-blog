package com.bengu.springblog.controllers;

import com.bengu.springblog.dto.CreateUserRequest;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserApiController {

    private final UserService userService;

    public UserApiController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<User> createUser(
            @RequestBody CreateUserRequest request) {

        User createdUser = userService.createUser(request);


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdUser);
    }
}