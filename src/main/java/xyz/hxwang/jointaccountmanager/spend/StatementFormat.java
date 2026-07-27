package xyz.hxwang.jointaccountmanager.spend;

/**
 * Whether an account arrives with an itemised statement, and if so whose format
 * it is. NONE means the bill is paid straight from chequing with no breakdown,
 * so marking it paid generates a single transaction instead of importing one.
 *
 * <p>Each issuer exports differently, and not just cosmetically: Chase and BOA
 * write purchases as negative amounts, Amex and Discover as positive, and Citi
 * splits them across separate debit and credit columns. The sign is resolved
 * into an explicit direction at parse time so nothing downstream has to care.
 */
public enum StatementFormat {
    NONE, CHASE, AMEX, BOA, CITI, DISCOVER
}
