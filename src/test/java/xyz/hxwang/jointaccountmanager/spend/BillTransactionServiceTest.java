package xyz.hxwang.jointaccountmanager.spend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.hxwang.jointaccountmanager.Account;
import xyz.hxwang.jointaccountmanager.AccountRepository;
import xyz.hxwang.jointaccountmanager.Record;
import xyz.hxwang.jointaccountmanager.RecordRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Generated bill transactions, and the queue they must never join.
 *
 * <p>Rent and internet arrive with their category already decided, taken from
 * the account's configuration. They are therefore finished the moment they are
 * written, and the classifier has nothing to say about them.
 *
 * <p>The failure this guards against is silent. Two separate methods generate
 * these rows — one for a bill paid now, one backfilling bills paid before the
 * feature existed — and only the backfill set {@code processed}. Because the
 * field defaults to false, every bill the nightly job paid went into the
 * classification queue and stayed there: correctly categorised, counted in
 * every total, and still listed forever as awaiting review. Nothing threw and
 * no total was wrong, so only the pending count ever showed it.
 *
 * <p>Hence {@link #bothGenerationPathsProduceTheSameRow()}: the two paths
 * duplicate this logic, so the invariant worth holding is not that each is
 * individually right but that they cannot drift apart again.
 */
@ExtendWith(MockitoExtension.class)
class BillTransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private RecordRepository recordRepository;
    @Mock
    private BankTxnRepository bankTxns;

    @InjectMocks
    private BillTransactionService service;

    /** An account paid straight from chequing: no statement, category known up front. */
    private Account internetAccount() {
        return Account.builder()
                .id(7L)
                .acctName("Internet")
                .dayOfMonth(12)
                .statementFormat(StatementFormat.NONE)
                .defaultCategory("INTERNET")
                .build();
    }

    private Record paidInternetBill() {
        return Record.builder()
                .id(41L)
                .acctName("Internet")
                .date(LocalDate.of(2026, 7, 12))
                .amount(new BigDecimal("64.99"))
                .isPaid(true)
                .build();
    }

    private BankTxn captureSaved() {
        ArgumentCaptor<BankTxn> saved = ArgumentCaptor.forClass(BankTxn.class);
        verify(bankTxns).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("A bill paid tonight is not left waiting for the classifier")
    void paidBillIsNotQueuedForClassification() {
        when(accountRepository.findAll()).thenReturn(List.of(internetAccount()));
        when(bankTxns.findByRecordId(41L)).thenReturn(Optional.empty());

        service.onRecordPaid(paidInternetBill());

        BankTxn txn = captureSaved();
        assertTrue(txn.isProcessed(),
                "the category came from account configuration, so there is nothing to classify");
        assertEquals("INTERNET", txn.getCategory());
        assertEquals(CategorySource.DEFAULT, txn.getCategorySource());
    }

    @Test
    @DisplayName("Paying a bill now and backfilling it later produce the same row")
    void bothGenerationPathsProduceTheSameRow() {
        when(accountRepository.findAll()).thenReturn(List.of(internetAccount()));
        when(bankTxns.findByRecordId(41L)).thenReturn(Optional.empty());

        service.onRecordPaid(paidInternetBill());
        BankTxn live = captureSaved();
        reset(bankTxns);

        when(recordRepository.findAll()).thenReturn(List.of(paidInternetBill()));
        when(bankTxns.findByRecordId(41L)).thenReturn(Optional.empty());

        service.backfillPaidBills(null, false);
        BankTxn backfilled = captureSaved();

        // Whether a bill was paid tonight or reconstructed months later is a
        // detail of how it reached the ledger, never of what it is.
        assertEquals(backfilled.isProcessed(), live.isProcessed(),
                "the two generation paths disagree on whether the row needs classifying");
        assertEquals(backfilled.getCategory(), live.getCategory());
        assertEquals(backfilled.getCategorySource(), live.getCategorySource());
        assertEquals(backfilled.getDirection(), live.getDirection());
        assertEquals(backfilled.getTxnType(), live.getTxnType());
        assertEquals(backfilled.getAmount(), live.getAmount());
        assertEquals(backfilled.getDedupKey(), live.getDedupKey());
        assertEquals(backfilled.getMerchant(), live.getMerchant());
    }

    @Test
    @DisplayName("A generated row is never left without the category the column demands")
    void generatedRowAlwaysCarriesACategory() {
        when(accountRepository.findAll()).thenReturn(List.of(internetAccount()));
        when(bankTxns.findByRecordId(41L)).thenReturn(Optional.empty());

        service.onRecordPaid(paidInternetBill());

        // category is NOT NULL, so generating without one would fail at the
        // insert rather than at the guard.
        assertNotNull(captureSaved().getCategory());
    }

    @Test
    @DisplayName("An account still awaiting its category configuration generates nothing")
    void unconfiguredAccountGeneratesNothing() {
        Account unconfigured = Account.builder()
                .id(8L)
                .acctName("Internet")
                .dayOfMonth(12)
                .statementFormat(StatementFormat.NONE)
                .defaultCategory(null)
                .build();
        when(accountRepository.findAll()).thenReturn(List.of(unconfigured));

        service.onRecordPaid(paidInternetBill());

        verify(bankTxns, never()).save(any());
    }

    @Test
    @DisplayName("A card with a statement generates nothing, so its spend is not counted twice")
    void statementBackedAccountGeneratesNothing() {
        Account card = Account.builder()
                .id(9L)
                .acctName("Chase 0375")
                .dayOfMonth(3)
                .statementFormat(StatementFormat.CHASE)
                .defaultCategory("SHOPPING")
                .build();
        when(accountRepository.findAll()).thenReturn(List.of(card));

        service.onRecordPaid(Record.builder()
                .id(42L)
                .acctName("Chase 0375")
                .date(LocalDate.of(2026, 7, 3))
                .amount(new BigDecimal("120.00"))
                .isPaid(true)
                .build());

        verify(bankTxns, never()).save(any());
    }

    @Test
    @DisplayName("A bill already generated for is not generated for twice")
    void alreadyGeneratedBillIsSkipped() {
        when(accountRepository.findAll()).thenReturn(List.of(internetAccount()));
        when(bankTxns.findByRecordId(41L)).thenReturn(Optional.of(new BankTxn()));

        service.onRecordPaid(paidInternetBill());

        verify(bankTxns, never()).save(any());
    }
}
