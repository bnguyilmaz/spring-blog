package com.bengu.springblog.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LastFmServiceTest {
    LastFmService service;
    MockRestServiceServer server;

    @BeforeEach
    void setup() {
        service = new LastFmService();
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.com/2.0/");
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();
    }

    @Test
    void readsRecentTrackJsonWithArtistAndImage() {
        server.expect(queryParam("user", "alice"))
                .andRespond(withSuccess("""
                        {"recenttracks":{"track":[{"name":"Song A","artist":{"#text":"Artist A","mbid":""},
                        "image":[{"size":"large","#text":"https://example.com/cover.jpg"}],"@attr":{"nowplaying":"true"}}]}}
                        """, MediaType.APPLICATION_JSON));
        var tracks = service.getRecentTracks("alice");
        assertThat(tracks).hasSize(1);
        assertThat(tracks.get(0).getArtist().getName()).isEqualTo("Artist A");
        assertThat(tracks.get(0).getDisplayImageUrl()).isEqualTo("https://example.com/cover.jpg");
        server.verify();
    }

    @Test
    void emptyHistoryIsAnEmptyList() {
        server.expect(queryParam("method", "user.getrecenttracks"))
                .andRespond(withSuccess("{\"recenttracks\":{\"track\":[]}}", MediaType.APPLICATION_JSON));
        assertThat(service.getRecentTracks("alice")).isEmpty();
        server.verify();
    }

    @Test
    void sequentialRequestsUseTheRequestedUsername() {
        for (String username : java.util.List.of("alice", "bob")) {
            server.expect(queryParam("user", username)).andRespond(withSuccess(
                    "{\"topartists\":{\"artist\":[{\"name\":\"" + username + " artist\",\"playcount\":\"7\"}]}}", MediaType.APPLICATION_JSON));
        }
        assertThat(service.getTopArtists("alice").get(0).getName()).isEqualTo("alice artist");
        assertThat(service.getTopArtists("bob").get(0).getName()).isEqualTo("bob artist");
        server.verify();
    }

    @Test
    void apiErrorsAreNotEmptyHistory() {
        server.expect(queryParam("user", "alice"))
                .andRespond(withSuccess("{\"error\":29,\"message\":\"Rate limit exceeded\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.getRecentTracks("alice"))
                .isInstanceOf(com.bengu.springblog.exceptions.LastFmException.class);
    }

    @Test
    void httpErrorsDoNotExposeApiKey() {
        server.expect(queryParam("user", "alice")).andRespond(withServerError());
        assertThatThrownBy(() -> service.getRecentTracks("alice"))
                .isInstanceOf(com.bengu.springblog.exceptions.LastFmException.class)
                .hasMessageNotContaining("test-key");
    }
}
