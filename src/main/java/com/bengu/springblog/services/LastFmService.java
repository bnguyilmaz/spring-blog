package com.bengu.springblog.services;

import com.bengu.springblog.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;



@Service
public class LastFmService {

    @Value("${lastfm.api.key}")
    private String apiKey;

    @Value("${lastfm.base.url}")
    private String baseUrl;

    @Value("${lastfm.user}")
    private String user;
    private String displayImageUrl;

    RestTemplate restTemplate = new RestTemplate();
    public List<ArtistResponse> getTopArtists(){
        String url = baseUrl
                + "?method=user.gettopartists"
                + "&user=" + user
                + "&api_key=" + apiKey
                + "&period=1month"
                + "&format=json"
                + "&limit=10";
        TopArtistsResponse response = restTemplate.getForObject(url, TopArtistsResponse.class);

        System.out.println(response.getTopartists()
                .getArtist()
                .stream()
                .filter(a -> a != null)
                .toList());
        return response.getTopartists()
                .getArtist()
                .stream()
                .filter(a -> a != null)
                .toList();
    }
    public List<AlbumResponse> getTopAlbums(){
        //returns a list includes album objects
         String url = baseUrl
                + "?method=user.gettopalbums"
                + "&user=" + user
                + "&api_key=" + apiKey
                + "&format=json"
                + "&limit=12";

        TopAlbumsResponse response = restTemplate.getForObject(url,TopAlbumsResponse.class);

        System.out.println(response.getTopalbums());
        return response.getTopalbums()
                .getAlbum()
                .stream()
                .filter(album -> album != null)
                .peek(album -> album.setDisplayImageUrl(findBestImage(album.getImage())))  //calles helper function
                .toList();
    }
    public List<TrackResponse> getRecentTracks(){

        String url = baseUrl
                + "?method=user.getrecenttracks"
                + "&user=" + user
                + "&api_key=" + apiKey
                + "&format=json"
                + "&limit=1";
        RecentTracksResponse response = restTemplate.getForObject(url,RecentTracksResponse.class);

        System.out.println(response.getRecenttracks().getTrack());
        return response.getRecenttracks()
                .getTrack()
                .stream()
                .filter(track -> track != null)
                .peek(album -> album.setDisplayImageUrl(findBestImage(album.getImage())))  //calles helper function
                .toList();
    }



    private String findBestImage(List<ImageResponse> images) {
        // returns best image's url
        if (images == null) {
            return null;
        }

        return images.stream()
                .filter(img -> "large".equals(img.getSize()))
                .map(ImageResponse::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }
}
