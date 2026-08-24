package com.bengu.springblog.controllers;

import com.bengu.springblog.config.SecurityConfig;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.exceptions.EmailAlreadyExistsException;
import com.bengu.springblog.exceptions.GlobalExceptionHandler;
import com.bengu.springblog.exceptions.GoogleAccountAlreadyLinkedException;
import com.bengu.springblog.exceptions.UsernameAlreadyExistsException;
import com.bengu.springblog.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(
        controllers = AuthController.class,
        properties = {
                "spring.security.oauth2.client.registration.google.client-id=test-client-id",
                "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
        }
)
@Import({
        AuthControllerTest.WebSecurityTestConfiguration.class,
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class AuthControllerTest {

    private static final String GOOGLE_SUB = "google-123";
    private static final String EMAIL = "bengu@example.com";
    private static final String USERNAME = "bengu";
    private static final String FULL_NAME = "Bengu Yilmaz";
    private static final String PICTURE = "https://example.com/picture.jpg";
    private static final String PENDING_GOOGLE_SUB = "pendingGoogleSub";
    private static final String PENDING_GOOGLE_EMAIL = "pendingGoogleEmail";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void oauthUser_shouldReturnUserView_whenGoogleUserExists() throws Exception {
        User existingUser = new User(USERNAME, EMAIL, null);

        when(userService.findByGoogleSub(GOOGLE_SUB))
                .thenReturn(Optional.of(existingUser));

        mockMvc.perform(get("/oauth-user").with(googleLogin()))
                .andExpect(status().isOk())
                .andExpect(view().name("oauth-user"))
                .andExpect(model().attribute("user", existingUser))
                .andExpect(model().attribute("name", FULL_NAME))
                .andExpect(model().attribute("email", EMAIL))
                .andExpect(model().attribute("picture", PICTURE));

        verify(userService).findByGoogleSub(GOOGLE_SUB);
    }

    @Test
    void oauthUser_shouldStoreGoogleDataAndRedirect_whenGoogleUserDoesNotExist()
            throws Exception {
        MockHttpSession session = new MockHttpSession();

        when(userService.findByGoogleSub(GOOGLE_SUB))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/oauth-user")
                        .with(googleLogin())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/choose-username"));

        assertThat(session.getAttribute(PENDING_GOOGLE_SUB)).isEqualTo(GOOGLE_SUB);
        assertThat(session.getAttribute(PENDING_GOOGLE_EMAIL)).isEqualTo(EMAIL);
        verify(userService).findByGoogleSub(GOOGLE_SUB);
    }

    @Test
    void oauthUser_shouldRedirectToAuthentication_whenRequestIsUnauthenticated()
            throws Exception {
        mockMvc.perform(get("/oauth-user"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/google"));

        verify(userService, never()).findByGoogleSub(GOOGLE_SUB);
    }

    @Test
    void chooseUsername_shouldReturnFormWithSuggestion_whenPendingSessionIsValid()
            throws Exception {
        MockHttpSession session = pendingGoogleSession();
        when(userService.suggestUsername(EMAIL)).thenReturn(USERNAME);

        mockMvc.perform(get("/choose-username")
                        .with(oidcLogin())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("choose-username"))
                .andExpect(model().attribute("suggestedUsername", USERNAME))
                .andExpect(model().attribute("email", EMAIL));

        verify(userService).suggestUsername(EMAIL);
    }

    @Test
    void chooseUsername_shouldRedirectWithoutSuggestion_whenPendingSessionIsMissing()
            throws Exception {
        mockMvc.perform(get("/choose-username").with(oidcLogin()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth-user"));

        verify(userService, never()).suggestUsername(EMAIL);
    }

    @Test
    void chooseUsername_shouldRedirectWithoutSuggestion_whenPendingEmailIsMissing()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PENDING_GOOGLE_SUB, GOOGLE_SUB);

        mockMvc.perform(get("/choose-username")
                        .with(oidcLogin())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth-user"));

        verify(userService, never()).suggestUsername(EMAIL);
    }

    @Test
    void chooseUsername_shouldRedirectToAuthentication_whenRequestIsUnauthenticated()
            throws Exception {
        mockMvc.perform(get("/choose-username"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/google"));
    }

    @Test
    void createGoogleUser_shouldCreateUserClearSessionAndRedirect_whenRequestIsValid()
            throws Exception {
        MockHttpSession session = pendingGoogleSession();

        mockMvc.perform(post("/choose-username")
                        .with(oidcLogin())
                        .with(csrf())
                        .session(session)
                        .param("username", USERNAME))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth-user"));

        verify(userService).createGoogleUser(GOOGLE_SUB, EMAIL, USERNAME);
        assertThat(session.getAttribute(PENDING_GOOGLE_SUB)).isNull();
        assertThat(session.getAttribute(PENDING_GOOGLE_EMAIL)).isNull();
    }

    @Test
    void createGoogleUser_shouldReturnForbiddenWithoutCsrf() throws Exception {
        MockHttpSession session = pendingGoogleSession();

        mockMvc.perform(post("/choose-username")
                        .with(oidcLogin())
                        .session(session)
                        .param("username", USERNAME))
                .andExpect(status().isForbidden());

        verify(userService, never()).createGoogleUser(GOOGLE_SUB, EMAIL, USERNAME);
    }

    @Test
    void createGoogleUser_shouldRedirectWithoutCreating_whenPendingSessionIsMissing()
            throws Exception {
        mockMvc.perform(post("/choose-username")
                        .with(oidcLogin())
                        .with(csrf())
                        .param("username", USERNAME))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth-user"));

        verify(userService, never()).createGoogleUser(GOOGLE_SUB, EMAIL, USERNAME);
    }

    @Test
    void createGoogleUser_shouldRedirectWithoutCreating_whenPendingEmailIsMissing()
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PENDING_GOOGLE_SUB, GOOGLE_SUB);

        mockMvc.perform(post("/choose-username")
                        .with(oidcLogin())
                        .with(csrf())
                        .session(session)
                        .param("username", USERNAME))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth-user"));

        verify(userService, never()).createGoogleUser(GOOGLE_SUB, EMAIL, USERNAME);
    }

    @Test
    void createGoogleUser_shouldReturnBadRequest_whenUsernameParameterIsMissing()
            throws Exception {
        mockMvc.perform(post("/choose-username")
                        .with(oidcLogin())
                        .with(csrf())
                        .session(pendingGoogleSession()))
                .andExpect(status().isBadRequest());

        verify(userService, never()).createGoogleUser(GOOGLE_SUB, EMAIL, USERNAME);
    }

    @Test
    void createGoogleUser_shouldShowUsernameErrorAndKeepSession_whenUsernameExists()
            throws Exception {
        MockHttpSession session = pendingGoogleSession();
        when(userService.createGoogleUser(GOOGLE_SUB, EMAIL, USERNAME))
                .thenThrow(new UsernameAlreadyExistsException(USERNAME));

        mockMvc.perform(post("/choose-username")
                        .with(oidcLogin())
                        .with(csrf())
                        .session(session)
                        .param("username", USERNAME))
                .andExpect(status().isOk())
                .andExpect(view().name("choose-username"))
                .andExpect(model().attribute("suggestedUsername", USERNAME))
                .andExpect(model().attribute(
                        "usernameError",
                        "Username is already taken: " + USERNAME
                ));

        assertThat(session.getAttribute(PENDING_GOOGLE_SUB)).isEqualTo(GOOGLE_SUB);
        assertThat(session.getAttribute(PENDING_GOOGLE_EMAIL)).isEqualTo(EMAIL);
        verify(userService).createGoogleUser(GOOGLE_SUB, EMAIL, USERNAME);
    }

    @Test
    void createGoogleUser_shouldShowRegistrationErrorAndKeepSession_whenEmailExists()
            throws Exception {
        MockHttpSession session = pendingGoogleSession();
        when(userService.createGoogleUser(GOOGLE_SUB, EMAIL, USERNAME))
                .thenThrow(new EmailAlreadyExistsException(EMAIL));

        mockMvc.perform(post("/choose-username")
                        .with(oidcLogin())
                        .with(csrf())
                        .session(session)
                        .param("username", USERNAME))
                .andExpect(status().isOk())
                .andExpect(view().name("choose-username"))
                .andExpect(model().attribute(
                        "registrationError",
                        "A user with this email already exists: " + EMAIL
                ));

        assertThat(session.getAttribute(PENDING_GOOGLE_SUB)).isEqualTo(GOOGLE_SUB);
        assertThat(session.getAttribute(PENDING_GOOGLE_EMAIL)).isEqualTo(EMAIL);
    }

    @Test
    void createGoogleUser_shouldShowRegistrationErrorAndKeepSession_whenGoogleAccountIsLinked()
            throws Exception {
        MockHttpSession session = pendingGoogleSession();
        when(userService.createGoogleUser(GOOGLE_SUB, EMAIL, USERNAME))
                .thenThrow(new GoogleAccountAlreadyLinkedException());

        mockMvc.perform(post("/choose-username")
                        .with(oidcLogin())
                        .with(csrf())
                        .session(session)
                        .param("username", USERNAME))
                .andExpect(status().isOk())
                .andExpect(view().name("choose-username"))
                .andExpect(model().attribute(
                        "registrationError",
                        "This Google account is already linked to a user."
                ));

        assertThat(session.getAttribute(PENDING_GOOGLE_SUB)).isEqualTo(GOOGLE_SUB);
        assertThat(session.getAttribute(PENDING_GOOGLE_EMAIL)).isEqualTo(EMAIL);
    }

    private OidcLoginRequestPostProcessor googleLogin() {
        return oidcLogin().idToken(token -> token
                .subject(GOOGLE_SUB)
                .claim("email", EMAIL)
                .claim("name", FULL_NAME)
                .claim("picture", PICTURE));
    }

    private MockHttpSession pendingGoogleSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PENDING_GOOGLE_SUB, GOOGLE_SUB);
        session.setAttribute(PENDING_GOOGLE_EMAIL, EMAIL);
        return session;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class WebSecurityTestConfiguration {
    }
}
