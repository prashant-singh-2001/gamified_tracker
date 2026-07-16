package com.tracker.gamification.messaging;

import com.tracker.gamification.dto.LevelTrackerRequestDTO;
import com.tracker.gamification.service.LevelTrackerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Activity Logged Listener Tests")
class ActivityLoggedListenerTest {

    @Mock
    private LevelTrackerService levelTrackerService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private ActivityLoggedListener listener;

    @BeforeEach
    void setUp() {
        listener = new ActivityLoggedListener(levelTrackerService, processedEventRepository);
    }

    @Test
    @DisplayName("first delivery records the event processed BEFORE applying XP via LevelTrackerService")
    void onActivityLogged_firstDelivery_appliesXpAndRecordsProcessed() {
        ActivityLoggedEvent event = new ActivityLoggedEvent(42L, 1L, 2L, 30.0);
        when(processedEventRepository.existsById("42")).thenReturn(false);

        listener.onActivityLogged(event);

        InOrder inOrder = inOrder(processedEventRepository, levelTrackerService);
        inOrder.verify(processedEventRepository).save(argThat(pe -> "42".equals(pe.getIdempotencyKey())));
        inOrder.verify(levelTrackerService).save(eq(1L), any(LevelTrackerRequestDTO.class));

        ArgumentCaptor<LevelTrackerRequestDTO> captor = ArgumentCaptor.forClass(LevelTrackerRequestDTO.class);
        verify(levelTrackerService).save(eq(1L), captor.capture());
        assertEquals(2L, captor.getValue().activityId());
        assertEquals(30.0, captor.getValue().xp(), 1e-9);
    }

    @Test
    @DisplayName("duplicate delivery is skipped — XP is never applied twice")
    void onActivityLogged_duplicateDelivery_skipped() {
        ActivityLoggedEvent event = new ActivityLoggedEvent(42L, 1L, 2L, 30.0);
        when(processedEventRepository.existsById("42")).thenReturn(true);

        listener.onActivityLogged(event);

        verify(processedEventRepository, never()).save(any());
        verify(levelTrackerService, never()).save(anyLong(), any());
    }
}
