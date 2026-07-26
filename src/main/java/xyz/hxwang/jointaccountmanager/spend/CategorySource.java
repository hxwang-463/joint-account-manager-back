package xyz.hxwang.jointaccountmanager.spend;

/** Where a transaction's category came from, so manual fixes are not overwritten. */
public enum CategorySource {
    /** Chosen by the classifier skill. */
    AI,
    /** Set by hand; authoritative, and never overwritten by the classifier. */
    USER,
    /** Not classified yet, or a row that needs no classifying (a card payment). */
    DEFAULT
}
