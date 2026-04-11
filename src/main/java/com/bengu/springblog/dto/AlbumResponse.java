package com.bengu.springblog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AlbumResponse {
    private String name;
    private AlbumArtistResponse artist;
    private List<ImageResponse> image;
    private String displayImageUrl;



    public String getDisplayImageUrl() {
        return displayImageUrl;
    }


    public List<ImageResponse> getImage() {
        return image;
    }

    public void setImage(List<ImageResponse> image) {
        this.image = image;
    }

    public void setDisplayImageUrl(String displayImageUrl) {
        this.displayImageUrl = displayImageUrl;
    }



    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AlbumArtistResponse getArtist() {
        return artist;
    }

    public void setArtist(AlbumArtistResponse artist) {
        this.artist = artist;
    }


}
