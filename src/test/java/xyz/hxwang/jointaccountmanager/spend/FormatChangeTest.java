package xyz.hxwang.jointaccountmanager.spend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What happens when a bank changes its export.
 *
 * <p>The failure that matters is not a rejected file — it is an accepted one
 * that quietly drops rows. Parsers used to skip any row they could not read, so
 * a renamed column produced an import that reported success while losing money
 * from the totals. These tests hold the line that a file is read completely or
 * not at all.
 */
class FormatChangeTest {

    private final ChaseStatementParser chase = new ChaseStatementParser();
    private final CitiStatementParser citi = new CitiStatementParser();

    private Reader csv(String body) {
        return new StringReader(body);
    }

    @Test
    @DisplayName("A row missing its description fails the file instead of vanishing from it")
    void partialRowLossIsRefused() {
        String body = """
                Transaction Date,Post Date,Description,Category,Type,Amount,Memo
                07/25/2026,07/25/2026,GOOD ONE,Food & Drink,Sale,-5.75,
                07/25/2026,07/25/2026,,Shopping,Sale,-42.10,
                07/25/2026,07/25/2026,GOOD TWO,Shopping,Sale,-9.99,
                """;
        Exception thrown = assertThrows(Exception.class, () -> chase.parse(csv(body)));
        assertTrue(thrown.getMessage().contains("Description"),
                "the message should name the column: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("row"),
                "the message should name the row: " + thrown.getMessage());
    }

    @Test
    @DisplayName("A row missing its amount fails the file")
    void missingAmountIsRefused() {
        String body = """
                Transaction Date,Post Date,Description,Category,Type,Amount,Memo
                07/25/2026,07/25/2026,STARBUCKS,Food & Drink,Sale,,
                """;
        assertThrows(Exception.class, () -> chase.parse(csv(body)));
    }

    @Test
    @DisplayName("Citi: a row with neither a debit nor a credit fails the file")
    void citiRowWithNoAmountIsRefused() {
        String body = """
                Status,Date,Description,Debit,Credit,Member Name
                Cleared,05/15/2026,SWEETGREEN,,,HUIXUAN WANG
                """;
        Exception thrown = assertThrows(Exception.class, () -> citi.parse(csv(body)));
        assertTrue(thrown.getMessage().toLowerCase().contains("debit"), thrown.getMessage());
    }

    @Test
    @DisplayName("Every parser declares the columns it reads, and its own fixture satisfies them")
    void requiredColumnsAreDeclaredAndSatisfied() {
        record Case(StatementParser parser, List<String> headers) {
        }
        List<Case> cases = List.of(
                new Case(chase, List.of("Transaction Date", "Post Date", "Description", "Category",
                        "Type", "Amount", "Memo")),
                new Case(new AmexStatementParser(), List.of("Date", "Description", "Card Member",
                        "Account #", "Amount", "Extended Details", "Reference", "Category")),
                new Case(new BoaStatementParser(), List.of("Posted Date", "Reference Number",
                        "Payee", "Address", "Amount")),
                new Case(citi, List.of("Status", "Date", "Description", "Debit", "Credit",
                        "Member Name")),
                new Case(new DiscoverStatementParser(), List.of("Trans. Date", "Post Date",
                        "Description", "Amount", "Category")));

        for (Case testCase : cases) {
            List<String> required = testCase.parser().requiredColumns();
            assertFalse(required.isEmpty(),
                    testCase.parser().format() + " declares no required columns");
            List<String> missing = required.stream()
                    .filter(column -> !testCase.headers().contains(column))
                    .toList();
            assertTrue(missing.isEmpty(),
                    testCase.parser().format() + " requires columns its own format does not have: "
                            + missing);
        }
    }
}
