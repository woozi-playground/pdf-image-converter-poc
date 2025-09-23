package com.poc.lab.batch.deputy.service;

import com.poc.lab.batch.deputy.dto.GetDeputyDocumentRequest;
import com.poc.lab.batch.deputy.dto.GetDeputyDocumentResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class BtService {

    private static final String BT_BASE_URL = "http://localhost:8080";
    private final RestClient btClient = RestClient.builder().baseUrl(BT_BASE_URL).build();


    public GetDeputyDocumentResponse deputyDocument(final String userId, final String docId, final String docType) {
        return btClient.post()
                .uri("/api/v1/deputy/document")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GetDeputyDocumentRequest(docId, docType, userId))
                .retrieve()
                .toEntity(GetDeputyDocumentResponse.class)
                .getBody();
    }

}
