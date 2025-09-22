package com.poc.lab.court.domain.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;
import java.util.UUID;

@RestController
public class CourtApi {

    @PostMapping("/documents/{docType}")
    public ResponseEntity<CourtDocument> document(
            @PathVariable final String docType,
            @RequestBody final GetCourtDocumentRequest request
    ) throws InterruptedException {
        final int i = new Random().nextInt(1, 100);
        if(i % 66 == 0) {
            throw new RuntimeException("EDMS Error !");
        }
        final CourtDocument courtDocument = courtDocument(docType, request.docId);
        return ResponseEntity.ok(courtDocument);
    }

    private CourtDocument courtDocument(final String docType, final String docId) throws InterruptedException {
        Thread.sleep(new Random().nextInt(100, 3000));
        if (docType.equalsIgnoreCase("basic")) {
            String pdfContent = "%PDF-1.4\n1 0 obj\n<<\n/Type /Catalog\n/Pages 2 0 R\n>>\nendobj\n2 0 obj\n<<\n/Type /Pages\n/Kids [3 0 R]\n/Count 1\n>>\nendobj\n3 0 obj\n<<\n/Type /Page\n/Parent 2 0 R\n/Resources <<\n/Font <<\n/F1 4 0 R\n>>\n>>\n/MediaBox [0 0 612 792]\n/Contents 5 0 R\n>>\nendobj\n4 0 obj\n<<\n/Type /Font\n/Subtype /Type1\n/BaseFont /Helvetica\n>>\nendobj\n5 0 obj\n<< /Length 68 >>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Basic Document ID: " + docId + ") Tj\nET\nendstream\nendobj\nxref\ntrailer\n<<\n/Size 6\n/Root 1 0 R\n>>\nstartxref\n%%EOF";
            return new CourtDocument(java.util.Base64.getEncoder().encodeToString(pdfContent.getBytes()));
        }
        if (docType.equalsIgnoreCase("family")) {
            String pdfContent = "%PDF-1.4\n1 0 obj\n<<\n/Type /Catalog\n/Pages 2 0 R\n>>\nendobj\n2 0 obj\n<<\n/Type /Pages\n/Kids [3 0 R]\n/Count 1\n>>\nendobj\n3 0 obj\n<<\n/Type /Page\n/Parent 2 0 R\n/Resources <<\n/Font <<\n/F1 4 0 R\n>>\n>>\n/MediaBox [0 0 612 792]\n/Contents 5 0 R\n>>\nendobj\n4 0 obj\n<<\n/Type /Font\n/Subtype /Type1\n/BaseFont /Helvetica\n>>\nendobj\n5 0 obj\n<< /Length 69 >>\nstream\nBT\n/F1 12 Tf\n100 700 Td\n(Family Document ID: " + docId + ") Tj\nET\nendstream\nendobj\nxref\ntrailer\n<<\n/Size 6\n/Root 1 0 R\n>>\nstartxref\n%%EOF";
            return new CourtDocument(java.util.Base64.getEncoder().encodeToString(pdfContent.getBytes()));
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    record GetCourtDocumentRequest(
            String docId
    ) {
    }

    record CourtDocument(
            String pdf
    ) {
    }
}
