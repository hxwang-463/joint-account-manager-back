package xyz.hxwang.jointaccountmanager.spend;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads one bank's CSV export into {@link ParsedTxn}s.
 *
 * <p>Implementations are responsible for normalising away the differences
 * between banks — most importantly the sign convention, which is inverted
 * between Chase and Amex. Everything downstream sees a positive amount plus an
 * explicit {@link Direction}.
 */
public interface StatementParser {

    StatementFormat format();

    /** True when this file's header row looks like this bank's export. */
    boolean matchesHeader(List<String> headers);

    /**
     * Every column this parser reads. All must be present, or the file is
     * rejected before a single row is stored.
     *
     * <p>{@link #matchesHeader} only looks at enough columns to tell the banks
     * apart; it is deliberately loose so a cosmetic change does not stop
     * recognition. This is the strict list, and it exists because a bank
     * quietly renaming a column used to produce a partial import that reported
     * success — rows with no readable value were skipped and never counted.
     */
    List<String> requiredColumns();

    List<ParsedTxn> parse(Reader reader) throws IOException;

    /**
     * Shared helpers. Kept here rather than in a utility class so the parsers
     * read top to bottom without hopping between files.
     */
    final class Support {

        private Support() {
        }

        static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("M/d/uuuu");

        static CSVFormat csvFormat() {
            return CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .get();
        }

        static CSVParser open(Reader reader) throws IOException {
            return CSVParser.builder().setReader(reader).setFormat(csvFormat()).get();
        }

        /**
         * Exports are occasionally written with a byte-order mark, which would
         * otherwise become part of the first header's name and break lookups.
         */
        static String stripBom(String value) {
            return value != null && value.startsWith("﻿") ? value.substring(1) : value;
        }

        static String get(CSVRecord record, String column) {
            if (!record.isMapped(column) || !record.isSet(column)) {
                return null;
            }
            String value = record.get(column);
            return value == null || value.isBlank() ? null : value.trim();
        }

        /**
         * Reads a column that must have a value, naming the row when it does
         * not. Never returns null: a row that reaches a parser has content, so
         * a missing required field means the format is not what we think it is,
         * and dropping the row would lose money silently.
         */
        static String require(CSVRecord record, String column) {
            String value = get(record, column);
            if (value == null) {
                throw new IllegalStateException(
                        "row " + record.getRecordNumber() + " has no value in the '" + column
                                + "' column");
            }
            return value;
        }

        static LocalDate parseDate(String value) {
            return value == null ? null : LocalDate.parse(value.trim(), US_DATE);
        }

        /** Tolerates thousands separators, currency symbols and parenthesised negatives. */
        static BigDecimal parseAmount(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing amount");
            }
            String cleaned = value.trim().replace(",", "").replace("$", "").replace(" ", "");
            boolean parenthesised = cleaned.startsWith("(") && cleaned.endsWith(")");
            if (parenthesised) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            BigDecimal parsed = new BigDecimal(cleaned);
            return parenthesised ? parsed.negate() : parsed;
        }

        /**
         * Identity for a bank that publishes no transaction id of its own.
         *
         * <p>The occurrence index matters: two genuinely separate identical
         * purchases on the same day would otherwise hash alike and the second
         * would be silently discarded as a duplicate. Counting repeats within
         * the file gives each its own key, and a re-upload of the same rows
         * reproduces the same counts, so overlapping imports still collapse.
         */
        static String hashKey(LocalDate date, BigDecimal amount, String description,
                              Map<String, Integer> seen) {
            String base = date + "|" + amount.toPlainString() + "|" + description;
            int occurrence = seen.merge(base, 1, Integer::sum);
            return "h:" + sha256(base + "|" + occurrence);
        }

        static Map<String, Integer> newOccurrenceCounter() {
            return new HashMap<>();
        }

        /**
         * Recognises a card payoff from its description.
         *
         * <p>Only Chase and Discover label these explicitly; the rest leave it
         * to be inferred. Getting it wrong is the most damaging mistake a
         * parser can make — a single missed autopay counted as spending was
         * larger than every purchase on that statement combined.
         */
        static boolean looksLikePayment(String description) {
            String lower = description.toLowerCase();
            return lower.contains("autopay")
                    || lower.contains("auto-pmt")
                    || lower.contains("directpay")
                    || lower.contains("payment - thank")
                    || lower.contains("payment thank")
                    || lower.contains("payment received")
                    || lower.contains("online payment")
                    || lower.contains("electronic payment")
                    || (lower.contains("payment") && lower.contains("thank you"));
        }

        static String sha256(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(hash.length * 2);
                for (byte b : hash) {
                    hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                    hex.append(Character.forDigit(b & 0xF, 16));
                }
                return hex.toString();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }
    }
}
