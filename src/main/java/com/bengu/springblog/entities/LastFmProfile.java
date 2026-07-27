package com.bengu.springblog.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lastfm_profiles")
public class LastFmProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "lastfm_username", nullable = false, length = 50)
    private String lastFmUsername;

    @Column(name = "connected_at", nullable = false, updatable = false)
    private LocalDateTime connectedAt = LocalDateTime.now();

    protected LastFmProfile() {
    }

    public LastFmProfile(User user, String lastFmUsername) {
        this.user = user;
        this.lastFmUsername = lastFmUsername;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getLastFmUsername() {
        return lastFmUsername;
    }

    public void setLastFmUsername(String lastFmUsername) {
        this.lastFmUsername = lastFmUsername;
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }
}