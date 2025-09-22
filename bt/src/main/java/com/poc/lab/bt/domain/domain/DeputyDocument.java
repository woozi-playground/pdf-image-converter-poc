package com.poc.lab.bt.domain.domain;

public class DeputyDocument {
    private String docId;
    private String docType;
    private String userId;
    private String pdf;

    public DeputyDocument() {
    }

    public DeputyDocument(final String docId, final String docType, final String pdf) {
        this.docId = docId;
        this.docType = docType;
        this.pdf = pdf;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getDocId() {
        return docId;
    }

    public String getDocType() {
        return docType;
    }

    public String getUserId() {
        return userId;
    }

    public String getPdf() {
        return pdf;
    }
}
