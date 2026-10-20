package com.tracker.gateway.standings;

import com.tracker.gateway.repository.UserRepository;
import com.tracker.gateway.user.Role;
import com.tracker.gateway.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandingsEnricherTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StandingsEnricher enricher;

    private static User user(long id, String first, String last) {
        User u = new User();
        u.setId(id);
        u.setFirstName(first);
        u.setLastName(last);
        u.setEmail(first.toLowerCase() + "@private.example");
        u.setPassword("$2a$secret-hash");
        u.setRole(Role.USER);
        return u;
    }

    private static Map<String, Object> row(Object rank, Object userId, double xp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rank", rank);
        m.put("userId", userId);
        m.put("totalXp", xp);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asRow(Object o) {
        return (Map<String, Object>) o;
    }

    @Test
    void addsDisplayNameToEachRow_keepingOrderAndExistingKeys() {
        when(userRepository.findAllById(Set.of(7L, 3L)))
                .thenReturn(List.of(user(3, "Grace", "Hopper"), user(7, "Ada", "Lovelace")));

        List<Object> out = enricher.withDisplayNames(List.of(row(1, 7, 900.0), row(2, 3, 450.5)));

        assertThat(out).hasSize(2);
        assertThat(asRow(out.get(0))).containsEntry("userId", 7).containsEntry("displayName", "Ada L.")
                .containsEntry("rank", 1).containsEntry("totalXp", 900.0);
        assertThat(asRow(out.get(1))).containsEntry("userId", 3).containsEntry("displayName", "Grace H.");
        assertThat(asRow(out.get(0)).keySet()).containsExactly("rank", "userId", "totalXp", "displayName");
    }

    @Test
    void oneBatchedLookupRegardlessOfRowCount_andDuplicatesCollapse() {
        when(userRepository.findAllById(Set.of(7L))).thenReturn(List.of(user(7, "Ada", "Lovelace")));

        enricher.withDisplayNames(List.of(row(1, 7, 1.0), row(2, 7, 1.0), row(3, 7, 1.0)));

        verify(userRepository).findAllById(Set.of(7L));
    }

    @Test
    void aUserWhoNoLongerExistsGetsANullDisplayName_notAMissingKey() {
        when(userRepository.findAllById(Set.of(99L))).thenReturn(List.of());

        List<Object> out = enricher.withDisplayNames(List.of(row(1, 99, 5.0)));

        assertThat(asRow(out.get(0))).containsKey("displayName").containsEntry("displayName", null);
    }

    @Test
    void neverLeaksEmailOrPasswordHashOrTheFullLastName() {
        when(userRepository.findAllById(Set.of(7L))).thenReturn(List.of(user(7, "Ada", "Lovelace")));

        List<Object> out = enricher.withDisplayNames(List.of(row(1, 7, 1.0)));

        assertThat(out.toString()).doesNotContain("private.example").doesNotContain("secret-hash")
                .doesNotContain("Lovelace");
    }

    @Test
    void rowsWithoutANumericUserIdPassThroughUntouched() {
        Map<String, Object> noId = new LinkedHashMap<>(Map.of("rank", 1));
        Map<String, Object> stringId = row(2, "7", 1.0);

        List<Object> out = enricher.withDisplayNames(List.of(noId, stringId, "not-an-object"));

        assertThat(out).containsExactly(noId, stringId, "not-an-object");
        assertThat(asRow(out.get(0))).doesNotContainKey("displayName");
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void emptyPageDoesNotQueryAtAll() {
        assertThat(enricher.withDisplayNames(List.of())).isEmpty();
        verify(userRepository, never()).findAllById(any());
    }
}
