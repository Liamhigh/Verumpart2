package com.verum.omnis.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.util.TypedValue;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.verum.omnis.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;

public class PDFSealer {

    public static class SealRequest {
        /**
         * Title displayed at the top of the generated report.  If null or empty,
         * "Forensic Report" will be used.
         */
        public String title;
        /**
         * Summary description shown beneath the title.  If null or empty, a
         * default message will be printed.
         */
        public String summary;
        /**
         * Whether to include a QR code in the bottom right corner.  Defaults
         * to {@code true} when unset.
         */
        public boolean includeQr = true;
        /**
         * Whether to include the certification block with tick and hash in the
         * bottom right.  Defaults to {@code true} when unset.
         */
        public boolean includeHash = true;
    }

    public static void generateSealedPdf(Context ctx, SealRequest req, File outFile) throws IOException {
        // Compute a unique hash for this report.  In a real implementation this
        // would incorporate the report's contents; here we use a timestamp for
        // demonstration purposes only.
        String fullHash = sha512(("VerumOmnis" + System.currentTimeMillis()).getBytes());
        String shortHash = truncate(fullHash, 8);

        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = doc.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        // Top‑center full‑color logo
        Bitmap logo = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_verum_logo);
        float logoWidth = 120;
        float logoHeight = 120;
        canvas.drawBitmap(Bitmap.createScaledBitmap(logo, (int) logoWidth, (int) logoHeight, true),
                (pageInfo.getPageWidth() - logoWidth) / 2f, 20f, null);

        // Semi‑transparent watermark in the centre
        Paint watermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        watermarkPaint.setAlpha(30);
        Bitmap wmLogo = Bitmap.createScaledBitmap(logo, 300, 300, true);
        canvas.drawBitmap(wmLogo, (pageInfo.getPageWidth() - 300) / 2f,
                (pageInfo.getPageHeight() - 300) / 2f, watermarkPaint);

        // Title & summary text
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF000000);
        textPaint.setTextSize(sp(ctx, 16));
        String title = (req != null && req.title != null && !req.title.trim().isEmpty())
                ? req.title : "Forensic Report";
        canvas.drawText(title, 40f, 170f, textPaint);
        textPaint.setTextSize(sp(ctx, 12));
        String summary = (req != null && req.summary != null && !req.summary.trim().isEmpty())
                ? req.summary : "No summary provided.";
        canvas.drawText(summary, 40f, 190f, textPaint);

        // Certification block (tick + truncated hash) – conditional
        boolean includeHash = req == null || req.includeHash;
        boolean includeQr = req == null || req.includeQr;
        textPaint.setTextSize(sp(ctx, 10));
        if (includeHash) {
            String certText = "\u2714 Patent Pending Verum Omnis  •  " + shortHash;
            float x = pageInfo.getPageWidth() - textPaint.measureText(certText) - 40f;
            float y = pageInfo.getPageHeight() - 40f;
            canvas.drawText(certText, x, y, textPaint);
        }

        // QR code above the cert block – conditional
        if (includeQr) {
            try {
                Bitmap qr = makeQr("verum://hash/" + shortHash);
                int q = 96;
                float x = pageInfo.getPageWidth() - q - 40f;
                float y = pageInfo.getPageHeight() - q - (includeHash ? 60f : 40f);
                canvas.drawBitmap(qr, x, y, null);
            } catch (WriterException e) {
                // Intentionally ignore QR generation failures; the PDF remains valid.
            }
        }

        doc.finishPage(page);
        FileOutputStream fos = new FileOutputStream(outFile);
        doc.writeTo(fos);
        fos.close();
        doc.close();
    }

    private static Bitmap makeQr(String text) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 256, 256);
        int w = bitMatrix.getWidth();
        int h = bitMatrix.getHeight();
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                bmp.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bmp;
    }

    private static float sp(Context ctx, float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, ctx.getResources().getDisplayMetrics());
    }

    private static String sha512(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(data);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }

    private static String truncate(String fullHash, int chars) {
        if (fullHash == null) return "";
        return fullHash.length() <= chars ? fullHash : fullHash.substring(0, chars);
    }
}