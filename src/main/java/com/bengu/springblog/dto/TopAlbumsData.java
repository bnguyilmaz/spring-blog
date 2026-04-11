package com.bengu.springblog.dto;

import java.util.List;

public class TopAlbumsData {
    private List<AlbumResponse> album;


    public List<AlbumResponse> getAlbum() {
        return album;
    }

    public void setAlbum(List<AlbumResponse> album) {
        this.album = album;
    }
}
