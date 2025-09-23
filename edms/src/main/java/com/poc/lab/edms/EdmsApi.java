package com.poc.lab.edms;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

/**
 * EDMS API (모의 구현)
 * - PNG 바이너리 또는 Base64 PNG를 받아 resources 하위에 저장
 * - 저장 성공 시 UUID 기반 식별키 반환
 */
@RestController
@RequestMapping("/files")
public class EdmsApi {

    // 바이너리 PNG 업로드
    @PostMapping(consumes = MediaType.IMAGE_PNG_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> uploadPng(
            @RequestHeader(value = "X-UserId", required = false) String userId,
            @RequestHeader(value = "X-DocId", required = false) String docId,
            @RequestHeader(value = "X-DocType", required = false) String docType,
            InputStream requestBody
    ) {
        final int i = new Random().nextInt(1, 100);
        if(i % 66 == 0) {
            throw new RuntimeException("EDMS Error !");
        }
        try {
            BufferedImage image = ImageIO.read(requestBody);
            if (image == null) {
                return ResponseEntity.badRequest().body("invalid image");
            }
            String id = saveImageToResources(image, userId, docId, docType);
            return id != null ? ResponseEntity.ok(id) : ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Base64 PNG 업로드 (필요 시 사용)
    @PostMapping(path = "/base64", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> uploadBase64Png(
            @RequestHeader(value = "X-UserId", required = false) String userId,
            @RequestHeader(value = "X-DocId", required = false) String docId,
            @RequestHeader(value = "X-DocType", required = false) String docType,
            @RequestBody String base64
    ) {
        try {
            if (!StringUtils.hasText(base64)) {
                return ResponseEntity.badRequest().body("empty body");
            }
            byte[] bytes = Base64.getDecoder().decode(base64.trim());
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
                BufferedImage image = ImageIO.read(bis);
                if (image == null) {
                    return ResponseEntity.badRequest().body("invalid base64 image");
                }
                String id = saveImageToResources(image, userId, docId, docType);
                return id != null ? ResponseEntity.ok(id) : ResponseEntity.internalServerError().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("decode error");
        }
    }

    @PostMapping("/delete/{id}")
    public ResponseEntity<Integer> deleteFiles(@PathVariable final String id) {
        try {
            final File baseDir = resolveWritableResourcesDir();
            final File edmsDir = new File(baseDir, "static/edms");
            if (!edmsDir.exists() || !edmsDir.isDirectory()) {
                return ResponseEntity.notFound().build();
            }
            final File target = new File(edmsDir, id + ".png");
            if (!target.exists()) {
                return ResponseEntity.notFound().build();
            }
            boolean deleted = target.delete();
            if (deleted) {
                return ResponseEntity.ok(1);
            }
            return ResponseEntity.ok(0);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // 실제 저장 로직: resources 하위 디렉토리에 파일 저장 후 UUID 반환
    private String saveImageToResources(BufferedImage image, String userId, String docId, String docType) throws IOException {
        File baseDir = resolveWritableResourcesDir();
        File edmsDir = new File(baseDir, "static/edms");
        if (!edmsDir.exists() && !edmsDir.mkdirs()) {
            return null;
        }
        String uuid = UUID.randomUUID().toString();
        String fileName = uuid + ".png";
        File out = new File(edmsDir, fileName);
        if (!ImageIO.write(image, "png", out)) {
            return null;
        }
        if (!out.exists() || out.length() == 0) {
            return null;
        }
        return uuid;
    }

    private static File resolveWritableResourcesDir() throws IOException {
        // Gradle 기본 출력 위치 우선 시도
        File gradleBuildResources = new File("edms/build/resources/main");
        if (gradleBuildResources.isDirectory() || gradleBuildResources.mkdirs()) {
            return gradleBuildResources;
        }
        // Maven 기본 출력 위치 시도
        File mavenTargetClasses = new File("edms/target/classes");
        if (mavenTargetClasses.isDirectory() || mavenTargetClasses.mkdirs()) {
            return mavenTargetClasses;
        }
        // 최후 대안: 실행 디렉토리 하위 폴더
        File fallback = new File("edms-runtime-resources");
        if (!fallback.exists()) {
            Files.createDirectories(fallback.toPath());
        }
        return fallback;
    }
}
