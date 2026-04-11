package com.bengu.springblog.dto;

public class AlbumArtistResponse {
    private String name;

    public AlbumArtistResponse(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
