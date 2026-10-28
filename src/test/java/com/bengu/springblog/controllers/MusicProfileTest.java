package com.bengu.springblog.controllers;

import com.bengu.springblog.config.SecurityConfig;
import com.bengu.springblog.entities.LastFmProfile;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.exceptions.LastFmException;
import com.bengu.springblog.repositories.LastFmProfileRepository;
import com.bengu.springblog.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@WebMvcTest(controllers = {MusicController.class, ProfileController.class}, properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "lastfm.api.key=test-lastfm-key", "lastfm.api.secret=test-lastfm-secret",
        "lastfm.base.url=https://example.com/2.0/",
        "lastfm.callback.url=http://localhost:8084/lastfm/callback"
})
@Import({SecurityConfig.class, WebSecurityTestConfig.class, LastFmProfileService.class, LastFmAuthorizationService.class})
class MusicProfileTest {
    static final String TOKEN = "GHIJKLMNOPQRSTUVWXYZghijklmnopqr";
    @Autowired MockMvc mvc;
    @Autowired LastFmAuthorizationService authorization;
    @MockitoBean UserService users;
    @MockitoBean LastFmProfileRepository profiles;
    @MockitoBean LastFmService lastFm;
    MockRestServiceServer server;

    @BeforeEach
    void setup() {
        when(users.findByGoogleSub("user")).thenReturn(Optional.of(user("alice", "user")));
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(authorization, "restTemplate")).build();
    }

    @Test
    void unregisteredGoogleUserReturnsToOnboarding() throws Exception {
        when(users.findByGoogleSub("user")).thenReturn(Optional.empty());
        mvc.perform(get("/profile/lastfm").with(oidcLogin())).andExpect(redirectedUrl("/oauth-user"));
        mvc.perform(post("/lastfm/connect").with(oidcLogin()).with(csrf())).andExpect(redirectedUrl("/oauth-user"));
    }

    @Test
    void ownMusicWithoutVerifiedLastFmShowsSetupWithoutCallingLastFm() throws Exception {
        mvc.perform(get("/music").with(oidcLogin())).andExpect(redirectedUrl("/profile/lastfm"));
        verifyNoInteractions(lastFm);
    }

    @Test
    void legacyManuallyEnteredNameRequiresAuthorization() throws Exception {
        when(profiles.findByUser_GoogleSub("user")).thenReturn(Optional.of(new LastFmProfile(user("alice", "user"), "legacy-name")));
        mvc.perform(get("/music").with(oidcLogin())).andExpect(redirectedUrl("/profile/lastfm"));
        verifyNoInteractions(lastFm);
    }

    @Test
    void ownMusicIgnoresAnotherUsernameInQuery() throws Exception {
        when(profiles.findByUser_GoogleSub("user")).thenReturn(Optional.of(verified("alice", "user", "alice-fm")));
        mvc.perform(get("/music").param("user", "bob-fm").with(oidcLogin()))
                .andExpect(status().isOk()).andExpect(model().attribute("lastFmUsername", "alice-fm"));
        verify(lastFm).getRecentTracks("alice-fm");
        verify(lastFm, never()).getRecentTracks("bob-fm");
    }

    @Test
    void viewerWithoutLastFmCanSeeAnotherUsersVerifiedMusic() throws Exception {
        when(users.getUserByUsername("bob")).thenReturn(Optional.of(user("bob", "bob-google")));
        when(profiles.findByUser_Username("bob")).thenReturn(Optional.of(verified("bob", "bob-google", "bobs-real-fm")));
        mvc.perform(get("/users/bob/music").with(oidcLogin()).param("user", "alice-fm"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("profileUsername", "bob"))
                .andExpect(model().attribute("lastFmUsername", "bobs-real-fm"))
                .andExpect(model().attribute("ownProfile", false))
                .andExpect(content().string(not(containsString("Last.fm hesabını değiştir"))));
        verify(lastFm).getRecentTracks("bobs-real-fm");
        verify(profiles, never()).findByUser_GoogleSub("user");
    }

    @Test
    void missingLastFmShowsTargetProfileStateInsteadOfOwnConnectionForm() throws Exception {
        when(users.getUserByUsername("bob")).thenReturn(Optional.of(user("bob", "bob-google")));
        mvc.perform(get("/users/bob/music").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("henüz Last.fm hesabını bağlamamış")))
                .andExpect(content().string(not(containsString("action=\"/lastfm/connect\""))));
        verifyNoInteractions(lastFm);
    }

    @Test
    void unverifiedLegacyNameIsNotExposedToVisitors() throws Exception {
        when(users.getUserByUsername("bob")).thenReturn(Optional.of(user("bob", "bob-google")));
        when(profiles.findByUser_Username("bob")).thenReturn(Optional.of(new LastFmProfile(user("bob", "bob-google"), "claimed-name")));
        mvc.perform(get("/users/bob/music").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("claimed-name"))));
        verifyNoInteractions(lastFm);
    }

    @Test
    void visitingProfileDoesNotExposeUserEntityEmailOrGoogleIdentity() throws Exception {
        User bob = user("bob", "private-google-sub");
        when(users.getUserByUsername("bob")).thenReturn(Optional.of(bob));
        when(profiles.findByUser_Username("bob")).thenReturn(Optional.of(verified("bob", "private-google-sub", "bob-fm")));
        mvc.perform(get("/users/bob").with(oidcLogin()))
                .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("user"))
                .andExpect(content().string(containsString("/users/bob/music")))
                .andExpect(content().string(not(containsString(bob.getEmail()))))
                .andExpect(content().string(not(containsString("private-google-sub"))))
                .andExpect(content().string(not(containsString("action=\"/lastfm/connect\""))));
    }

    @Test
    void unknownProfileAndMusicReturn404() throws Exception {
        mvc.perform(get("/users/unknown").with(oidcLogin())).andExpect(status().isNotFound());
        mvc.perform(get("/users/unknown/music").with(oidcLogin())).andExpect(status().isNotFound());
    }

    @Test
    void profilePagesAndAuthorizationRequireLogin() throws Exception {
        for (String path : List.of("/users", "/users/bob", "/users/bob/music", "/profile/lastfm", "/lastfm/callback")) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/oauth2/authorization/google"));
        }
    }

    @Test
    void discoverPageFindsApplicationUsername() throws Exception {
        mvc.perform(get("/users").with(oidcLogin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Kullanıcı adı")));
        mvc.perform(get("/users").param("username", " bob ").with(oidcLogin())).andExpect(redirectedUrl("/users/bob"));
    }

    @Test
    void profileSearchDoesNotAcceptPathsOrUrlsAsUsernames() throws Exception {
        for (String name : List.of("../../profile/lastfm", "https://example.com", "bob/other", "a".repeat(51))) {
            mvc.perform(get("/users").with(oidcLogin()).param("username", name)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void authorizationRequiresCsrfAndDoesNotAllowGetStart() throws Exception {
        mvc.perform(post("/lastfm/connect").with(oidcLogin())).andExpect(status().isForbidden());
        mvc.perform(get("/lastfm/connect").with(oidcLogin())).andExpect(status().isMethodNotAllowed());
        server.verify();
    }

    @Test
    void callbackConnectsOnlyAuthorizedAccountToInitiatingUser() throws Exception {
        MockHttpSession browser = new MockHttpSession();
        String state = start(browser);
        server.expect(queryParam("method", "auth.getSession")).andRespond(withSuccess(
                "{\"session\":{\"name\":\"VerifiedAlice\",\"key\":\"secret-session-key\",\"subscriber\":0}}", MediaType.APPLICATION_JSON));
        mvc.perform(get("/lastfm/callback").session(browser).with(oidcLogin())
                        .param("state", state).param("token", TOKEN).param("username", "mallory").param("userId", "999"))
                .andExpect(redirectedUrl("/music"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        verify(profiles).save(argThat(p -> p.getUser().getUsername().equals("alice")
                && p.getLastFmUsername().equals("VerifiedAlice") && p.isVerified()));
        server.verify();
    }

    @Test
    void callbackCannotBeRepeated() throws Exception {
        MockHttpSession browser = new MockHttpSession();
        String state = start(browser);
        server.expect(queryParam("method", "auth.getSession")).andRespond(withSuccess(
                "{\"session\":{\"name\":\"VerifiedAlice\",\"key\":\"secret-session-key\"}}", MediaType.APPLICATION_JSON));
        mvc.perform(get("/lastfm/callback").session(browser).with(oidcLogin()).param("state", state).param("token", TOKEN))
                .andExpect(redirectedUrl("/music"));
        mvc.perform(get("/lastfm/callback").session(browser).with(oidcLogin()).param("state", state).param("token", TOKEN))
                .andExpect(redirectedUrl("/profile/lastfm")).andExpect(flash().attributeExists("lastFmError"));
        verify(profiles, times(1)).save(any());
        server.verify();
    }

    @Test
    void callbackWithNoPriorRequestCannotLinkAnAccount() throws Exception {
        mvc.perform(get("/lastfm/callback").with(oidcLogin()).param("token", TOKEN).param("state", "forged"))
                .andExpect(redirectedUrl("/profile/lastfm")).andExpect(flash().attributeExists("lastFmError"));
        verify(profiles, never()).save(any());
        server.verify();
    }

    @Test
    void changedGoogleAccountCannotReceiveOldAuthorization() throws Exception {
        MockHttpSession browser = new MockHttpSession();
        String state = start(browser);
        mvc.perform(get("/lastfm/callback").session(browser).with(oidcLogin().idToken(t -> t.subject("different-account")))
                        .param("state", state).param("token", TOKEN))
                .andExpect(redirectedUrl("/profile/lastfm")).andExpect(flash().attributeExists("lastFmError"));
        verify(profiles, never()).save(any());
        server.verify();
    }

    @Test
    void declinedOrIncompleteAuthorizationShowsRestartMessage() throws Exception {
        MockHttpSession browser = new MockHttpSession();
        String state = start(browser);
        mvc.perform(get("/lastfm/callback").session(browser).with(oidcLogin()).param("state", state))
                .andExpect(redirectedUrl("/profile/lastfm")).andExpect(flash().attributeExists("lastFmError"));
        verify(profiles, never()).save(any());
    }

    @Test
    void failedAuthorizationPreservesOldVerifiedAccount() throws Exception {
        LastFmProfile existing = verified("alice", "user", "old-account");
        when(profiles.findByUser_GoogleSub("user")).thenReturn(Optional.of(existing));
        MockHttpSession browser = new MockHttpSession();
        String state = start(browser);
        server.expect(queryParam("method", "auth.getSession")).andRespond(withServerError());
        mvc.perform(get("/lastfm/callback").session(browser).with(oidcLogin()).param("state", state).param("token", TOKEN))
                .andExpect(redirectedUrl("/profile/lastfm")).andExpect(flash().attributeExists("lastFmError"));
        assertThat(existing.getLastFmUsername()).isEqualTo("old-account");
        assertThat(existing.isVerified()).isTrue();
        verify(profiles, never()).save(any());
    }

    @Test
    void authorizationUpdatesExistingProfileInsteadOfCreatingAnother() throws Exception {
        LastFmProfile existing = verified("alice", "user", "old-account");
        when(profiles.findByUser_GoogleSub("user")).thenReturn(Optional.of(existing));
        MockHttpSession browser = new MockHttpSession();
        String state = start(browser);
        server.expect(queryParam("method", "auth.getSession")).andRespond(withSuccess(
                "{\"session\":{\"name\":\"NewAccount\",\"key\":\"session-key\"}}", MediaType.APPLICATION_JSON));
        mvc.perform(get("/lastfm/callback").session(browser).with(oidcLogin()).param("state", state).param("token", TOKEN))
                .andExpect(redirectedUrl("/music"));
        verify(profiles).save(existing);
        assertThat(existing.getLastFmUsername()).isEqualTo("NewAccount");
    }

    @Test
    void visitorMusicErrorRetainsTargetInRetryLink() throws Exception {
        when(users.getUserByUsername("bob")).thenReturn(Optional.of(user("bob", "bob-google")));
        when(profiles.findByUser_Username("bob")).thenReturn(Optional.of(verified("bob", "bob-google", "bob-fm")));
        when(lastFm.getRecentTracks("bob-fm")).thenThrow(new LastFmException());
        mvc.perform(get("/users/bob/music").with(oidcLogin()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("musicError"))
                .andExpect(content().string(containsString("href=\"/users/bob/music\"")));
    }

    private String start(MockHttpSession browser) throws Exception {
        var result = mvc.perform(post("/lastfm/connect").with(csrf()).with(oidcLogin()).session(browser)
                        .param("userId", "999").param("lastFmUsername", "mallory"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://www.last.fm/api/auth/**")).andReturn();
        String url = result.getResponse().getRedirectedUrl();
        String callback = URLDecoder.decode(UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("cb"), StandardCharsets.UTF_8);
        return UriComponentsBuilder.fromUriString(callback).build().getQueryParams().getFirst("state");
    }

    private User user(String name, String googleSub) {
        User user = new User(name, name + "@example.com", null);
        user.setGoogleSub(googleSub);
        return user;
    }

    private LastFmProfile verified(String name, String subject, String fmName) {
        LastFmProfile profile = new LastFmProfile(user(name, subject), fmName);
        profile.verify(fmName);
        return profile;
    }
}
