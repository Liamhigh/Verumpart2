package com.verum.omnis.tax;

import com.verum.omnis.core.JurisdictionManager;
import java.util.HashMap;
import java.util.Locale;

/**
 * Simple tax return estimator based on jurisdiction.
 *
 * <p>This module uses the current jurisdiction code to look up a baseline
 * income tax rate.  It then computes a discounted rate equal to half of the
 * baseline and estimates the tax due for a given income.  The rates are
 * illustrative only and should be replaced with jurisdiction‑specific tax
 * laws for real deployments.</p>
 */
public final class TaxReturnModule {
    /** Container for a computed tax return estimate. */
    public static class TaxEstimate {
        public String jurisdiction;
        public double baseRate;
        public double discountedRate;
        public double taxDue;
    }

    private static final HashMap<String, Double> BASE_RATES;
    static {
        BASE_RATES = new HashMap<>();
        BASE_RATES.put("ZAF", 0.20); // South Africa (illustrative)
        BASE_RATES.put("UAE", 0.00); // UAE no personal income tax
        BASE_RATES.put("EU",  0.25); // Generic EU baseline
    }

    private TaxReturnModule() {}

    /**
     * Estimate a personal income tax return for the given income at 50% of the
     * local baseline rate.
     *
     * @param ctx Android context used to derive the current jurisdiction
     * @param income annual taxable income in the local currency
     * @return a populated {@link TaxEstimate} describing the calculation
     */
    public static TaxEstimate estimate(android.content.Context ctx, double income) {
        TaxEstimate est = new TaxEstimate();
        String code = JurisdictionManager.getCurrentJurisdictionCode();
        if (code == null || code.trim().isEmpty()) code = "ZAF";
        code = code.toUpperCase(Locale.US);
        est.jurisdiction = code;
        double base = BASE_RATES.getOrDefault(code, 0.20);
        est.baseRate = base;
        est.discountedRate = base / 2.0;
        est.taxDue = income * est.discountedRate;
        return est;
    }
}