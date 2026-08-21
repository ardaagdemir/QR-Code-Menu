package com.qrmenu.customersession;

import com.qrmenu.customersession.repository.TableVisitRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableVisitCleanupSchedulerTest {

    @Mock
    private TableVisitRepository repository;

    @Mock
    private TableVisitCleanupCloser closer;

    @Test
    void oneVisitFailureDoesNotStopTheRemainingBatch() {
        UUID failingVisitId = UUID.randomUUID();
        UUID healthyVisitId = UUID.randomUUID();
        TableVisit failingVisit = mock(TableVisit.class);
        TableVisit healthyVisit = mock(TableVisit.class);
        when(failingVisit.getId()).thenReturn(failingVisitId);
        when(healthyVisit.getId()).thenReturn(healthyVisitId);
        when(repository.findAllByClosedAtIsNullAndLastActivityAtBefore(any(Instant.class)))
                .thenReturn(List.of(failingVisit, healthyVisit));
        doThrow(new IllegalStateException("simulated close failure"))
                .when(closer)
                .closeVisit(failingVisitId);

        new TableVisitCleanupScheduler(repository, closer).closeStaleTableVisits();

        var calls = inOrder(closer);
        calls.verify(closer).closeVisit(failingVisitId);
        calls.verify(closer).closeVisit(healthyVisitId);
    }
}
