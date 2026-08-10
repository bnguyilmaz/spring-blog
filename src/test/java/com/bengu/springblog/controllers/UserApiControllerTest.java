package com.bengu.springblog.controllers;

import com.bengu.springblog.dto.CreateUserRequest;
import com.bengu.springblog.dto.UserResponse;
import com.bengu.springblog.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserApiController.class)
class UserApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void createUser_shouldReturn201AndCreatedUser() throws Exception {
        UserResponse createdUser = new UserResponse(
                1L,
                "bengu",
                LocalDateTime.of(2026, 8, 3, 12, 0)
        );

        when(userService.createUser(any(CreateUserRequest.class)))
                .thenReturn(createdUser);

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "bengu",
                                          "email": "bengu@example.com",
                                          "password": "plain-password"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("bengu"))
                .andExpect(jsonPath("$.createdAt")
                        .value("2026-08-03T12:00:00"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService)
                .createUser(any(CreateUserRequest.class));
    }
}