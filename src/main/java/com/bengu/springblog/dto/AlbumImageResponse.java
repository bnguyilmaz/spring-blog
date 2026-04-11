package com.bengu.springblog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AlbumImageResponse {
    private List<ImageResponse> images;

    public List<ImageResponse> getImages() {
        return images;
    }

    public void setImage(List<ImageResponse> images) {
        this.images = images;
    }
}
