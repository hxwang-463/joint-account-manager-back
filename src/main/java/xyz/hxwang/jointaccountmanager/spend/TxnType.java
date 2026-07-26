package xyz.hxwang.jointaccountmanager.spend;

public enum TxnType {
    PURCHASE,
    REFUND,
    /** Paying off the card from chequing — a transfer, never spending. */
    PAYMENT,
    FEE,
    ADJUSTMENT
}
