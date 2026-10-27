package com.bengu.springblog.controllers;

import com.bengu.springblog.config.SecurityConfig;
import com.bengu.springblog.dto.*;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {HomeController.class, AuthController.class, MusicController.class, ProfileController.class}, properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@Import({SecurityConfig.class, WebSecurityTestConfig.class})
class PageRenderingTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService users;
    @MockitoBean LastFmService lastFm;
    @MockitoBean LastFmProfileService profiles;

    @Test
    void homeHasWorkingLoginLink() throws Exception {
        preview("index", mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/google"))).andReturn());
    }

    @Test
    void unlinkedProfilePromptsForLastFm() throws Exception {
        user();
        preview("profile-setup", mvc.perform(get("/oauth-user").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"lastFmUsername\""))).andReturn());
    }

    @Test
    void connectedProfileHasMusicAndSettingsLinks() throws Exception {
        user();
        when(profiles.usernameFor("user")).thenReturn(Optional.of("alice-fm"));
        preview("profile", mvc.perform(get("/oauth-user").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Müziklerimi gör")))
                .andExpect(content().string(containsString("alice-fm"))).andReturn());
    }

    @Test
    void settingsRetainsCurrentUsername() throws Exception {
        user();
        when(profiles.usernameFor("user")).thenReturn(Optional.of("alice-fm"));
        preview("settings", mvc.perform(get("/profile/lastfm").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"alice-fm\""))).andReturn());
    }

    @Test
    void usernameSelectionKeepsFormAndSuggestion() throws Exception {
        when(users.suggestUsername("alice@example.com")).thenReturn("alice");
        preview("username", mvc.perform(get("/choose-username").with(oidcLogin())
                        .sessionAttr("pendingGoogleSub", "user").sessionAttr("pendingGoogleEmail", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"alice\""))).andReturn());
    }

    @Test
    void populatedMusicRendersTracksAlbumsAndArtistsAndEscapesNames() throws Exception {
        when(profiles.usernameFor("user")).thenReturn(Optional.of("alice-fm"));
        when(lastFm.getRecentTracks("alice-fm")).thenReturn(List.of(
                new TrackResponse(new RecentTrackArtistResponse("Radiohead"), "Weird Fishes / Arpeggi", List.of(), null),
                new TrackResponse(new RecentTrackArtistResponse("Björk"), "<script>alert('x')</script>", List.of(), null)));
        AlbumResponse album = new AlbumResponse();
        album.setName("In Rainbows");
        album.setArtist(new AlbumArtistResponse("Radiohead"));
        when(lastFm.getTopAlbums("alice-fm")).thenReturn(List.of(album));
        ArtistResponse artist = new ArtistResponse();
        artist.setName("Radiohead");
        artist.setPlaycount("42");
        when(lastFm.getTopArtists("alice-fm")).thenReturn(List.of(artist));
        preview("music", mvc.perform(get("/music").with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Weird Fishes / Arpeggi")))
                .andExpect(content().string(containsString("In Rainbows")))
                .andExpect(content().string(containsString("42 dinleme")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(containsString("/images/album-placeholder.svg"))).andReturn());
    }

    private void user() {
        when(users.findByGoogleSub("user")).thenReturn(Optional.of(new User("alice", "alice@example.com", null)));
    }

    private void preview(String name, MvcResult result) throws Exception {
        if (Boolean.getBoolean("springblog.preview")) {
            Path directory = Path.of("target", "preview");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name + ".html"), result.getResponse().getContentAsString());
        }
    }
}
