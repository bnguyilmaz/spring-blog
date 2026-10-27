package com.bengu.springblog.controllers;

import com.bengu.springblog.config.SecurityConfig;
import com.bengu.springblog.entities.LastFmProfile;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.exceptions.LastFmException;
import com.bengu.springblog.repositories.LastFmProfileRepository;
import com.bengu.springblog.services.LastFmProfileService;
import com.bengu.springblog.services.LastFmService;
import com.bengu.springblog.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {MusicController.class, ProfileController.class}, properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@Import({SecurityConfig.class, WebSecurityTestConfig.class, LastFmProfileService.class})
class MusicProfileTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService users;
    @MockitoBean LastFmProfileRepository profiles;
    @MockitoBean LastFmService lastFm;

    @Test
    void unregisteredGoogleUserReturnsToOnboarding() throws Exception {
        mvc.perform(get("/profile/lastfm").with(oidcLogin()))
                .andExpect(redirectedUrl("/oauth-user"));
    }

    @Test
    void musicWithoutLastFmShowsSetup() throws Exception {
        mvc.perform(get("/music").with(oidcLogin()))
                .andExpect(redirectedUrl("/profile/lastfm"));
        verifyNoInteractions(lastFm);
    }

    @Test
    void eachAccountSeesOnlyItsOwnMusicEvenWithAnotherUsernameInQuery() throws Exception {
        for (String account : List.of("alice", "bob")) {
            when(profiles.findByUser_GoogleSub(account)).thenReturn(Optional.of(
                    new LastFmProfile(new User(account, account + "@example.com", null), account + "-fm")));
            when(lastFm.getRecentTracks(account + "-fm")).thenReturn(List.of());
            when(lastFm.getTopArtists(account + "-fm")).thenReturn(List.of());
            when(lastFm.getTopAlbums(account + "-fm")).thenReturn(List.of());
            mvc.perform(get("/music").param("user", "someone-else")
                            .with(oidcLogin().idToken(t -> t.subject(account))))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("lastFmUsername", account + "-fm"))
                    .andExpect(content().string(containsString(account + "-fm")))
                    .andExpect(content().string(containsString("Henüz dinleme kaydı yok")));
            verify(lastFm).getRecentTracks(account + "-fm");
        }
        verify(lastFm, never()).getRecentTracks("someone-else");
    }

    @Test
    void savesLastFmAgainstAuthenticatedAccountAndOpensMusic() throws Exception {
        User alice = new User("alice", "alice@example.com", null);
        when(users.findByGoogleSub("alice-sub")).thenReturn(Optional.of(alice));
        when(lastFm.verifyUsername("alice-fm")).thenReturn("Alice-FM");
        mvc.perform(post("/profile/lastfm").with(csrf())
                        .with(oidcLogin().idToken(t -> t.subject("alice-sub")))
                        .param("lastFmUsername", " alice-fm ").param("userId", "999"))
                .andExpect(redirectedUrl("/music"));
        verify(profiles).save(argThat(p -> p.getUser() == alice && p.getLastFmUsername().equals("Alice-FM")));
    }

    @Test
    void changingLastFmUpdatesExistingProfile() throws Exception {
        User alice = new User("alice", "alice@example.com", null);
        LastFmProfile profile = new LastFmProfile(alice, "old-fm");
        when(users.findByGoogleSub("alice-sub")).thenReturn(Optional.of(alice));
        when(profiles.findByUser_GoogleSub("alice-sub")).thenReturn(Optional.of(profile));
        when(lastFm.verifyUsername("new-fm")).thenReturn("new-fm");
        mvc.perform(post("/profile/lastfm").with(csrf())
                        .with(oidcLogin().idToken(t -> t.subject("alice-sub")))
                        .param("lastFmUsername", "new-fm"))
                .andExpect(redirectedUrl("/music"));
        verify(profiles).save(profile);
        org.assertj.core.api.Assertions.assertThat(profile.getLastFmUsername()).isEqualTo("new-fm");
    }

    @Test
    void invalidUsernameKeepsFormAndDoesNotSave() throws Exception {
        when(users.findByGoogleSub("user")).thenReturn(Optional.of(new User("alice", "a@example.com", null)));
        when(lastFm.verifyUsername("missing")).thenThrow(new IllegalArgumentException("Last.fm kullanıcısı bulunamadı."));
        mvc.perform(post("/profile/lastfm").with(csrf()).with(oidcLogin())
                        .param("lastFmUsername", "missing"))
                .andExpect(status().isOk())
                .andExpect(view().name("lastfm-settings"))
                .andExpect(model().attributeExists("lastFmError"))
                .andExpect(model().attribute("lastFmUsername", "missing"));
        verify(profiles, never()).save(any());
    }

    @Test
    void blankUsernameDoesNotCallLastFmOrSave() throws Exception {
        when(users.findByGoogleSub("user")).thenReturn(Optional.of(new User("alice", "a@example.com", null)));
        mvc.perform(post("/profile/lastfm").with(csrf()).with(oidcLogin()).param("lastFmUsername", " "))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("lastFmError"));
        verifyNoInteractions(lastFm);
        verify(profiles, never()).save(any());
    }

    @Test
    void lastFmOutageKeepsExistingAccountAndShowsRetry() throws Exception {
        when(profiles.findByUser_GoogleSub("user")).thenReturn(Optional.of(
                new LastFmProfile(new User("alice", "a@example.com", null), "alice-fm")));
        when(lastFm.getRecentTracks("alice-fm")).thenThrow(new LastFmException());
        mvc.perform(get("/music").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("musicError"))
                .andExpect(content().string(containsString("Tekrar dene")));
    }

    @Test
    void savingRequiresCsrf() throws Exception {
        mvc.perform(post("/profile/lastfm").with(oidcLogin()).param("lastFmUsername", "alice"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles, lastFm);
    }
}
