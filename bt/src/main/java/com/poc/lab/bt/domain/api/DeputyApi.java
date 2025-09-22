package com.poc.lab.bt.domain.api;

import com.poc.lab.bt.domain.application.DeputyService;
import com.poc.lab.bt.domain.application.command.GetDeputyDocumentCommand;
import com.poc.lab.bt.domain.domain.DeputyDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/")
public class DeputyApi {

    private final DeputyService deputyService;

    public DeputyApi(final DeputyService deputyService) {
        this.deputyService = deputyService;
    }

    @PostMapping("/deputy/document")
    public ResponseEntity<DeputyDocument> document(@RequestBody final GetDeputyDocumentRequest request) {
        final DeputyDocument document = deputyService.document(new GetDeputyDocumentCommand(
                request.docId, request.docType, request.userId
        ));
        return ResponseEntity.ok(document);
    }

    record GetDeputyDocumentRequest(
            String docId,
            String docType,
            String userId
    ) {
    }
}
