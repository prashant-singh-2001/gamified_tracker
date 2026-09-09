package com.tracker.activity.repository;

import com.tracker.activity.dao.Activity;
import com.tracker.activity.dao.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
public class ActivityRepositoryTest {

    @Autowired
    private ActivityRepository activityRepository;

    private static Activity activity(String name, boolean active) {
        return Activity.builder()
                .name(name)
                .category(Category.WORK)
                .xpMultiplier(1.0)
                .active(active)
                .description("d")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testFindByName() {
        Activity saved = activityRepository.save(activity("X", true));

        var opt = activityRepository.findByName("X");
        assertTrue(opt.isPresent());
        assertEquals(saved.getId(), opt.get().getId());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("#84: findByName is unfiltered -- returns an inactive row "
            + "too, since ActivityLogServiceImpl/ActivityNameResolutionService/NaturalLogServiceImpl "
            + "depend on that")
    void testFindByName_returnsInactiveRow() {
        activityRepository.save(activity("Retired", false));

        var opt = activityRepository.findByName("Retired");
        assertTrue(opt.isPresent());
        assertFalse(opt.get().isActive());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("#84: findByNameAndActiveTrue returns an active row")
    void testFindByNameAndActiveTrue_returnsActiveRow() {
        Activity saved = activityRepository.save(activity("Running", true));

        var opt = activityRepository.findByNameAndActiveTrue("Running");
        assertTrue(opt.isPresent());
        assertEquals(saved.getId(), opt.get().getId());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("#84: findByNameAndActiveTrue returns empty for a "
            + "soft-deleted row, even though it exists -- this is the actual fix under test")
    void testFindByNameAndActiveTrue_excludesInactiveRow() {
        activityRepository.save(activity("Retired", false));

        var opt = activityRepository.findByNameAndActiveTrue("Retired");
        assertTrue(opt.isEmpty());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("#84: findAllByActiveTrue excludes inactive rows while "
            + "returning active ones -- the catalog-listing fix under test")
    void testFindAllByActiveTrue_excludesInactiveRows() {
        activityRepository.save(activity("Active One", true));
        activityRepository.save(activity("Retired", false));

        List<Activity> result = activityRepository.findAllByActiveTrue();

        assertEquals(1, result.size());
        assertEquals("Active One", result.get(0).getName());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("#84: findAll (unfiltered) still returns both -- "
            + "ActivityNameResolutionService's fuzzy-suggestion catalog read depends on this")
    void testFindAll_isUnfiltered() {
        activityRepository.save(activity("Active One", true));
        activityRepository.save(activity("Retired", false));

        List<Activity> result = activityRepository.findAll();

        assertEquals(2, result.size());
    }
}

