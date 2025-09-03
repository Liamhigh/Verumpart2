package com.verum.omnis.core;

public class JurisdictionManager {
    public static String getCurrentJurisdictionCode() {
        // In production, detect via locale, SIM, IP (client-side only), or user choice.
        return "ZAF"; // South Africa default for demo
    }
}