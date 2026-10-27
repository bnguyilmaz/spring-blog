package com.bengu.springblog.controllers;

import org.springframework.stereotype.Controller;
import com.bengu.springblog.exceptions.LastFmException;
import com.bengu.springblog.services.LastFmProfileService;
import com.bengu.springblog.services.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProfileController {
    private final UserService users;
    private final LastFmProfileService profiles;

    public ProfileController(UserService users, LastFmProfileService profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    @GetMapping("/profile/lastfm")
    public String settings(@AuthenticationPrincipal OidcUser principal, Model model) {
        if (users.findByGoogleSub(principal.getSubject()).isEmpty()) return "redirect:/oauth-user";
        model.addAttribute("lastFmUsername", profiles.usernameFor(principal.getSubject()).orElse(""));
        return "lastfm-settings";
    }

    @PostMapping("/profile/lastfm")
    public String connect(@AuthenticationPrincipal OidcUser principal,
                          @RequestParam(defaultValue = "") String lastFmUsername, Model model) {
        if (users.findByGoogleSub(principal.getSubject()).isEmpty()) return "redirect:/oauth-user";
        try {
            profiles.connect(principal.getSubject(), lastFmUsername);
            return "redirect:/music";
        } catch (IllegalArgumentException | LastFmException exception) {
            model.addAttribute("lastFmUsername", lastFmUsername);
            model.addAttribute("lastFmError", exception.getMessage());
            return "lastfm-settings";
        }
    }
}
