package com.poc.lab.batch.deputy.dto;

public record GetDeputyDocumentRequest(
        String docId,
        String docType,
        String userId
) {
}
