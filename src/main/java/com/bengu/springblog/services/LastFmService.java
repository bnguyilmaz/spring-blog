package com.bengu.springblog.services;

import com.bengu.springblog.dto.*;
import com.bengu.springblog.exceptions.LastFmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class LastFmService {
    @Value("${lastfm.api.key}")
    private String apiKey;
    @Value("${lastfm.base.url}")
    private String baseUrl;
    private final RestTemplate restTemplate;

    public LastFmService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        restTemplate = new RestTemplate(factory);
    }

    public String verifyUsername(String username) {
        Map<?, ?> response = request("user.getinfo", username, Map.class);
        if (response.get("error") instanceof Number error) {
            if (error.intValue() == 6) {
                throw new IllegalArgumentException("Last.fm kullanıcısı bulunamadı. Kullanıcı adını kontrol et.");
            }
            throw new LastFmException();
        }
        if (response.get("user") instanceof Map<?, ?> user
                && user.get("name") instanceof String name && !name.isBlank()) {
            return name;
        }
        throw new LastFmException();
    }

    public List<ArtistResponse> getTopArtists(String username) {
        TopArtistsResponse response = request("user.gettopartists", username, TopArtistsResponse.class);
        if (response.getTopartists() == null) throw new LastFmException();
        return nonNullItems(response.getTopartists().getArtist());
    }

    public List<AlbumResponse> getTopAlbums(String username) {
        TopAlbumsResponse response = request("user.gettopalbums", username, TopAlbumsResponse.class);
        if (response.getTopalbums() == null) throw new LastFmException();
        List<AlbumResponse> albums = nonNullItems(response.getTopalbums().getAlbum());
        albums.forEach(album -> album.setDisplayImageUrl(findBestImage(album.getImage())));
        return albums;
    }

    public List<TrackResponse> getRecentTracks(String username) {
        RecentTracksResponse response = request("user.getrecenttracks", username, RecentTracksResponse.class);
        if (response.getRecenttracks() == null) throw new LastFmException();
        List<TrackResponse> tracks = nonNullItems(response.getRecenttracks().getTrack());
        tracks.forEach(track -> track.setDisplayImageUrl(findBestImage(track.getImage())));
        return tracks;
    }

    private <T> T request(String method, String username, Class<T> responseType) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("method", method)
                .queryParam("user", username)
                .queryParam("api_key", apiKey)
                .queryParam("format", "json")
                .queryParam("limit", 12)
                .queryParam("period", "1month")
                .build().encode().toUri();
        try {
            T response = restTemplate.getForObject(uri, responseType);
            if (response == null) throw new LastFmException();
            return response;
        } catch (RestClientException exception) {
            // The request URI includes the API key; keep it out of error messages.
            throw new LastFmException();
        }
    }

    private static <T> List<T> nonNullItems(List<T> items) {
        return items == null ? List.of() : items.stream().filter(Objects::nonNull).toList();
    }

    private String findBestImage(List<ImageResponse> images) {
        return nonNullItems(images).stream()
                .filter(image -> "large".equals(image.getSize()))
                .map(ImageResponse::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst().orElse(null);
    }
}
