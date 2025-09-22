package com.poc.lab.batch.deputy.dto;

public record AccountingSystemDocument(
        String userId,
        String docId1,
        String docType1,
        String edmsId1,
        String docId2,
        String docType2,
        String edmsId2,
        String guid
) {
}
