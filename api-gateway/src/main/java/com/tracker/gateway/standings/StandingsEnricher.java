package com.tracker.gateway.standings;

import com.tracker.gateway.repository.UserRepository;
import com.tracker.gateway.user.DisplayNames;
import com.tracker.gateway.user.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds a {@code displayName} to each row of a leaderboard-style response.
 *
 * <p>Done in the gateway because it owns {@code User}: gamification-service only ever sees a
 * {@code userId}, and giving it names would mean a new cross-service data flow (and a backfill for
 * every existing user). One batched query per response, whatever the page size.
 */
@Component
public class StandingsEnricher {

    private final UserRepository userRepository;

    public StandingsEnricher(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @param rows the parsed JSON array from gamification-service; elements that are not objects
     *             with a numeric {@code userId} pass through untouched
     * @return the same rows (order preserved) with {@code displayName} added; {@code null} when the
     *         user no longer exists, so the shape stays uniform and the client can fall back
     */
    public List<Object> withDisplayNames(List<?> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map && map.get("userId") instanceof Number id) {
                ids.add(id.longValue());
            }
        }

        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            for (User user : userRepository.findAllById(ids)) {
                names.put(user.getId(), DisplayNames.of(user.getFirstName(), user.getLastName()));
            }
        }

        List<Object> enriched = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (row instanceof Map<?, ?> map && map.get("userId") instanceof Number id) {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                copy.put("displayName", names.get(id.longValue()));
                enriched.add(copy);
            } else {
                enriched.add(row);
            }
        }
        return enriched;
    }
}
