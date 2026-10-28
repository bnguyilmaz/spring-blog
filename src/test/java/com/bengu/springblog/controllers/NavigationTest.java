package com.bengu.springblog.controllers;

import com.bengu.springblog.config.SecurityConfig;
import com.bengu.springblog.entities.User;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {HomeController.class, AuthController.class, MusicController.class}, properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@Import({SecurityConfig.class, WebSecurityTestConfig.class})
class NavigationTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService users;
    @MockitoBean LastFmService lastFm;
    @MockitoBean com.bengu.springblog.services.LastFmProfileService profiles;

    @Test
    void rootDisplaysHome() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"));
    }

    @Test
    void musicRequiresLogin() throws Exception {
        mvc.perform(get("/music"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/google"));
    }

    @Test
    void profileOffersLastFmSetupAndHomeNavigation() throws Exception {
        when(users.findByGoogleSub("account-a"))
                .thenReturn(Optional.of(new User("alice", "alice@example.com", null)));
        mvc.perform(get("/oauth-user").with(oidcLogin().idToken(t -> t.subject("account-a"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/profile/lastfm")))
                .andExpect(content().string(containsString("/lastfm/connect")))
                .andExpect(content().string(containsString("Ana sayfa")));
    }
}
