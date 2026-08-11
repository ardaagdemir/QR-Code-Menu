package com.qrmenu.shared;

/**
 * Integer minor-unit (kuruş) money value object - no floating point
 * (docs/product-requirements.md Section 5, confirmed rule). No currency field: the
 * system is single-currency (TRY, RECOMMENDED - Section 5) and multi-currency support
 * isn't even listed as future work in Section 7, so carrying a currency on every
 * instance would be unused weight, not forward-compatibility.
 *
 * First introduced in Milestone 4 (Section 9) for cart/order price computation. Entities
 * still persist plain `long` minor-unit columns (same convention as the menu module's
 * Product/BranchProduct) - Money is used at the computation site to make the arithmetic
 * explicit and safe, not as a JPA-mapped type.
 */
public record Money(long amountMinorUnits) {

    public static final Money ZERO = new Money(0);

    public Money {
        if (amountMinorUnits < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative: " + amountMinorUnits);
        }
    }

    public static Money ofMinorUnits(long amountMinorUnits) {
        return new Money(amountMinorUnits);
    }

    public Money plus(Money other) {
        return new Money(this.amountMinorUnits + other.amountMinorUnits);
    }

    public Money multipliedBy(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative: " + quantity);
        }
        return new Money(this.amountMinorUnits * quantity);
    }
}
