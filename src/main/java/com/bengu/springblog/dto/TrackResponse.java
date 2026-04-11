package com.bengu.springblog.dto;

import java.util.List;

public class TrackResponse {
    private RecentTrackArtistResponse artist;
    private String name;
    private List<ImageResponse> image;
    private String displayImageUrl;


    public String getDisplayImageUrl() {
        return displayImageUrl;
    }

    public void setDisplayImageUrl(String displayImageUrl) {
        this.displayImageUrl = displayImageUrl;
    }

    public void setArtist(RecentTrackArtistResponse artist) {
        this.artist = artist;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setImage(List<ImageResponse> image) {
        this.image = image;
    }

    public RecentTrackArtistResponse getArtist() {
        return artist;
    }

    public TrackResponse(RecentTrackArtistResponse artist, String name, List<ImageResponse> image, String displayImageUrl) {
        this.artist = artist;
        this.name = name;
        this.image = image;
        this.displayImageUrl = displayImageUrl;
    }

    public String getName() {
        return name;
    }

    public List<ImageResponse> getImage() {
        return image;
    }
}
