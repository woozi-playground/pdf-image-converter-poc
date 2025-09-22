package com.poc.lab.bt.domain.application.command;

public record GetDeputyDocumentCommand(
        String docId,
        String docType,
        String userId
) {
}

