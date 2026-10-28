package com.bengu.springblog.controllers;

import com.bengu.springblog.entities.User;
import com.bengu.springblog.exceptions.LastFmException;
import com.bengu.springblog.services.LastFmAuthorizationService;
import com.bengu.springblog.services.LastFmProfileService;
import com.bengu.springblog.services.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class ProfileController {
    private final UserService users;
    private final LastFmProfileService profiles;
    private final LastFmAuthorizationService authorization;

    public ProfileController(UserService users, LastFmProfileService profiles, LastFmAuthorizationService authorization) {
        this.users = users;
        this.profiles = profiles;
        this.authorization = authorization;
    }

    @GetMapping("/profile/lastfm")
    public String settings(@AuthenticationPrincipal OidcUser principal, Model model) {
        if (users.findByGoogleSub(principal.getSubject()).isEmpty()) return "redirect:/oauth-user";
        model.addAttribute("lastFmUsername", profiles.usernameFor(principal.getSubject()).orElse(null));
        return "lastfm-settings";
    }

    @PostMapping("/lastfm/connect")
    public String connect(@AuthenticationPrincipal OidcUser principal, HttpSession session, RedirectAttributes redirect) {
        if (users.findByGoogleSub(principal.getSubject()).isEmpty()) return "redirect:/oauth-user";
        try {
            return "redirect:" + authorization.begin(principal.getSubject(), session);
        } catch (LastFmException exception) {
            redirect.addFlashAttribute("lastFmError", exception.getMessage());
            return "redirect:/profile/lastfm";
        }
    }

    @GetMapping("/lastfm/callback")
    public String callback(@AuthenticationPrincipal OidcUser principal,
                           @RequestParam(defaultValue = "") String state,
                           @RequestParam(defaultValue = "") String token,
                           HttpSession session, HttpServletResponse response, RedirectAttributes redirect) {
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
        try {
            authorization.complete(principal.getSubject(), state, token, session);
            return "redirect:/music";
        } catch (LastFmException exception) {
            redirect.addFlashAttribute("lastFmError", exception.getMessage());
            return "redirect:/profile/lastfm";
        } catch (IllegalStateException exception) {
            return "redirect:/oauth-user";
        }
    }

    @GetMapping("/users")
    public String findUser(@RequestParam(defaultValue = "") String username) {
        String name = username.trim();
        if (name.isEmpty()) return "user-search";
        if (!name.matches("[A-Za-z0-9._-]{3,50}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return "redirect:" + UriComponentsBuilder.fromPath("/users/{username}").buildAndExpand(name).encode().toUriString();
    }

    @GetMapping("/users/{username}")
    public String publicProfile(@PathVariable String username, @AuthenticationPrincipal OidcUser principal, Model model) {
        User target = users.getUserByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Only public display fields enter the model; never the User entity.
        model.addAttribute("profileUsername", target.getUsername());
        model.addAttribute("ownProfile", principal.getSubject().equals(target.getGoogleSub()));
        model.addAttribute("lastFmUsername", profiles.usernameForUser(target.getUsername()).orElse(null));
        return "user-profile";
    }
}
