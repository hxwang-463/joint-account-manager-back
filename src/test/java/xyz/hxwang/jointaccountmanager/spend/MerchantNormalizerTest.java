package xyz.hxwang.jointaccountmanager.spend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The normaliser decides what counts as "the same merchant", so a decision made
 * once keeps applying. Anything that varies per purchase — an order id, a store
 * number, the date of the ride — has to come out, or the merchant is never
 * recognised again.
 */
class MerchantNormalizerTest {

    private final MerchantNormalizer normalizer = new MerchantNormalizer();

    @Test
    @DisplayName("Rides on different days collapse to one merchant")
    void stripsEmbeddedDates() {
        String a = normalizer.lookupKey("LYFT   *1 RIDE 07-14");
        String b = normalizer.lookupKey("LYFT   *1 RIDE 07-12");
        String c = normalizer.lookupKey("LYFT   *1 RIDE 07-10");
        assertEquals(a, b);
        assertEquals(b, c);
        assertFalse(a.matches(".*\\d{1,2}[-/]\\d{1,2}.*"), "no date should survive: " + a);
    }

    @Test
    @DisplayName("Delivery orders with a date and an index collapse too")
    void stripsDateWithOrderIndex() {
        assertEquals(
                normalizer.lookupKey("DOORDASH*07/21-3 ORDER"),
                normalizer.lookupKey("DOORDASH*07/02-2 ORDER"));
    }

    @Test
    @DisplayName("Per-purchase order ids collapse")
    void stripsRandomOrderIds() {
        assertEquals(
                normalizer.lookupKey("Amazon.com*UG43F3G23"),
                normalizer.lookupKey("Amazon.com*2Y7DA9IY3"));
    }

    @Test
    @DisplayName("Genuinely different merchants stay apart")
    void keepsDistinctMerchantsDistinct() {
        assertNotEquals(normalizer.lookupKey("UBER"), normalizer.lookupKey("UBER EATS"));
        assertNotEquals(
                normalizer.lookupKey("WHOLEFDS ROG 10820"),
                normalizer.lookupKey("PUBLIX"));
    }

    @Test
    @DisplayName("A payment-app prefix does not split one merchant in two")
    void stripsPaymentPrefixes() {
        assertEquals(
                normalizer.lookupKey("AplPay PUBLIX"),
                normalizer.lookupKey("PUBLIX"));
    }

    @Test
    @DisplayName("A descriptor that is entirely noise still yields a key")
    void neverReturnsEmpty() {
        assertFalse(normalizer.lookupKey("12345 678901").isBlank());
    }
}
