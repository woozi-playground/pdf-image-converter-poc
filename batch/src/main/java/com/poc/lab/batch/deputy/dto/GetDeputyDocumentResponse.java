package com.poc.lab.batch.deputy.dto;

public record GetDeputyDocumentResponse(
        String docId,
        String docType,
        String userId,
        String pdf
) {
}
