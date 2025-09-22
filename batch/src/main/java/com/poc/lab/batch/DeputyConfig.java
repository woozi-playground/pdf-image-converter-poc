package com.poc.lab.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.lab.batch.deputy.ImageByteUtils;
import com.poc.lab.batch.deputy.dto.AccountingSystemDocument;
import com.poc.lab.batch.deputy.dto.ChannelSystemDocument;
import com.poc.lab.batch.deputy.dto.GetDeputyDocumentRequest;
import com.poc.lab.batch.deputy.dto.GetDeputyDocumentResponse;
import com.poc.lab.batch.deputy.reader.CommaSeparatedObjectStreamItemReader;
import com.poc.lab.batch.deputy.writer.ChannelLineDelimitedJsonItemWriter;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.util.Strings;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.support.SynchronizedItemStreamWriter;
import org.springframework.batch.item.support.builder.SynchronizedItemStreamReaderBuilder;
import org.springframework.batch.item.support.builder.SynchronizedItemStreamWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.poc.lab.batch.deputy.CustomStringUtils.isBlank;
import static com.poc.lab.batch.deputy.CustomStringUtils.stringOrBlank;
import static java.util.Objects.isNull;

@Configuration
public class DeputyConfig {

    private static final String JOB_NAME = "deputyDocumentJob";
    private static final String STEP_NAME = "courtDocumentFileStep";

    private static final String EDMS_BASE_URL = "http://localhost:8082";
    private static final String BT_BASE_URL = "http://localhost:8080";

    private static final int RENDER_DPI = 300;
    private final RestClient edmsClient = RestClient.builder().baseUrl(EDMS_BASE_URL).build();
    private final RestClient btClient = RestClient.builder().baseUrl(BT_BASE_URL).build();

    private ExecutorService ioPool;

    @Bean
    public Job deputyDocumentJob(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(deputyDocumentFileStep(jobRepository, txManager))
                .build();
    }

    @Bean
    public Step deputyDocumentFileStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<AccountingSystemDocument, ChannelSystemDocument>chunk(30, txManager)
                .reader(deputyDocumentReader())
                .processor(deputyDocumentProcessor())
                .writer(deputyDocumentWriter())
                .taskExecutor(chunkExecutor())  // 청크 레벨 병렬화!
                .build();
    }

    @Bean
    public TaskExecutor chunkExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setThreadNamePrefix("chunk-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ItemStreamReader<AccountingSystemDocument> deputyDocumentReader() {
        final CommaSeparatedObjectStreamItemReader<AccountingSystemDocument> delegate =
                new CommaSeparatedObjectStreamItemReader<>(
                        new FileSystemResource("batch/src/main/resources/from/accounting"),
                        new ObjectMapper(),
                        AccountingSystemDocument.class
                );

        return new SynchronizedItemStreamReaderBuilder<AccountingSystemDocument>()
                .delegate(delegate)
                .build();
    }

    private ItemProcessor<? super AccountingSystemDocument, ? extends ChannelSystemDocument> deputyDocumentProcessor() {
        return (ItemProcessor<AccountingSystemDocument, ChannelSystemDocument>) item -> {
            final CompletableFuture<String> pdf1Future = CompletableFuture.supplyAsync(() -> deputyDocumentPdf(btClient, item.userId(), item.docId1(), item.docType1()), ioPool);
            final CompletableFuture<String> pdf2Future = CompletableFuture.supplyAsync(() -> deputyDocumentPdf(btClient, item.userId(), item.docId2(), item.docType2()), ioPool);

            pdf1Future.join();
            pdf2Future.join();

            final String pdf1 = pdf1Future.get();
            final String pdf2 = pdf2Future.get();
            if (isBlank(pdf1) || isBlank(pdf2)) {
                return ChannelSystemDocument.defaults(item);
            }

            BufferedImage img1 = null;
            BufferedImage img2 = null;
            try {
                final CompletableFuture<BufferedImage> image1 = CompletableFuture.supplyAsync(() -> pdfBase64ToImageFirstPage(pdf1, RENDER_DPI), ioPool);
                final CompletableFuture<BufferedImage> image2 = CompletableFuture.supplyAsync(() -> pdfBase64ToImageFirstPage(pdf2, RENDER_DPI), ioPool);
                img1 = image1.join();
                img2 = image2.join();
                if (isNull(img1) || isNull(img2)) {
                    return ChannelSystemDocument.defaults(item);
                }
                final List<Runnable> compensations = new ArrayList<>();
                try {
                    final String edmsId1 = uploadToEdms(edmsClient, img1, item.userId(), item.docId1(), item.docType1());
                    if (edmsId1 == null) {
                        return ChannelSystemDocument.defaults(item);
                    }
                    compensations.add(() -> deleteFromEdms(edmsClient, edmsId1));
                    final String edmsId2 = uploadToEdms(edmsClient, img2, item.userId(), item.docId2(), item.docType2());
                    if (edmsId2 == null) {
                        compensations.forEach(Runnable::run); // edmsId1 삭제
                        return ChannelSystemDocument.defaults(item);
                    }
                    compensations.add(() -> deleteFromEdms(edmsClient, edmsId2));
                    return new ChannelSystemDocument(
                            item.userId(), item.docId1(), item.docType1(), edmsId1, item.docId2(), item.docType2(), edmsId2, UUID.randomUUID().toString()
                    );
                } catch (Exception e) {
                    compensations.forEach(Runnable::run); // 역순 보상
                    throw e;
                }
            } finally {
                if (img1 != null) {
                    img1.flush();
                    img1 = null;
                }
                if (img2 != null) {
                    img2.flush();
                    img2 = null;
                }
            }
        };
    }

    private String deputyDocumentPdf(RestClient client, String userId, String docId, String docType) {
        try {
            return Optional.ofNullable(deputyDocument(client, userId, docId, docType))
                    .map(GetDeputyDocumentResponse::pdf)
                    .filter(Strings::isNotBlank)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static GetDeputyDocumentResponse deputyDocument(final RestClient client, final String userId, final String docId, final String docType) {
        return client.post()
                .uri("/api/v1/deputy/document")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GetDeputyDocumentRequest(docId, docType, userId))
                .retrieve()
                .toEntity(GetDeputyDocumentResponse.class)
                .getBody();
    }

    private BufferedImage pdfBase64ToImageFirstPage(String base64Pdf, int dpi) {
        try {
            final byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);
            try (var doc = Loader.loadPDF(pdfBytes)) {
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

    private String uploadToEdms(final RestClient client, final BufferedImage image, final String userId, final String docId, final String docType) {
        try {
            final byte[] png = ImageByteUtils.png(image);
            return client.post()
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

    @Bean
    public SynchronizedItemStreamWriter<ChannelSystemDocument> deputyDocumentWriter() {
        final ChannelLineDelimitedJsonItemWriter<ChannelSystemDocument> delegate = new ChannelLineDelimitedJsonItemWriter<>(
                new FileSystemResource("batch/build/resources/main/to/channel"),
                new ObjectMapper(),
                ChannelSystemDocument.class);

        return new SynchronizedItemStreamWriterBuilder<ChannelSystemDocument>()
                .delegate(delegate)
                .build();
    }

    private void deleteFromEdms(RestClient client, String edmsId) {
        try {
            client.post()
                    .uri("/delete/files/" + edmsId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
        }
    }

    @Bean
    public ExecutorService imageConversionExecutor() {
        this.ioPool = Executors.newFixedThreadPool(30);
        return this.ioPool;
    }

    @PreDestroy
    public void cleanup() throws InterruptedException {
        if (ioPool != null) {
            ioPool.shutdown();
            if (!ioPool.awaitTermination(60, TimeUnit.SECONDS)) {
                ioPool.shutdownNow();
            }
        }
    }
}
