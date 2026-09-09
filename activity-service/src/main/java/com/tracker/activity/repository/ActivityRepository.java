package com.tracker.activity.repository;

import com.tracker.activity.dao.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    // Unfiltered on purpose -- ActivityLogServiceImpl (log-time InactiveActivityException guard),
    // ActivityNameResolutionService (fuzzy suggestions deliberately include inactive activities,
    // #7/#66), and NaturalLogServiceImpl (draft preview) all depend on seeing inactive rows here.
    // Do NOT add a WHERE clause to this method -- see findByNameAndActiveTrue below instead.
    Optional<Activity> findByName(String name);

    // #84: the catalog-read-only variants. Used ONLY by ActivityServiceImpl's getActivity/
    // getAllActivities -- every other consumer above must keep using the unfiltered methods.
    Optional<Activity> findByNameAndActiveTrue(String name);

    List<Activity> findAllByActiveTrue();
}
