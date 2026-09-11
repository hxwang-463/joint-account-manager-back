package xyz.hxwang.jointaccountmanager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How far back the records list reaches.
 *
 * <p>The window's past end is the only part that moves; the future end is
 * deliberately open, so these tests are all about the start date the controller
 * hands the service.
 */
@ExtendWith(MockitoExtension.class)
class RecordControllerTest {

    @Mock
    private RecordService recordService;

    @InjectMocks
    private RecordController controller;

    /** The window start the controller asked for, relative to today. */
    private long daysBackRequested() {
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        verify(recordService).getAllRecordsAfterDate(from.capture());
        return java.time.temporal.ChronoUnit.DAYS.between(from.getValue(), LocalDate.now());
    }

    @Test
    @DisplayName("A caller that says nothing still gets the week it always got")
    void defaultsToOneWeek() {
        // The frontend shipped before this parameter existed, and an old cached
        // bundle will keep calling without it.
        controller.getRecords(RecordController.DEFAULT_DAYS);

        assertEquals(7, daysBackRequested());
    }

    @Test
    @DisplayName("Asking for more weeks widens the window rather than paging it")
    void widensToTheRequestedWindow() {
        // Each tap of "load earlier" adds a week and refetches the whole list,
        // because the running balance is computed across the entire set.
        controller.getRecords(28);

        assertEquals(28, daysBackRequested());
    }

    @Test
    @DisplayName("A window of zero or fewer days still returns today's records")
    void clampsUpToAtLeastOneDay() {
        controller.getRecords(0);

        // Anything less than a day would leave a table with no rows at all,
        // which reads as a failure rather than as an empty window.
        assertEquals(1, daysBackRequested());
    }

    @Test
    @DisplayName("A window of ten years is the most anyone can ask for")
    void clampsDownToTheMaximum() {
        // Unclamped, a hand-typed days=999999 would select the whole table.
        controller.getRecords(999_999);

        assertEquals(RecordController.MAX_DAYS, daysBackRequested());
    }

    @Test
    @DisplayName("A negative window cannot reach forward past today")
    void negativeWindowDoesNotInvertTheRange() {
        // Without the clamp this would ask for records after a date in the
        // future, quietly returning nothing.
        controller.getRecords(-30);

        assertEquals(1, daysBackRequested());
    }

    @Test
    @DisplayName("The records come back as the service returned them")
    void passesTheServiceResultThrough() {
        RecordDTO rent = RecordDTO.builder().id(1L).acctName("Rent").build();
        when(recordService.getAllRecordsAfterDate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(rent));

        assertEquals(List.of(rent), controller.getRecords(7));
    }
}
