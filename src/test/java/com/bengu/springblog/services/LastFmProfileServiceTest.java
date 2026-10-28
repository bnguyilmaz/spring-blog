package com.bengu.springblog.services;

import com.bengu.springblog.entities.LastFmProfile;
import com.bengu.springblog.entities.User;
import com.bengu.springblog.repositories.LastFmProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LastFmProfileServiceTest {
    LastFmProfileRepository repository = mock(LastFmProfileRepository.class);
    UserService users = mock(UserService.class);
    LastFmProfileService service = new LastFmProfileService(repository, users);
    User alice = new User("alice", "alice@example.com", null);

    @Test
    void manuallyEnteredLegacyProfileIsNotVerified() {
        when(repository.findByUser_GoogleSub("alice-google"))
                .thenReturn(Optional.of(new LastFmProfile(alice, "unverified-name")));
        assertThat(service.usernameFor("alice-google")).isEmpty();
    }

    @Test
    void authorizedNameIsPersistedWithVerificationDateAgainstGoogleIdentity() {
        when(users.findByGoogleSub("alice-google")).thenReturn(Optional.of(alice));
        assertThatCode(() -> service.connectVerified("alice-google", "VerifiedAlice")).doesNotThrowAnyException();
        ArgumentCaptor<LastFmProfile> saved = ArgumentCaptor.forClass(LastFmProfile.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(alice);
        assertThat(saved.getValue().getLastFmUsername()).isEqualTo("VerifiedAlice");
        assertThat(ReflectionTestUtils.getField(saved.getValue(), "verifiedAt")).isNotNull();
    }

    @Test
    void authorizationUpdatesLegacyRecordInsteadOfCreatingAnotherOne() {
        LastFmProfile existing = new LastFmProfile(alice, "old-name");
        when(users.findByGoogleSub("alice-google")).thenReturn(Optional.of(alice));
        when(repository.findByUser_GoogleSub("alice-google")).thenReturn(Optional.of(existing));
        assertThatCode(() -> service.connectVerified("alice-google", "NewVerifiedName")).doesNotThrowAnyException();
        verify(repository).save(existing);
        assertThat(existing.getLastFmUsername()).isEqualTo("NewVerifiedName");
    }

    @Test
    void deletedOrUnregisteredUserCannotReceiveAnAuthorizedConnection() {
        assertThatThrownBy(() -> service.connectVerified("unknown-google", "VerifiedAlice"))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }
}
