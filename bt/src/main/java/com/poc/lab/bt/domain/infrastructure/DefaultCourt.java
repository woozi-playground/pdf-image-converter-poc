package com.poc.lab.bt.domain.infrastructure;

import com.poc.lab.bt.domain.domain.Court;
import com.poc.lab.bt.domain.domain.DeputyDocument;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Component
public class DefaultCourt implements Court {

    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:8081")
            .build();

    @Override
    public DeputyDocument document(final String docId, final String docType) {
        final ResDocument resDocument = restClient.post()
                .uri(URI.create("/documents/" + docType))
                .accept(MediaType.APPLICATION_JSON)
                .body(new ReqDocument(docId))
                .retrieve()
                .toEntity(ResDocument.class)
                .getBody();
        return new DeputyDocument(docId, docType, resDocument.pdf);
    }

    record ReqDocument(
            String docId
    ) {
    }

    record ResDocument(
            String pdf
    ) {
    }
}
