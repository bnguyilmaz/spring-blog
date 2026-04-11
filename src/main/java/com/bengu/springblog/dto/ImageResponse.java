package com.bengu.springblog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Setter;

public class ImageResponse {
    @Setter
    private String size;
    @JsonProperty("#text")
    private String imageUrl;

    public ImageResponse(String size, String imageUrl) {
        this.size = size;
        this.imageUrl = imageUrl;
    }

    public String getSize() {
        return size;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
