package com.bengu.springblog.controllers;

import com.bengu.springblog.exceptions.LastFmException;
import com.bengu.springblog.services.LastFmProfileService;
import com.bengu.springblog.services.LastFmService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/music")
public class MusicController {
    private final LastFmService lastFm;
    private final LastFmProfileService profiles;

    public MusicController(LastFmService lastFm, LastFmProfileService profiles) {
        this.lastFm = lastFm;
        this.profiles = profiles;
    }

    @GetMapping
    public String music(@AuthenticationPrincipal OidcUser principal, Model model) {
        String username = profiles.usernameFor(principal.getSubject()).orElse(null);
        if (username == null) return "redirect:/profile/lastfm";
        model.addAttribute("lastFmUsername", username);
        model.addAttribute("artists", List.of());
        model.addAttribute("albums", List.of());
        model.addAttribute("recentTrack", List.of());
        try {
            model.addAttribute("recentTrack", lastFm.getRecentTracks(username));
            model.addAttribute("artists", lastFm.getTopArtists(username));
            model.addAttribute("albums", lastFm.getTopAlbums(username));
        } catch (LastFmException exception) {
            model.addAttribute("musicError", exception.getMessage());
        }
        return "music";
    }

    @GetMapping("/artist")
    public String showArtistDetail(@RequestParam String name, Model model) {
        model.addAttribute("artistName", name);
        return "artist-detail";
    }
}
