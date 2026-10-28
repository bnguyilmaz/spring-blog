package com.bengu.springblog.services;

import com.bengu.springblog.exceptions.LastFmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
class LastFmAuthorizationServiceTest {
    static final String TOKEN = "0123456789abcdef0123456789abcdef";
    LastFmProfileService profiles;
    LastFmAuthorizationService service;
    MockRestServiceServer server;
    MockHttpSession session;
    Clock clock = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        profiles = mock(LastFmProfileService.class);
        service = new LastFmAuthorizationService(profiles);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "apiSecret", "test-secret");
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.com/2.0/");
        ReflectionTestUtils.setField(service, "callbackUrl", "http://localhost:8084/lastfm/callback");
        ReflectionTestUtils.setField(service, "clock", clock);
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();
        session = new MockHttpSession();
    }

    @Test
    void redirectUsesLastFmAndCallbackWithUnpredictableStateWithoutSecret() {
        String first = service.begin("google-a", session);
        var params = UriComponentsBuilder.fromUriString(first).build().getQueryParams();
        assertThat(URI.create(first).getHost()).isEqualTo("www.last.fm");
        assertThat(params.getFirst("api_key")).isEqualTo("test-key");
        String callback = java.net.URLDecoder.decode(params.getFirst("cb"), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(callback).startsWith("http://localhost:8084/lastfm/callback?state=");
        assertThat(first).doesNotContain("test-secret", "google-a");
        String second = service.begin("google-a", session);
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void callbackExchangesSignedTokenAndSavesOnlyAuthorizedName() {
        String state = begin("google-a", session);
        // Independent known signature for api_key + method + token + shared secret.
        String signature = java.util.HexFormat.of().formatHex(org.springframework.util.DigestUtils.md5Digest(
                ("api_keytest-keymethodauth.getSessiontoken" + TOKEN + "test-secret").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        server.expect(queryParam("method", "auth.getSession"))
                .andExpect(queryParam("token", TOKEN))
                .andExpect(queryParam("api_sig", signature))
                .andRespond(withSuccess("{\"session\":{\"name\":\"AuthorizedAlice\",\"key\":\"private-session-key\",\"subscriber\":0}}", MediaType.APPLICATION_JSON));
        service.complete("google-a", state, TOKEN, session);
        verify(profiles).connectVerified("google-a", "AuthorizedAlice");
        assertThat(java.util.Collections.list(session.getAttributeNames())).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GHIJKLMNOPQRSTUVWXYZghijklmnopqr", "opaque-v2-token-with-a-different-length", "opaque+token/with=reserved&characters?"})
    void providerTokensAreOpaqueAndReachLastFmWithoutChangingTheirValue(String providerToken) {
        String state = begin("google-a", session);
        String signature = org.springframework.util.DigestUtils.md5DigestAsHex(
                ("api_keytest-keymethodauth.getSessiontoken" + providerToken + "test-secret").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        server.expect(queryParam("method", "auth.getSession"))
                .andExpect(request -> {
                    String encoded = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
                    assertThat(java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(providerToken);
                })
                .andExpect(queryParam("api_sig", signature))
                .andRespond(withSuccess("{\"session\":{\"name\":\"AuthorizedAlice\",\"key\":\"private-session-key\"}}", MediaType.APPLICATION_JSON));
        assertThatCode(() -> service.complete("google-a", state, providerToken, session)).doesNotThrowAnyException();
        verify(profiles).connectVerified("google-a", "AuthorizedAlice");
        server.verify();
    }

    @Test
    void callbackCannotBeUsedWithoutStartingAuthorization() {
        assertThatThrownBy(() -> service.complete("google-a", "invented-state", TOKEN, session))
                .isInstanceOf(LastFmException.class).hasMessageContaining("isteği bulunamadı");
        verifyNoInteractions(profiles);
        server.verify();
    }

    @Test
    void callbackCannotCrossBrowserSessions() {
        String state = begin("google-a", session);
        assertThatThrownBy(() -> service.complete("google-a", state, TOKEN, new MockHttpSession())).isInstanceOf(LastFmException.class);
        verifyNoInteractions(profiles);
    }

    @Test
    void callbackCannotLinkAccountAfterGoogleUserChanges() {
        String state = begin("google-a", session);
        assertThatThrownBy(() -> service.complete("google-b", state, TOKEN, session)).isInstanceOf(LastFmException.class);
        verifyNoInteractions(profiles);
    }

    @Test
    void callbackCannotUseWrongState() {
        begin("google-a", session);
        assertThatThrownBy(() -> service.complete("google-a", "wrong-state", TOKEN, session))
                .isInstanceOf(LastFmException.class).hasMessageContaining("eşleşmiyor");
        verifyNoInteractions(profiles);
    }

    @Test
    void startingAgainInvalidatesPreviousCallback() {
        String oldState = begin("google-a", session);
        begin("google-a", session);
        assertThatThrownBy(() -> service.complete("google-a", oldState, TOKEN, session)).isInstanceOf(LastFmException.class);
        verifyNoInteractions(profiles);
    }

    @Test
    void callbackExpiresAfterTenMinutes() {
        String state = begin("google-a", session);
        ReflectionTestUtils.setField(service, "clock", Clock.offset(clock, java.time.Duration.ofMinutes(11)));
        assertThatThrownBy(() -> service.complete("google-a", state, TOKEN, session))
                .isInstanceOf(LastFmException.class).hasMessageContaining("10 dakikalık");
        verifyNoInteractions(profiles);
    }

    @Test
    void upstreamSignatureErrorHasDistinctMessageAndSanitizedLog(CapturedOutput output) {
        String state = begin("google-a", session);
        server.expect(queryParam("method", "auth.getSession")).andRespond(
                withStatus(org.springframework.http.HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":13,\"message\":\"untrusted test-key test-secret " + TOKEN + "\"}"));
        assertThatThrownBy(() -> service.complete("google-a", state, TOKEN, session))
                .isInstanceOf(LastFmException.class).hasMessageContaining("ayarları");
        assertThat(output.getAll()).contains("UPSTREAM_API_ERROR", "apiCode=13", "httpStatus=403")
                .doesNotContain("test-key", "test-secret", TOKEN);
        verifyNoInteractions(profiles);
        server.verify();
    }

    @Test
    void consentErrorInSuccessfulHttpResponseIsDifferentFromExpiredLocalSession(CapturedOutput output) {
        String state = begin("google-a", session);
        server.expect(queryParam("method", "auth.getSession")).andRespond(withSuccess(
                "{\"error\":14,\"message\":\"not authorized\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.complete("google-a", state, TOKEN, session))
                .isInstanceOf(LastFmException.class).hasMessageContaining("izin verilmediğini");
        assertThat(output.getAll()).contains("UPSTREAM_API_ERROR", "apiCode=14");
        verifyNoInteractions(profiles);
    }

    @Test
    void missingCallbackStateHasSpecificRestartMessage(CapturedOutput output) {
        begin("google-a", session);
        assertThatThrownBy(() -> service.complete("google-a", "", TOKEN, session))
                .isInstanceOf(LastFmException.class).hasMessageContaining("işlem bilgisi eksik");
        assertThat(output.getAll()).contains("STATE_MISSING").doesNotContain("test-key", "test-secret", TOKEN);
        verifyNoInteractions(profiles);
    }

    @Test
    void callbackCanOnlyBeConsumedOnce() {
        String state = begin("google-a", session);
        server.expect(queryParam("method", "auth.getSession"))
                .andRespond(withSuccess("{\"session\":{\"name\":\"Alice\",\"key\":\"session-key\"}}", MediaType.APPLICATION_JSON));
        service.complete("google-a", state, TOKEN, session);
        assertThatThrownBy(() -> service.complete("google-a", state, TOKEN, session)).isInstanceOf(LastFmException.class);
        verify(profiles, times(1)).connectVerified("google-a", "Alice");
        server.verify();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"", "   ", "token\r\ninjection", "token\u0000injection"})
    void malformedOrMissingTokenDoesNotConnect(String token) {
        String state = begin("google-a", session);
        assertThatThrownBy(() -> service.complete("google-a", state, token, session)).isInstanceOf(LastFmException.class);
        verifyNoInteractions(profiles);
    }

    @Test
    void excessivelyLongTokenDoesNotReachLastFm() {
        String state = begin("google-a", session);
        assertThatThrownBy(() -> service.complete("google-a", state, "x".repeat(4097), session))
                .isInstanceOf(LastFmException.class);
        verifyNoInteractions(profiles);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"error\":14,\"message\":\"Not authorized\"}", "{}", "{\"session\":{\"name\":\"Alice\"}}", "{\"session\":{\"name\":\"\",\"key\":\"secret\"}}", "invalid-json"})
    void unsuccessfulOrIncompleteResponseCannotConnect(String body) {
        String state = begin("google-a", session);
        server.expect(queryParam("method", "auth.getSession")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.complete("google-a", state, TOKEN, session))
                .isInstanceOf(LastFmException.class).hasMessageNotContaining("test-secret").hasMessageNotContaining(TOKEN).hasMessageNotContaining("secret");
        verifyNoInteractions(profiles);
        server.verify();
    }

    @Test
    void networkFailureDoesNotSaveAndDoesNotLeakCredentials() {
        String state = begin("google-a", session);
        server.expect(queryParam("method", "auth.getSession")).andRespond(withServerError());
        assertThatThrownBy(() -> service.complete("google-a", state, TOKEN, session))
                .isInstanceOf(LastFmException.class).hasMessageNotContaining("test-key").hasMessageNotContaining("test-secret").hasMessageNotContaining(TOKEN);
        verifyNoInteractions(profiles);
        assertThatThrownBy(() -> service.complete("google-a", state, TOKEN, session)).isInstanceOf(LastFmException.class);
        server.verify();
    }

    @Test
    void missingConfigurationCannotStartAuthorization() {
        ReflectionTestUtils.setField(service, "apiSecret", "");
        assertThatThrownBy(() -> service.begin("google-a", session)).isInstanceOf(LastFmException.class);
        assertThat(java.util.Collections.list(session.getAttributeNames())).isEmpty();
    }

    private String begin(String subject, MockHttpSession browserSession) {
        String url = service.begin(subject, browserSession);
        String callback = java.net.URLDecoder.decode(UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("cb"), java.nio.charset.StandardCharsets.UTF_8);
        return UriComponentsBuilder.fromUriString(callback).build().getQueryParams().getFirst("state");
    }
}
