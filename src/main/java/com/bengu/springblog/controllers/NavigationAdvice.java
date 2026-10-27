package com.bengu.springblog.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NavigationAdvice {
    @ModelAttribute
    public void navigation(Authentication authentication, Model model) {
        OidcUser user = authentication != null && authentication.getPrincipal() instanceof OidcUser oidc ? oidc : null;
        model.addAttribute("signedIn", user != null);
        model.addAttribute("avatarUrl", user == null ? null : user.getPicture());
    }
}
