package com.verum.omnis.core;

import android.content.Context;

import com.verum.omnis.ai.RulesEngine;
import com.verum.omnis.ai.RnDController;
import com.verum.omnis.ai.BusinessFraudExtractor;
import com.verum.omnis.forensic.RecoveryLedger;

import org.json.JSONObject;

import java.io.File;

public class AnalysisEngine {

    public static class ForensicReport {
        public String evidenceHash;
        public double riskScore;
        public String jurisdiction;
        public String[] topLiabilities;
        public String blockchainAnchor;
        public JSONObject behavioralProfile;
        public RecoveryLedger.Entry ledgerEntry; // optional
    }

    public static ForensicReport analyze(Context context, File file) {
        ForensicReport report = new ForensicReport();

        // 1. Hash
        try {
            report.evidenceHash = HashUtil.sha512File(file);
        } catch (Exception e) {
            report.evidenceHash = "HASH_ERROR";
        }

        // 2. Behavioral heuristics (quick stub + mock profile)
        report.riskScore = BehavioralAnalyzer.quickScore(file.getName());
        report.behavioralProfile = BehavioralAnalyzer.mockProfile();

        // 3. Jurisdiction
        report.jurisdiction = JurisdictionManager.getCurrentJurisdictionCode();

        // 4. Blockchain anchor (stubbed eth:// URI)
        report.blockchainAnchor = BlockchainService.anchor(report.evidenceHash);

// 5. Rules engine + R&D feedback
        try {
            RulesEngine.Result rr = RulesEngine.analyzeFile(context, file);
            report.riskScore = rr.riskScore;
            report.topLiabilities = rr.topLiabilities;

            // R&D experimental layer: weight boost and JSON diagnostics
            RnDController.Feedback fb = RnDController.synthesize(context, rr);
            report.riskScore = Math.min(1.0, rr.riskScore + fb.suggestedRiskWeightBoost);

            // Merge diagnostics into behavioralProfile if none set yet
            if (report.behavioralProfile == null || report.behavioralProfile.length() == 0) {
                report.behavioralProfile = fb.report;
            }

        } catch (Exception e) {
            report.topLiabilities = new String[]{"Rules engine failed: " + e.getMessage()};
        }

// 6. Fraud extraction + recovery ledger
        try {
            BusinessFraudExtractor.Extraction ex = BusinessFraudExtractor.parse(file);
            if (ex != null && ex.isBusiness) {
                double amountUsd = BusinessFraudExtractor.toBaseUsd(context, ex.currency, ex.amount);

                report.ledgerEntry = RecoveryLedger.create(
                        context,
                        "CASE-" + System.currentTimeMillis(), // simple caseId
                        ex.amount,            // original amount
                        amountUsd,            // normalized USD amount
                        ex.currency,
                        ex.company,
                        "UNKNOWN",            // TODO: detect jurisdiction properly
                        report.evidenceHash,
                        "v5.2.6"
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        // 7. R&D (reserved for future)
        // TODO: RnDController.runExperimental(file)

        return report;
    }
}
