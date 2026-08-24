package com.bengu.springblog.controllers;

import com.bengu.springblog.entities.User;
import com.bengu.springblog.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.security.oauth2.client.autoconfigure
        .OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet
        .OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.method.annotation
        .AuthenticationPrincipalArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {
        OAuth2ClientAutoConfiguration.class,
        OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthControllerMvcTest.AuthenticationPrincipalConfig.class)
class AuthControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void oauthUser_shouldRedirectToChooseUsername_whenGoogleUserDoesNotExist()
            throws Exception {

        authenticateOidcUser();

        when(userService.findByGoogleSub("google-sub"))
                .thenReturn(Optional.empty());

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(
                        get("/oauth-user")
                                .session(session)
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/choose-username"));

        assertThat(session.getAttribute("pendingGoogleSub"))
                .isEqualTo("google-sub");

        assertThat(session.getAttribute("pendingGoogleEmail"))
                .isEqualTo("bengu@example.com");

        verify(userService).findByGoogleSub("google-sub");
    }

    @Test
    void oauthUser_shouldReturnOauthUserPage_whenGoogleUserAlreadyExists()
            throws Exception {

        authenticateOidcUser();

        User existingUser = mock(User.class);

        when(userService.findByGoogleSub("google-sub"))
                .thenReturn(Optional.of(existingUser));

        mockMvc.perform(get("/oauth-user"))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth-user"))
                .andExpect(model().attribute("user", existingUser))
                .andExpect(model().attribute("name", "Bengü Yılmaz"))
                .andExpect(model().attribute(
                        "email",
                        "bengu@example.com"
                ))
                .andExpect(model().attribute(
                        "picture",
                        "https://example.com/picture.jpg"
                ));

        verify(userService).findByGoogleSub("google-sub");
    }

    @Test
    void chooseUsernamePage_shouldReturnPageAndSuggestion_whenSessionExists()
            throws Exception {

        MockHttpSession session = pendingGoogleUserSession();

        when(userService.suggestUsername("bengu@example.com"))
                .thenReturn("bengu");

        mockMvc.perform(
                        get("/choose-username")
                                .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(view().name("choose-username"))
                .andExpect(model().attribute(
                        "suggestedUsername",
                        "bengu"
                ));

        verify(userService).suggestUsername("bengu@example.com");
    }

    @Test
    void chooseUsernamePage_shouldRedirectToOauthUser_whenSessionIsMissing()
            throws Exception {

        mockMvc.perform(get("/choose-username"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth-user"));

        verifyNoInteractions(userService);
    }

    @Test
    void createGoogleUser_shouldCreateUserAndClearSession_whenRequestIsValid()
            throws Exception {

        MockHttpSession session = pendingGoogleUserSession();

        mockMvc.perform(
                        post("/choose-username")
                                .session(session)
                                .param("username", "bengu")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth-user"));

        verify(userService).createGoogleUser(
                "google-sub",
                "bengu@example.com",
                "bengu"
        );

        assertThat(session.getAttribute("pendingGoogleSub")).isNull();
        assertThat(session.getAttribute("pendingGoogleEmail")).isNull();
    }

    @Test
    void createGoogleUser_shouldRedirectToOauthUser_whenSessionIsMissing()
            throws Exception {

        mockMvc.perform(
                        post("/choose-username")
                                .param("username", "bengu")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth-user"));

        verifyNoInteractions(userService);
    }

    @Test
    void createGoogleUser_shouldReturnBadRequest_whenUsernameParameterIsMissing()
            throws Exception {

        MockHttpSession session = pendingGoogleUserSession();

        mockMvc.perform(
                        post("/choose-username")
                                .session(session)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    private MockHttpSession pendingGoogleUserSession() {
        MockHttpSession session = new MockHttpSession();

        session.setAttribute(
                "pendingGoogleSub",
                "google-sub"
        );

        session.setAttribute(
                "pendingGoogleEmail",
                "bengu@example.com"
        );

        return session;
    }

    private void authenticateOidcUser() {
        OidcUser oidcUser = mock(OidcUser.class);

        when(oidcUser.getSubject())
                .thenReturn("google-sub");

        when(oidcUser.getEmail())
                .thenReturn("bengu@example.com");

        when(oidcUser.getFullName())
                .thenReturn("Bengü Yılmaz");

        when(oidcUser.getPicture())
                .thenReturn("https://example.com/picture.jpg");

        OAuth2AuthenticationToken authentication =
                new OAuth2AuthenticationToken(
                        oidcUser,
                        List.of(),
                        "google"
                );

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthenticationPrincipalConfig
            implements WebMvcConfigurer {

        @Override
        public void addArgumentResolvers(
                List<HandlerMethodArgumentResolver> resolvers
        ) {
            resolvers.add(
                    new AuthenticationPrincipalArgumentResolver()
            );
        }
    }
}