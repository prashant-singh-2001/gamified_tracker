package com.tracker.activity.repository;

import com.tracker.activity.dao.ActivityLog;
import com.tracker.activity.domain.ActivityLogQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the optional filters for GET /activitylog/user/{id}.
 *
 * <p>A {@link Specification} rather than a JPQL query with {@code (:x IS NULL OR col = :x)}: that
 * idiom binds untyped NULLs, which Postgres rejects ("could not determine data type of parameter")
 * and H2 accepts, so it would pass every test here and fail in production. A specification only adds
 * a predicate for a filter that was actually supplied.
 */
public final class ActivityLogSpecifications {

    private ActivityLogSpecifications() {
    }

    public static Specification<ActivityLog> matching(Long userId, ActivityLogQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Ownership is part of the query, never a post-filter.
            predicates.add(cb.equal(root.get("userId"), userId));
            if (query.activityId() != null) {
                predicates.add(cb.equal(root.get("activity").get("id"), query.activityId()));
            }
            if (query.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), query.from().atStartOfDay()));
            }
            if (query.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startTime"), query.to().atTime(LocalTime.MAX)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
