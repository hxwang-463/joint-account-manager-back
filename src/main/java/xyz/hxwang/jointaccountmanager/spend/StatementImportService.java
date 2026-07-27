package xyz.hxwang.jointaccountmanager.spend;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import xyz.hxwang.jointaccountmanager.Account;
import xyz.hxwang.jointaccountmanager.AccountRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns an uploaded statement export into stored transactions.
 *
 * <p>Importing never touches the chequing balance. The balance moves when a
 * bill is marked paid; this table explains what that bill was made of. Keeping
 * them separate is what stops the same money being counted twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementImportService {

    private final AccountRepository accountRepository;
    private final BankTxnRepository bankTxns;
    private final StatementImportRepository imports;
    private final CategoryResolver categoryResolver;
    private final MerchantNormalizer merchantNormalizer;
    private final List<StatementParser> parsers;

    @Getter
    @Builder
    public static class ImportSummary {
        private final Long importId;
        private final Long accountId;
        private final String accountName;
        private final String filename;
        private final LocalDate periodStart;
        private final LocalDate periodEnd;
        private final int rowCount;
        private final int inserted;
        private final int duplicates;
        private final int payments;
        /** Rows that arrived with no known merchant and are waiting on the classifier. */
        private final int pendingClassification;
        private final boolean alreadyImported;
    }

    @Transactional
    public ImportSummary importStatement(Long accountId, MultipartFile file) {
        Account account = accountRepository.findById(accountId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No account with id " + accountId));

        if (account.getStatementFormat() == null || account.getStatementFormat() == StatementFormat.NONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account '" + account.getAcctName() + "' is not configured for statement imports. "
                            + "Set its statementFormat to one of CHASE, AMEX, BOA, CITI or DISCOVER first.");
        }

        byte[] bytes = read(file);
        String sha256 = StatementParser.Support.sha256(new String(bytes, StandardCharsets.UTF_8));

        /*
         * A file we have seen before is noted for reporting, but deliberately
         * not skipped.
         *
         * Skipping it looked like a harmless optimisation and was not: undoing
         * an earlier, overlapping import deletes rows that this file also
         * contains, and re-uploading is the obvious way to restore them. Short
         * circuiting on the hash made that silently do nothing — the upload
         * reported success while the rows stayed missing.
         *
         * Re-parsing costs milliseconds, and per-transaction dedup already
         * guarantees a genuine duplicate is not inserted twice, so correctness
         * never depended on this check.
         */
        boolean seenBefore = imports.findFirstByAccountIdAndFileSha256(accountId, sha256).isPresent();

        StatementParser parser = parserFor(account.getStatementFormat());
        rejectIfHeaderDisagrees(bytes, parser, account);

        List<ParsedTxn> parsed;
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            parsed = parser.parse(reader);
        } catch (IOException | RuntimeException e) {
            // Nothing is stored: parsing completes before the first row is
            // written, so a file this app cannot fully read is rejected whole
            // rather than imported in part.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read this file as a " + account.getStatementFormat() + " export — "
                            + e.getMessage()
                            + ". Nothing has been imported. If the file itself looks right, "
                            + account.getStatementFormat() + " may have changed its export format.");
        }
        if (parsed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No transactions found in this file");
        }

        StatementImport record = imports.save(StatementImport.builder()
                .accountId(accountId)
                .filename(file.getOriginalFilename())
                .fileSha256(sha256)
                .importedAt(LocalDateTime.now())
                .periodStart(parsed.stream().map(ParsedTxn::getTxnDate).min(Comparator.naturalOrder()).orElse(null))
                .periodEnd(parsed.stream().map(ParsedTxn::getTxnDate).max(Comparator.naturalOrder()).orElse(null))
                .rowCount(parsed.size())
                .status("RUNNING")
                .build());

        int inserted = 0;
        int duplicates = 0;
        int payments = 0;
        int pending = 0;

        for (ParsedTxn txn : parsed) {
            if (txn.getTxnType() == TxnType.PAYMENT) {
                payments++;
            }

            // Scoped to the account, so an overlapping export collapses while the
            // same amount on a different card stays its own transaction.
            if (bankTxns.findByAccountIdAndDedupKey(accountId, txn.getDedupKey()).isPresent()) {
                duplicates++;
                continue;
            }

            // Imports store what the bank sent. The only thing decided here is
            // whether this merchant has been classified before; if not the row
            // waits, rather than being given a guess that reads like a fact.
            String merchantKey = merchantNormalizer.lookupKey(txn.getDescriptionRaw());
            CategoryResolver.Resolution resolution =
                    categoryResolver.resolve(merchantKey, txn.getTxnType());
            if (!resolution.isProcessed()) {
                pending++;
            }

            bankTxns.save(BankTxn.builder()
                    .accountId(accountId)
                    .statementImportId(record.getId())
                    .txnDate(txn.getTxnDate())
                    .postDate(txn.getPostDate())
                    .descriptionRaw(trim(txn.getDescriptionRaw(), 255))
                    .merchant(trim(resolution.getMerchant(), 120))
                    .amount(txn.getAmount())
                    .direction(txn.getDirection())
                    .txnType(txn.getTxnType())
                    .category(resolution.getCategory())
                    .categorySource(resolution.getSource())
                    .processed(resolution.isProcessed())
                    .bankCategory(trim(txn.getBankCategory(), 80))
                    .cardMember(trim(txn.getCardMember(), 60))
                    .cardRef(trim(txn.getCardRef(), 20))
                    .sourceRef(trim(txn.getSourceRef(), 60))
                    .dedupKey(txn.getDedupKey())
                    .createdAt(LocalDateTime.now())
                    .build());
            inserted++;
        }

        record.setInsertedCount(inserted);
        record.setDuplicateCount(duplicates);
        record.setStatus("DONE");
        imports.save(record);

        log.info("Imported {} into account {}: {} rows, {} new, {} already present",
                file.getOriginalFilename(), account.getAcctName(), parsed.size(), inserted, duplicates);

        return ImportSummary.builder()
                .importId(record.getId()).accountId(accountId).accountName(account.getAcctName())
                .filename(record.getFilename())
                .periodStart(record.getPeriodStart()).periodEnd(record.getPeriodEnd())
                .rowCount(parsed.size()).inserted(inserted).duplicates(duplicates).payments(payments)
                .pendingClassification(pending).alreadyImported(seenBefore)
                .build();
    }

    /** Removes an import and everything it brought in, so a mistake is undoable. */
    @Transactional
    public void undoImport(Long importId) {
        StatementImport record = imports.findById(importId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No import with id " + importId));
        bankTxns.deleteByStatementImportId(importId);
        imports.delete(record);
        log.info("Undid import {} ({})", importId, record.getFilename());
    }

    private StatementParser parserFor(StatementFormat format) {
        return parsers.stream()
                .filter(p -> p.format() == format)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No parser registered for " + format));
    }

    /**
     * Catches uploading the right file to the wrong account. Without this the
     * import would appear to succeed while writing every transaction against
     * someone else's card.
     */
    private void rejectIfHeaderDisagrees(byte[] bytes, StatementParser parser, Account account) {
        List<String> headers;
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
             CSVParser csv = CSVParser.builder().setReader(reader)
                     .setFormat(CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())
                     .get()) {
            headers = csv.getHeaderNames().stream()
                    .map(StatementParser.Support::stripBom)
                    .toList();
        } catch (IOException | RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the header row: " + e.getMessage());
        }

        if (!parser.matchesHeader(headers)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This file does not look like a " + parser.format() + " export, which is what '"
                            + account.getAcctName() + "' is configured for. Its columns are: "
                            + String.join(", ", headers)
                            + ". Either this belongs to a different account, or "
                            + parser.format() + " has changed its export format.");
        }

        // Recognising the bank is not the same as being able to read it. This
        // is the strict check: a renamed or dropped column is caught here,
        // before anything is stored, rather than showing up as rows that
        // quietly failed to parse.
        List<String> missing = parser.requiredColumns().stream()
                .filter(column -> !headers.contains(column))
                .toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This " + parser.format() + " export is missing "
                            + (missing.size() == 1 ? "a column this app needs: " : "columns this app needs: ")
                            + String.join(", ", missing)
                            + ". Its columns are: " + String.join(", ", headers)
                            + ". " + parser.format() + " has probably changed its export format, so "
                            + "nothing has been imported — the parser needs updating first.");
        }
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file was uploaded");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the upload: " + e.getMessage());
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
