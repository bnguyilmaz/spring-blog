package com.bengu.springblog.controllers;

import com.bengu.springblog.dto.AlbumResponse;
import com.bengu.springblog.dto.ArtistResponse;
import com.bengu.springblog.services.LastFmService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/music")
public class MusicController {
    private final LastFmService lastFmService;

    public MusicController(LastFmService lastFmService) {
        this.lastFmService = lastFmService;
    }

    @GetMapping
    public String music(Model model){
        List<ArtistResponse> artists = lastFmService.getTopArtists();
        model.addAttribute("artists",artists);
        List<AlbumResponse> albums = lastFmService.getTopAlbums();
        model.addAttribute("albums",albums);
        return "music";
    }

}
