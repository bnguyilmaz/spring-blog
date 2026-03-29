package com.bengu.springblog.services;

import com.bengu.springblog.dto.ArtistResponse;
import com.bengu.springblog.dto.TopArtistsData;
import com.bengu.springblog.dto.TopArtistsResponse;
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

    RestTemplate restTemplate = new RestTemplate();
    public List<ArtistResponse> getTopArtists(){
        String url = baseUrl
                + "?method=user.gettopartists"
                + "&user=" + user
                + "&api_key=" + apiKey
                + "&period=7day"
                + "&format=json";
        System.out.println("BASE URL: " + baseUrl);
        System.out.println("API KEY: " + apiKey);
        System.out.println("USER: " + user);
        System.out.println("FINAL URL: " + url);
        TopArtistsResponse response = restTemplate.getForObject(url, TopArtistsResponse.class);
        List<ArtistResponse> artists = response.getTopartists().getArtist();

        return response.getTopartists()
                .getArtist()
                .stream()
                .filter(a -> a != null)
                .toList();
    }
}
