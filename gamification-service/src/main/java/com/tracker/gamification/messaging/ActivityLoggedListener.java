package com.tracker.gamification.messaging;

import com.tracker.contracts.event.ActivityLoggedEvent;
import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.service.LevelTrackerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class ActivityLoggedListener {

    private static final Logger log = LoggerFactory.getLogger(ActivityLoggedListener.class);

    private final LevelTrackerService levelTrackerService;
    private final ProcessedEventRepository processedEventRepository;

    public ActivityLoggedListener(LevelTrackerService levelTrackerService,
                                  ProcessedEventRepository processedEventRepository) {
        this.levelTrackerService = levelTrackerService;
        this.processedEventRepository = processedEventRepository;
    }

    @RabbitListener(queues = "${messaging.queue}")
    @Transactional
    public void onActivityLogged(ActivityLoggedEvent event) {
        String key = String.valueOf(event.logId());

        // Fast path for sequential redelivery.
        if (processedEventRepository.existsById(key)) {
            log.debug("Duplicate event {} ignored", key);
            return;
        }

        // Guard FIRST: the unique PK on processed_event serializes concurrent duplicates.
        // If a racing delivery already inserted this key, THIS save throws and the whole
        // @Transactional method rolls back (XP not applied) -> message redelivered ->
        // existsById now true -> skipped. XP is therefore applied exactly once.
        //
        // Issue #82: saveAndFlush (not save) forces the INSERT to hit the DB right here, at this
        // line, instead of at end-of-transaction flush -- so the guard genuinely runs before the
        // XP mutation below rather than relying on an unrelated flushAutomatically elsewhere.
        // ProcessedEvent.isNew() is hardcoded true (see that class), so this is a real INSERT and
        // a genuine duplicate throws DataIntegrityViolationException here, not a silent UPDATE.
        processedEventRepository.saveAndFlush(new ProcessedEvent(key, LocalDateTime.now()));

        levelTrackerService.save(event.userId(),
                new LevelTrackerRequestDTO(event.activityId(), event.xpEarned()));
    }
}