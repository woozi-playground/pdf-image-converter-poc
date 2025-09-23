package com.poc.lab.batch.deputy.tasklet;

import com.poc.lab.batch.deputy.dto.AccountingSystemDocument;
import com.poc.lab.batch.deputy.dto.ChannelSystemDocument;
import com.poc.lab.batch.deputy.dto.GetDeputyDocumentResponse;
import com.poc.lab.batch.deputy.service.BtService;
import com.poc.lab.batch.deputy.service.EdmsService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.util.Strings;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.poc.lab.batch.deputy.CustomStringUtils.isBlank;
import static org.apache.logging.log4j.util.Strings.isNotBlank;

public class PdfConvertToImageProcessor implements ItemProcessor<AccountingSystemDocument, ChannelSystemDocument> {

    private static final int RENDER_DPI = 300;

    private final ThreadPoolTaskExecutor ioPool = ioBoundThreadPool();
    private final BtService btService = new BtService();
    private final EdmsService edmsService = new EdmsService();

    @Override
    public ChannelSystemDocument process(@Nonnull final AccountingSystemDocument item) throws Exception {
        final CompletableFuture<String> firstCertificateImagIdFuture = CompletableFuture
                .supplyAsync(() -> familyRelationCertificatePdf(item.userId(), item.docId1(), item.docType1()), ioPool)
                .thenApplyAsync(it -> bufferedImage(it, RENDER_DPI), ioPool)
                .thenApplyAsync(it -> edmsService.upload(it, item.userId(), item.docId1(), item.docType1()), ioPool)
                .handle((firstImageId, throwable) -> throwable == null ? firstImageId : "");

        final CompletableFuture<String> secondCertificateImagIdFuture = CompletableFuture
                .supplyAsync(() -> familyRelationCertificatePdf(item.userId(), item.docId2(), item.docType2()), ioPool)
                .thenApplyAsync(it -> bufferedImage(it, RENDER_DPI), ioPool)
                .thenApplyAsync(it -> edmsService.upload(it, item.userId(), item.docId2(), item.docType2()), ioPool)
                .handle((firstImageId, throwable) -> throwable == null ? firstImageId : "");

        firstCertificateImagIdFuture.join();
        secondCertificateImagIdFuture.join();

        final String firstCertificateImagId = firstCertificateImagIdFuture.get();
        final String secondCertificateImagId = secondCertificateImagIdFuture.get();

        if (isBlank(firstCertificateImagId) && isBlank(secondCertificateImagId)) {
            return ChannelSystemDocument.defaults(item);
        }
        if (isNotBlank(firstCertificateImagId) && isBlank(secondCertificateImagId)) {
            edmsService.delete(firstCertificateImagId);
            return ChannelSystemDocument.defaults(item);
        }
        if (isBlank(firstCertificateImagId) && isNotBlank(secondCertificateImagId)) {
            edmsService.delete(secondCertificateImagId);
            return ChannelSystemDocument.defaults(item);
        }
        return new ChannelSystemDocument(
                item.userId(),
                item.docId1(),
                item.docType1(),
                firstCertificateImagId,
                item.docId2(),
                item.docType2(),
                secondCertificateImagId,
                UUID.randomUUID().toString()
        );
    }

    private String familyRelationCertificatePdf(String userId, String docId, String docType) {
        try {
            return Optional.ofNullable(btService.deputyDocument(userId, docId, docType))
                    .map(GetDeputyDocumentResponse::pdf)
                    .filter(Strings::isNotBlank)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage bufferedImage(final String pdf, final int dpi) {
        try {
            try (var doc = Loader.loadPDF(Base64.getDecoder().decode(pdf))) {
                if (doc.getNumberOfPages() <= 0) {
                    return null;
                }
                final PDFRenderer renderer = new PDFRenderer(doc);
                return renderer.renderImageWithDPI(0, dpi, ImageType.RGB);
            }
        } catch (Exception e) {
            return null;
        }
    }

    public ThreadPoolTaskExecutor ioBoundThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("batch-io-");
        executor.initialize();
        return executor;
    }


    @PreDestroy
    public void cleanup() {
        if (ioPool != null) {
            ioPool.shutdown();
        }
    }
}
