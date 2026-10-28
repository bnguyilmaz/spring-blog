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

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

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
        this.verifiedAt = null;
    }

    public boolean isVerified() { return verifiedAt != null; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }

    public void verify(String username) {
        this.lastFmUsername = username;
        this.verifiedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }
}
