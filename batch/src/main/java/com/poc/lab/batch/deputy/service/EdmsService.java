package com.poc.lab.batch.deputy.service;

import com.poc.lab.batch.deputy.ImageByteUtils;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.awt.image.BufferedImage;

import static com.poc.lab.batch.deputy.CustomStringUtils.stringOrBlank;

public class EdmsService {

    private static final String EDMS_BASE_URL = "http://localhost:8082";
    private final RestClient edmsClient = RestClient.builder().baseUrl(EDMS_BASE_URL).build();

    public String upload(final BufferedImage image, final String userId, final String docId, final String docType) {
        try {
            final byte[] png = ImageByteUtils.png(image);
            return edmsClient.post()
                    .uri("/files")
                    .contentType(MediaType.IMAGE_PNG)
                    .header("X-UserId", stringOrBlank(userId))
                    .header("X-DocId", stringOrBlank(docId))
                    .header("X-DocType", stringOrBlank(docType))
                    .body(png)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public Integer delete(String edmsId) {
        try {
            return edmsClient.post()
                    .uri("/delete/files/" + edmsId)
                    .retrieve()
                    .toEntity(Integer.class)
                    .getBody();
        } catch (Exception ignored) {
            return 0;
        }
    }
}
