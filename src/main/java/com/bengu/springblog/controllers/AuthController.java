package com.bengu.springblog.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/oauth-user")
    public String oauthUser(
            @AuthenticationPrincipal OidcUser oidcUser,
            Model model
    ) {
        model.addAttribute("name", oidcUser.getFullName());
        model.addAttribute("email", oidcUser.getEmail());
        model.addAttribute("picture", oidcUser.getPicture());
        model.addAttribute("sub", oidcUser.getSubject());

        return "oauth-user";
    }
}