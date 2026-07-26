package xyz.hxwang.jointaccountmanager.spend;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Endpoints for statement import and spend analysis.
 *
 * <p>Uploads are addressed through the account they belong to, so which card a
 * file came from is stated rather than guessed — the parser then checks the
 * header and rejects a file sent to the wrong account.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SpendController {

    private final StatementImportService importService;
    private final SpendQueryService queryService;
    private final ClassificationService classificationService;
    private final BillTransactionService billTransactionService;

    // ------------------------------------------------------------- accounts

    /** Accounts with their staleness signals: which exports need downloading. */
    @GetMapping("/accounts")
    public List<SpendDtos.AccountView> accounts() {
        return queryService.accountsWithStaleness();
    }

    @PutMapping("/accounts/{id}/config")
    public SpendDtos.AccountView configureAccount(@PathVariable Long id,
                                                  @RequestBody SpendDtos.AccountConfigRequest request) {
        return queryService.configureAccount(id, request);
    }

    // ----------------------------------------------------------- statements

    @PostMapping("/accounts/{id}/statements")
    public StatementImportService.ImportSummary uploadStatement(@PathVariable Long id,
                                                                @RequestParam("file") MultipartFile file) {
        return importService.importStatement(id, file);
    }

    @GetMapping("/statements")
    public List<SpendDtos.ImportView> imports(@RequestParam(defaultValue = "20") int limit) {
        return queryService.recentImports(limit);
    }

    /** Removes an import and every transaction it brought in. */
    @DeleteMapping("/statements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undoImport(@PathVariable Long id) {
        importService.undoImport(id);
    }

    // --------------------------------------------------------- transactions

    @GetMapping("/transactions")
    public List<SpendDtos.TransactionView> transactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String cardMember,
            @RequestParam(required = false) String merchant,
            @RequestParam(defaultValue = "false") boolean includePayments,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return queryService.transactions(from, to, accountId, category, cardMember, merchant,
                includePayments, page, Math.min(size, 500));
    }

    @PutMapping("/transactions/{id}/category")
    public SpendDtos.TransactionView overrideCategory(@PathVariable Long id,
                                                      @RequestBody SpendDtos.CategoryOverrideRequest request) {
        return queryService.overrideCategory(id, request);
    }

    // ------------------------------------------------------------- analysis

    /** Totals for one calendar month, by category and by cardholder. */
    @GetMapping("/analysis/monthly")
    public SpendDtos.SpendSummary monthly(@RequestParam int year, @RequestParam int month) {
        if (month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be between 1 and 12");
        }
        YearMonth target = YearMonth.of(year, month);
        return queryService.summary(target.atDay(1), target.atEndOfMonth());
    }

    /** Totals over an arbitrary window. */
    @GetMapping("/analysis/summary")
    public SpendDtos.SpendSummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queryService.summary(from, to);
    }

    /** Net spend per month for the last N months, oldest first. */
    @GetMapping("/analysis/trend")
    public List<SpendDtos.MonthTotal> trend(@RequestParam(defaultValue = "6") int months) {
        return queryService.monthlyTrend(Math.min(Math.max(months, 1), 24));
    }

    /** Filter choices built from the data present. */
    @GetMapping("/analysis/filters")
    public SpendDtos.FilterOptions filterOptions() {
        return queryService.filterOptions();
    }

    @GetMapping("/analysis/by-merchant")
    public List<SpendDtos.MerchantTotal> byMerchant(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "20") int limit) {
        return queryService.topMerchants(from, to, Math.min(limit, 200));
    }

    /**
     * Generates the missing spend transactions for direct-pay bills that were
     * paid before this feature existed. Never touches the balance ledger, and
     * is safe to run more than once.
     */
    @PostMapping("/spend/backfill-bills")
    public BillTransactionService.BackfillResult backfillBills(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return billTransactionService.backfillPaidBills(from, dryRun);
    }

    // -------------------------------------------------- classification API
    //
    // Used by the categorize-spend Claude Code skill. Work is handed out per
    // merchant rather than per transaction, because one decision about Uber
    // settles every Uber row at once — and keeps settling them on future
    // imports.

    /** Merchants awaiting classification, busiest first, plus the valid categories. */
    @GetMapping("/spend/pending")
    public ClassificationService.PendingBatch pending(@RequestParam(defaultValue = "20") int limit) {
        return classificationService.nextBatch(Math.min(Math.max(limit, 1), 100));
    }

    /** Records classifier decisions; keyed by merchant, so re-running is safe. */
    @PutMapping("/spend/pending")
    public ClassificationService.ApplyResult applyDecisions(
            @RequestBody ClassificationRequest request) {
        return classificationService.apply(request.decisions());
    }

    /** Counts for the "waiting to be categorised" banner. */
    @GetMapping("/spend/pending/summary")
    public ClassificationService.PendingSummary pendingSummary() {
        return classificationService.summary();
    }

    public record ClassificationRequest(List<ClassificationService.Decision> decisions) {
    }

    // ----------------------------------------------------------- categories

    @GetMapping("/categories")
    public List<Category> categories() {
        return queryService.activeCategories();
    }
}
