package com.bengu.springblog.services;

import com.bengu.springblog.exceptions.LastFmException;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Service
public class LastFmAuthorizationService {
    private static final String PENDING = LastFmAuthorizationService.class.getName() + ".pending";
    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(LastFmAuthorizationService.class);
    private static final Pattern API_ERROR_CODE = Pattern.compile("\"error\"\\s*:\\s*(\\d{1,3})(?!\\d)");
    private final SecureRandom random = new SecureRandom();
    @Value("${lastfm.api.key:}") private String apiKey;
    @Value("${lastfm.api.secret:}") private String apiSecret;
    @Value("${lastfm.base.url}") private String baseUrl;
    @Value("${lastfm.callback.url:http://localhost:8084/lastfm/callback}") private String callbackUrl;
    private Clock clock = Clock.systemUTC();
    private final RestTemplate restTemplate;
    private final LastFmProfileService profiles;

    public LastFmAuthorizationService(LastFmProfileService profiles) {
        this.profiles = profiles;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        restTemplate = new RestTemplate(factory);
    }

    public String begin(String subject, HttpSession session) {
        if (apiKey.isBlank() || apiSecret.isBlank() || subject == null || subject.isBlank()) {
            throw failure(Failure.CONFIGURATION_INVALID);
        }
        URI callback;
        try {
            callback = URI.create(callbackUrl);
            if (!callback.isAbsolute() || callback.getHost() == null
                    || callback.getRawQuery() != null || callback.getFragment() != null
                    || callback.getUserInfo() != null
                    || !("https".equals(callback.getScheme()) || ("http".equals(callback.getScheme())
                    && ("localhost".equals(callback.getHost()) || "127.0.0.1".equals(callback.getHost()))))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw failure(Failure.CONFIGURATION_INVALID);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String callbackWithState = UriComponentsBuilder.fromUri(callback).queryParam("state", state).build().toUriString();
        synchronized (session) {
            session.setAttribute(PENDING, new PendingAuthorization(state, subject, clock.instant(), new AtomicBoolean()));
        }
        return UriComponentsBuilder.fromUriString("https://www.last.fm/api/auth/")
                .queryParam("api_key", apiKey).queryParam("cb", callbackWithState)
                .build().encode().toUriString();
    }

    public void complete(String subject, String state, String token, HttpSession session) {
        synchronized (session) {
            Object stored = session.getAttribute(PENDING);
            if (!(stored instanceof PendingAuthorization pending)) {
                throw failure(Failure.REQUEST_MISSING);
            }
            if (state == null || state.isBlank()) {
                throw failure(Failure.STATE_MISSING);
            }
            if (!MessageDigest.isEqual(pending.state().getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))) {
                throw failure(Failure.STATE_MISMATCH);
            }
            session.removeAttribute(PENDING);
            if (!pending.used().compareAndSet(false, true)) throw failure(Failure.REQUEST_ALREADY_USED);
            if (!pending.subject().equals(subject)) throw failure(Failure.USER_CHANGED);
            if (!clock.instant().isBefore(pending.startedAt().plus(LIFETIME))) throw failure(Failure.REQUEST_EXPIRED);
            // Provider tokens are opaque; Last.fm validates them in auth.getSession.
            if (token == null || token.isBlank() || token.length() > 4096
                    || token.chars().anyMatch(Character::isISOControl)) {
                throw failure(Failure.TOKEN_INVALID);
            }
        }
        String signature = DigestUtils.md5DigestAsHex(
                ("api_key" + apiKey + "methodauth.getSessiontoken" + token + apiSecret).getBytes(StandardCharsets.UTF_8));
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("method", "auth.getSession").queryParam("api_key", apiKey)
                .queryParam("token", "{token}").queryParam("api_sig", signature).queryParam("format", "json")
                .encode().buildAndExpand(Map.of("token", token)).toUri();
        Map<?, ?> response;
        try {
            response = restTemplate.getForObject(uri, Map.class);
        } catch (RestClientResponseException exception) {
            var match = API_ERROR_CODE.matcher(exception.getResponseBodyAsString());
            int code = match.find() ? Integer.parseInt(match.group(1)) : 0;
            throw upstreamFailure(exception.getStatusCode().value(), code);
        } catch (RestClientException exception) {
            // Never log the exception message or response: either can contain credentials or tokens.
            log.warn("Last.fm authorization failed: reason=UPSTREAM_TRANSPORT_ERROR, exceptionType={}", exception.getClass().getSimpleName());
            throw new LastFmException("Last.fm doğrulama yanıtı alınamadı veya okunamadı. Lütfen bağlantıyı yeniden başlat.");
        }
        if (response != null && response.containsKey("error")) {
            int code = response.get("error") instanceof Number number ? number.intValue() : 0;
            throw upstreamFailure(200, code);
        }
        if (response == null || !(response.get("session") instanceof Map<?, ?> authorized)
                || !(authorized.get("name") instanceof String name) || name.isBlank() || name.length() > 50
                || !(authorized.get("key") instanceof String key) || key.isBlank()) {
            throw failure(Failure.RESPONSE_INVALID);
        }
        // Public listening endpoints need only the verified name. Do not retain the session key.
        profiles.connectVerified(subject, name);
    }

    private LastFmException failure(Failure reason) {
        log.warn("Last.fm authorization failed: reason={}", reason.name());
        return new LastFmException(reason.message);
    }

    private LastFmException upstreamFailure(int status, int code) {
        log.warn("Last.fm authorization failed: reason=UPSTREAM_API_ERROR, httpStatus={}, apiCode={}", status, code);
        String message = switch (code) {
            case 10, 13, 26 -> "Last.fm doğrulama isteğini reddetti. Uygulamanın Last.fm ayarları kontrol edilmeli.";
            case 14 -> "Last.fm bu bağlantı için izin verilmediğini bildirdi. Bağlantıyı yeniden başlatıp erişime izin ver.";
            case 15 -> "Last.fm doğrulama kodunun süresi dolmuş veya kod daha önce kullanılmış. Bağlantıyı yeniden başlat.";
            case 11, 16, 29 -> "Last.fm şu anda isteği işleyemiyor. Biraz bekleyip bağlantıyı yeniden başlat.";
            default -> "Last.fm doğrulama isteğini tamamlayamadı. Lütfen bağlantıyı yeniden başlat.";
        };
        return new LastFmException(message);
    }

    private enum Failure {
        CONFIGURATION_INVALID("Last.fm bağlantısı şu anda kullanılamıyor. Lütfen daha sonra tekrar dene."),
        REQUEST_MISSING("Başlatılan Last.fm bağlantı isteği bulunamadı. Uygulama yeniden başlamış veya oturum kapanmış olabilir. Bu sayfadaki düğmeden yeniden bağlan."),
        STATE_MISSING("Last.fm dönüşünde bağlantıya ait işlem bilgisi eksik. Bu sayfadaki düğmeden bağlantıyı yeniden başlat."),
        STATE_MISMATCH("Last.fm dönüşü son başlatılan bağlantıyla eşleşmiyor. Aynı tarayıcıda bu sayfadaki düğmeden yeniden bağlan."),
        REQUEST_ALREADY_USED("Bu Last.fm bağlantı isteği daha önce kullanılmış. Bağlantıyı yeniden başlat."),
        USER_CHANGED("Bağlantı sırasında giriş yapılan Google hesabı değişmiş. Kendi profilinden Last.fm bağlantısını yeniden başlat."),
        REQUEST_EXPIRED("Last.fm bağlantısının 10 dakikalık süresi doldu. Bu sayfadaki düğmeden yeniden bağlan."),
        TOKEN_INVALID("Last.fm geçerli bir doğrulama kodu göndermedi. Bağlantıyı yeniden başlatıp erişime izin ver."),
        RESPONSE_INVALID("Last.fm yanıtından doğrulanan hesap bilgileri alınamadı. Lütfen bağlantıyı yeniden başlat.");

        private final String message;
        Failure(String message) { this.message = message; }
    }

    private record PendingAuthorization(String state, String subject, Instant startedAt,
                                        AtomicBoolean used) implements Serializable {}
}
