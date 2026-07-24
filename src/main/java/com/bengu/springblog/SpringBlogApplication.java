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
    }

}
