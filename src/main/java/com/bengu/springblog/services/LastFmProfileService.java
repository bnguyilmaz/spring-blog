package com.bengu.springblog.services;

import com.bengu.springblog.entities.LastFmProfile;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.repositories.LastFmProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
public class LastFmProfileService {
    private final LastFmProfileRepository profiles;
    private final UserService users;

    public LastFmProfileService(LastFmProfileRepository profiles, UserService users) {
        this.profiles = profiles;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<String> usernameFor(String googleSub) {
        return profiles.findByUser_GoogleSub(googleSub)
                .filter(LastFmProfile::isVerified).map(LastFmProfile::getLastFmUsername);
    }

    @Transactional(readOnly = true)
    public Optional<String> usernameForUser(String username) {
        return profiles.findByUser_Username(username)
                .filter(LastFmProfile::isVerified).map(LastFmProfile::getLastFmUsername);
    }

    @Transactional
    public void connectVerified(String googleSub, String username) {
        if (username == null || username.isBlank() || username.length() > 50) {
            throw new IllegalArgumentException("Last.fm kullanıcı adı geçersiz.");
        }
        User user = users.findByGoogleSub(googleSub)
                .orElseThrow(() -> new IllegalStateException("Önce profilini tamamlamalısın."));
        LastFmProfile profile = profiles.findByUser_GoogleSub(googleSub)
                .orElseGet(() -> new LastFmProfile(user, username));
        profile.verify(username);
        profiles.save(profile);
    }
}
