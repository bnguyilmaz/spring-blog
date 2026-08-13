package com.bengu.springblog.controllers;

import com.bengu.springblog.entities.User;
import com.bengu.springblog.services.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class AuthController {

    /*
     * Session içinde kullanacağımız anahtarlar.
     *
     * Kullanıcı Google tarafından doğrulandı ancak henüz
     * username seçmediyse Google bilgilerini bunlarla saklıyoruz.
     */
    private static final String PENDING_GOOGLE_SUB =
            "pendingGoogleSub";

    private static final String PENDING_GOOGLE_EMAIL =
            "pendingGoogleEmail";

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /*
     * Google girişi başarılı olduğunda kullanıcı
     * SecurityConfig tarafından bu adrese gönderilir.
     */
    @GetMapping("/oauth-user")
    public String handleGoogleLogin(
            @AuthenticationPrincipal OidcUser oidcUser,
            HttpSession session,
            Model model
    ) {
        // Google tarafından gönderilen kullanıcı bilgileri
        String googleSub = oidcUser.getSubject();
        String email = oidcUser.getEmail();

        // Bu Google kullanıcısı bizim DB'mizde var mı?
        Optional<User> existingUser =
                userService.findByGoogleSub(googleSub);

        // DB'de yoksa önce username seçmesini istiyoruz.
        if (existingUser.isEmpty()) {

            // Google bilgilerini sonraki isteklere taşıması için
            // session'a geçici olarak koyuyoruz.
            session.setAttribute(
                    PENDING_GOOGLE_SUB,
                    googleSub
            );

            session.setAttribute(
                    PENDING_GOOGLE_EMAIL,
                    email
            );

            return "redirect:/choose-username";
        }

        // Kullanıcı zaten DB'de varsa HTML'de göstermek
        // istediğimiz bilgileri Model'e koyuyoruz.
        model.addAttribute(
                "user",
                existingUser.get()
        );

        model.addAttribute(
                "name",
                oidcUser.getFullName()
        );

        model.addAttribute(
                "email",
                email
        );

        model.addAttribute(
                "picture",
                oidcUser.getPicture()
        );

        return "oauth-user";
    }

    /*
     * Username seçim sayfasını gösterir.
     */
    @GetMapping("/choose-username")
    public String showChooseUsername(
            HttpSession session,
            Model model
    ) {
        // Önceki endpoint'in session'a koyduğu bilgileri alıyoruz.
        String googleSub = (String) session.getAttribute(
                PENDING_GOOGLE_SUB
        );

        String email = (String) session.getAttribute(
                PENDING_GOOGLE_EMAIL
        );

        // Session'da bekleyen Google kullanıcısı yoksa
        // bu sayfaya doğrudan gelinmiş olabilir.
        if (googleSub == null || email == null) {
            return "redirect:/oauth-user";
        }

        // E-postaya göre boşta olan bir username öneriyoruz.
        String suggestedUsername =
                userService.suggestUsername(email);

        // Bu veriler choose-username.html'e gönderilir.
        model.addAttribute(
                "suggestedUsername",
                suggestedUsername
        );

        model.addAttribute(
                "email",
                email
        );

        return "choose-username";
    }

    /*
     * Kullanıcı username formunu gönderdiğinde çalışır.
     */
    @PostMapping("/choose-username")
    public String createGoogleUser(
            @RequestParam("username") String username,
            HttpSession session
    ) {
        // Google bilgilerini formdan değil session'dan alıyoruz.
        String googleSub = (String) session.getAttribute(
                PENDING_GOOGLE_SUB
        );

        String email = (String) session.getAttribute(
                PENDING_GOOGLE_EMAIL
        );

        // Session bilgileri bulunmuyorsa kullanıcı oluşturamayız.
        if (googleSub == null || email == null) {
            return "redirect:/oauth-user";
        }

        // Google bilgileri ve kullanıcının seçtiği username ile
        // veritabanında User kaydı oluşturuyoruz.
        userService.createGoogleUser(
                googleSub,
                email,
                username
        );

        // Kayıt tamamlandığı için geçici bilgileri siliyoruz.
        session.removeAttribute(PENDING_GOOGLE_SUB);
        session.removeAttribute(PENDING_GOOGLE_EMAIL);

        return "redirect:/oauth-user";
    }
}