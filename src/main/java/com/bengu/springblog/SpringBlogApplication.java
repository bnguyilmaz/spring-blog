package com.bengu.springblog;

import com.bengu.springblog.services.LastFmService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringBlogApplication {

    public static void main(String[] args) {
        System.out.println(
                "LASTFM_API_KEY mevcut mu? "
                        + (System.getenv("LASTFM_API_KEY") != null)
        );
        SpringApplication.run(SpringBlogApplication.class, args);
        String googleClientId = System.getenv("GOOGLE_CLIENT_ID");

        System.out.println(
                "GOOGLE_CLIENT_ID mevcut mu? "
                        + (googleClientId != null)
        );

        System.out.println(
                "GOOGLE_CLIENT_ID biçimi doğru mu? "
                        + (googleClientId != null
                        && googleClientId.endsWith(".apps.googleusercontent.com"))
        );
        String googleClientSecret = System.getenv("GOOGLE_CLIENT_SECRET");

        System.out.println(
                "GOOGLE_CLIENT_SECRET mevcut mu? "
                        + (googleClientSecret != null && !googleClientSecret.isBlank())
        );
    }

}
