package xyz.hxwang.jointaccountmanager.spend;

/**
 * Whether an account arrives with an itemised statement, and if so whose format
 * it is. NONE means the bill is paid straight from chequing with no breakdown,
 * so marking it paid generates a single transaction instead of importing one.
 */
public enum StatementFormat {
    NONE, CHASE, AMEX
}
