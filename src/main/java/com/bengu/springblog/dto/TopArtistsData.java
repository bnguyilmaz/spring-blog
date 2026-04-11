package com.bengu.springblog.dto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class TopArtistsData {
    private List<ArtistResponse> artist;

    public List<ArtistResponse> getArtist() {
        return artist;
    }

    public void setArtist(List<ArtistResponse> artist) {
        this.artist = artist;
    }

}

