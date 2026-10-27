package com.bengu.springblog.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bengu.springblog.entities.LastFmProfile;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.repositories.LastFmProfileRepository;
import java.util.Optional;

@Service
public class LastFmProfileService {
    private final LastFmProfileRepository profiles;
    private final UserService users;
    private final LastFmService lastFm;

    public LastFmProfileService(LastFmProfileRepository profiles, UserService users, LastFmService lastFm) {
        this.profiles = profiles;
        this.users = users;
        this.lastFm = lastFm;
    }

    @Transactional(readOnly = true)
    public Optional<String> usernameFor(String googleSub) {
        return profiles.findByUser_GoogleSub(googleSub).map(LastFmProfile::getLastFmUsername);
    }

    @Transactional
    public void connect(String googleSub, String username) {
        if (username == null || !username.trim().matches("[A-Za-z0-9._-]{1,50}")) {
            throw new IllegalArgumentException("Geçerli bir Last.fm kullanıcı adı gir (en fazla 50 karakter). Profil bağlantısı yerine kullanıcı adını yaz.");
        }
        User user = users.findByGoogleSub(googleSub)
                .orElseThrow(() -> new IllegalStateException("Önce profilini tamamlamalısın."));
        String canonicalUsername = lastFm.verifyUsername(username.trim());
        LastFmProfile profile = profiles.findByUser_GoogleSub(googleSub)
                .orElseGet(() -> new LastFmProfile(user, canonicalUsername));
        profile.setLastFmUsername(canonicalUsername);
        profiles.save(profile);
    }
}
