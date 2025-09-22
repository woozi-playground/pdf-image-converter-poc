
package com.poc.lab.bt.domain.application;


import com.poc.lab.bt.domain.application.command.GetDeputyDocumentCommand;
import com.poc.lab.bt.domain.domain.Court;
import com.poc.lab.bt.domain.domain.DeputyDocument;
import org.springframework.stereotype.Service;

@Service
public class DeputyService {

    private final Court court;

    public DeputyService(final Court court) {
        this.court = court;
    }

    public DeputyDocument document(final GetDeputyDocumentCommand command) {
        final DeputyDocument document = court.document(command.docId(), command.docType());
        document.setUserId(command.userId());
        return document;
    }
}
