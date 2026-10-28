package com.bengu.springblog.controllers;

import com.bengu.springblog.entities.User;
import com.bengu.springblog.exceptions.LastFmException;
import com.bengu.springblog.services.LastFmProfileService;
import com.bengu.springblog.services.LastFmService;
import com.bengu.springblog.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.List;

@Controller
public class MusicController {
    private final LastFmService lastFm;
    private final LastFmProfileService profiles;
    private final UserService users;

    public MusicController(LastFmService lastFm, LastFmProfileService profiles, UserService users) {
        this.lastFm = lastFm;
        this.profiles = profiles;
        this.users = users;
    }

    @GetMapping("/music")
    public String music(@AuthenticationPrincipal OidcUser principal, Model model) {
        User user = users.findByGoogleSub(principal.getSubject()).orElse(null);
        if (user == null) return "redirect:/oauth-user";
        String username = profiles.usernameFor(principal.getSubject()).orElse(null);
        if (username == null) return "redirect:/profile/lastfm";
        preparePage(user.getUsername(), username, true, "/music", model);
        return "music";
    }

    @GetMapping("/users/{username}/music")
    public String userMusic(@PathVariable String username, @AuthenticationPrincipal OidcUser principal, Model model) {
        User target = users.getUserByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String lastFmUsername = profiles.usernameForUser(target.getUsername()).orElse(null);
        boolean own = principal.getSubject().equals(target.getGoogleSub());
        String pageUrl = UriComponentsBuilder.fromPath("/users/{username}/music")
                .buildAndExpand(target.getUsername()).encode().toUriString();
        preparePage(target.getUsername(), lastFmUsername, own, pageUrl, model);
        return "music";
    }

    private void preparePage(String profileUsername, String lastFmUsername, boolean ownProfile, String pageUrl, Model model) {
        model.addAttribute("profileUsername", profileUsername);
        model.addAttribute("lastFmUsername", lastFmUsername);
        model.addAttribute("ownProfile", ownProfile);
        model.addAttribute("musicPageUrl", pageUrl);
        model.addAttribute("artists", List.of());
        model.addAttribute("albums", List.of());
        model.addAttribute("recentTrack", List.of());
        if (lastFmUsername == null) return;
        try {
            model.addAttribute("recentTrack", lastFm.getRecentTracks(lastFmUsername));
            model.addAttribute("artists", lastFm.getTopArtists(lastFmUsername));
            model.addAttribute("albums", lastFm.getTopAlbums(lastFmUsername));
        } catch (LastFmException exception) {
            model.addAttribute("musicError", exception.getMessage());
        }
    }

    @GetMapping("/music/artist")
    public String showArtistDetail(@RequestParam String name,
                                   @RequestParam(required = false) String profile, Model model) {
        model.addAttribute("artistName", name);
        if (profile != null) {
            User target = users.getUserByUsername(profile)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            model.addAttribute("musicPageUrl", UriComponentsBuilder.fromPath("/users/{username}/music")
                    .buildAndExpand(target.getUsername()).encode().toUriString());
        } else {
            model.addAttribute("musicPageUrl", "/music");
        }
        return "artist-detail";
    }
}
