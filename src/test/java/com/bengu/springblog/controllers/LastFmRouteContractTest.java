package com.bengu.springblog.controllers;

import com.bengu.springblog.config.SecurityConfig;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.services.LastFmProfileService;
import com.bengu.springblog.services.LastFmService;
import com.bengu.springblog.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ProfileController.class, MusicController.class}, properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@Import({SecurityConfig.class, WebSecurityTestConfig.class})
class LastFmRouteContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService users;
    @MockitoBean LastFmService lastFm;
    @MockitoBean LastFmProfileService profiles;
    @MockitoBean com.bengu.springblog.services.LastFmAuthorizationService authorization;

    @Test
    void accountSettingsOffersAuthorizationInsteadOfManualUsername() throws Exception {
        when(users.findByGoogleSub("user")).thenReturn(Optional.of(new User("alice", "a@example.com", null)));
        mvc.perform(get("/profile/lastfm").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/lastfm/connect")))
                .andExpect(content().string(not(containsString("name=\"lastFmUsername\""))));
    }

    @Test
    void manualUsernameCanNoLongerBypassAuthorization() throws Exception {
        when(users.findByGoogleSub("user")).thenReturn(Optional.of(new User("alice", "a@example.com", null)));
        mvc.perform(post("/profile/lastfm").with(oidcLogin()).with(csrf()).param("lastFmUsername", "someone-else"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void anotherApplicationProfileCanBeVisited() throws Exception {
        when(users.getUserByUsername("bob")).thenReturn(Optional.of(new User("bob", "private@example.com", null)));
        mvc.perform(get("/users/bob").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bob")))
                .andExpect(content().string(not(containsString("private@example.com"))));
    }

    @Test
    void anotherUsersMissingLastFmDoesNotRedirectVisitorToOwnSettings() throws Exception {
        when(users.getUserByUsername("bob")).thenReturn(Optional.of(new User("bob", "private@example.com", null)));
        mvc.perform(get("/users/bob/music").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("henüz Last.fm hesabını bağlamamış")));
    }
}
