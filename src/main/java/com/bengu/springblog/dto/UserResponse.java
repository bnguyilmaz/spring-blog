package com.bengu.springblog.dto;

import java.time.LocalDateTime;

public class UserResponse {

    private Long id;
    private String username;
    private LocalDateTime createdAt;

    public UserResponse(
            Long id,
            String username,
            LocalDateTime createdAt) {

        this.id = id;
        this.username = username;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}