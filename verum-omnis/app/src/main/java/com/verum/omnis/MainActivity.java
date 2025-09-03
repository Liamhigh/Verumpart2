package com.verum.omnis;

import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.verum.omnis.core.AnalysisEngine;
import com.verum.omnis.core.PDFSealer;
import com.verum.omnis.core.MediaForensics;
import com.verum.omnis.ai.RnDMeshExchange;
import com.verum.omnis.ai.RnDController;
import com.verum.omnis.ai.RulesEngine;
import com.verum.omnis.security.IntegrityChecker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * MainActivity – Select File / Verify / Generate PDF
 * Unified reporting: Integrity + Forensic results in one report.
 */
public class MainActivity extends AppCompatActivity {

    private File selectedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button selectBtn = findViewById(R.id.selectBtn);
        Button verifyBtn = findViewById(R.id.verifyBtn);
        Button pdfBtn = findViewById(R.id.pdfBtn);
        ImageView logo = findViewById(R.id.logo);

        // File picker
        ActivityResultLauncher<String[]> filePicker =
                registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) {
                        selectedFile = copyUriToCache(uri);
                        showDialog("File Selected", "Selected: " + getFileName(uri));
                    }
                });

        selectBtn.setOnClickListener(v ->
                filePicker.launch(new String[]{"*/*"}));

        // Verify (now includes integrity + forensic in one report)
        verifyBtn.setOnClickListener(v -> {
            try {
                StringBuilder sb = new StringBuilder();

                // === System Integrity Section ===
                Map<String, String> results = IntegrityChecker.runChecks(this);
                sb.append("=== System Integrity ===\n");
                for (Map.Entry<String, String> e : results.entrySet()) {
                    sb.append(e.getKey()).append(" → ").append(e.getValue()).append("\n");
                }
                sb.append("\n");

                if (selectedFile == null) {
                    sb.append("No file selected. Forensic analysis skipped.\n");
                    showDialog("Unified Report", sb.toString());
                    return;
                }

                // === Forensic Analysis Section ===
                sb.append("=== Forensic Analysis ===\n");

                AnalysisEngine.ForensicReport report =
                        AnalysisEngine.analyze(this, selectedFile);

                sb.append("Hash: ").append(report.evidenceHash)
                        .append("\nRisk Score: ").append(report.riskScore)
                        .append("\nJurisdiction: ").append(report.jurisdiction)
                        .append("\nBlockchain: ").append(report.blockchainAnchor);

                if (report.topLiabilities != null) {
                    sb.append("\nTop Liabilities:");
                    for (String liab : report.topLiabilities) {
                        sb.append("\n• ").append(liab);
                    }
                }
                if (report.behavioralProfile != null) {
                    sb.append("\nBehavioral Profile:\n")
                            .append(report.behavioralProfile.toString(2));
                }
                if (report.ledgerEntry != null) {
                    sb.append("\nLedger Entry:");
                    sb.append("\n• Case ID: ").append(report.ledgerEntry.caseId);
                    sb.append("\n• Party: ").append(report.ledgerEntry.partyName);
                    sb.append("\n• Amount: ").append(report.ledgerEntry.fraudAmount)
                            .append(" ").append(report.ledgerEntry.currency);
                    sb.append("\n• Amount (USD): ").append(report.ledgerEntry.fraudAmountUsd);
                    sb.append("\n• Jurisdiction: ").append(report.ledgerEntry.partyJurisdiction);
                    if (report.ledgerEntry.sealedPdf != null) {
                        sb.append("\n• Sealed PDF: ").append(report.ledgerEntry.sealedPdf.getAbsolutePath());
                    }
                }

                // MediaForensics metadata
                HashMap<String, String> meta = MediaForensics.inspectFile(selectedFile);
                if (meta != null && !meta.isEmpty()) {
                    sb.append("\n\nFile Metadata:");
                    for (Map.Entry<String, String> entry : meta.entrySet()) {
                        sb.append("\n• ").append(entry.getKey())
                                .append(": ").append(entry.getValue());
                    }
                }

                // Show unified report
                showDialog("Unified Report", sb.toString());

                // === Mesh export (still runs silently in background) ===
                RnDController.Feedback fb =
                        RnDController.synthesize(this,
                                RulesEngine.analyzeFile(this, selectedFile));

                File meshFile = RnDMeshExchange.exportPacketToFile(this, fb);
                System.out.println("Mesh packet written: " + meshFile.getAbsolutePath());

                RnDMeshExchange.exportPacketByEmail(
                        this,
                        fb,
                        report,
                        "smtp.yourprovider.com", 587,
                        "automated_system@freethehustle.com",
                        "your_smtp_password",
                        "liam@example.com"
                );

            } catch (Exception e) {
                showDialog("Verify Failed", e.getMessage());
            }
        });

        // Generate PDF
        pdfBtn.setOnClickListener(v -> {
            try {
                File outFile = new File(getFilesDir(), "verum_output.pdf");

                PDFSealer.SealRequest req = new PDFSealer.SealRequest();
                req.title = "Verum Omnis Certification";
                req.summary = (selectedFile != null)
                        ? "Sealed report for: " + selectedFile.getName()
                        : "No input file attached.";
                req.includeQr = true;
                req.includeHash = true;

                PDFSealer.generateSealedPdf(this, req, outFile);
                showDialog("PDF Generated", outFile.getAbsolutePath());

            } catch (Exception e) {
                showDialog("PDF Generation Failed", e.getMessage());
            }
        });
    }

    private File copyUriToCache(Uri uri) {
        try {
            String name = getFileName(uri);
            File outFile = new File(getCacheDir(), name);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
            return outFile;
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy file: " + e.getMessage(), e);
        }
    }

    private String getFileName(Uri uri) {
        String result = "unknown";
        try (android.database.Cursor cursor =
                     getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    result = cursor.getString(nameIndex);
                }
            }
        }
        return result;
    }

    private void showDialog(String title, String message) {
        // Build scrollable TextView
        android.widget.TextView textView = new android.widget.TextView(this);
        textView.setText(message);
        textView.setPadding(40, 40, 40, 40);
        textView.setTextIsSelectable(true); // allow copy
        textView.setVerticalScrollBarEnabled(true);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(textView);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scrollView)  // instead of setMessage
                .setPositiveButton("OK", null)
                .setCancelable(true)
                .show();
    }

}
