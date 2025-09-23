package com.poc.lab.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.lab.batch.deputy.dto.AccountingSystemDocument;
import com.poc.lab.batch.deputy.dto.ChannelSystemDocument;
import com.poc.lab.batch.deputy.reader.CommaSeparatedObjectStreamItemReader;
import com.poc.lab.batch.deputy.tasklet.PdfConvertToImageProcessor;
import com.poc.lab.batch.deputy.writer.ChannelLineDelimitedJsonItemWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class FamilyRelationshipCertificateReceiveBathJobConfig {

    private static final String JOB_NAME = "deputyDocumentJob";
    private static final String STEP_NAME = "courtDocumentFileStep";

    @Bean
    public Job deputyDocumentJob(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(deputyDocumentFileStep(jobRepository, txManager))
                .build();
    }

    @Bean
    public Step deputyDocumentFileStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<AccountingSystemDocument, ChannelSystemDocument>chunk(1, txManager)
                .reader(deputyDocumentReader())
                .processor(deputyDocumentProcessor())
                .writer(deputyDocumentWriter())
                .build();
    }

    @Bean
    public ItemStreamReader<AccountingSystemDocument> deputyDocumentReader() {
        return new CommaSeparatedObjectStreamItemReader<>(
                new FileSystemResource("batch/src/main/resources/from/accounting"),
                new ObjectMapper(),
                AccountingSystemDocument.class
        );
    }

    @Bean
    public ItemProcessor<? super AccountingSystemDocument, ChannelSystemDocument> deputyDocumentProcessor() {
        return new PdfConvertToImageProcessor();
    }

    @Bean
    public ChannelLineDelimitedJsonItemWriter<ChannelSystemDocument> deputyDocumentWriter() {
        return new ChannelLineDelimitedJsonItemWriter<>(
                new FileSystemResource("batch/build/resources/main/to/channel"),
                new ObjectMapper(),
                ChannelSystemDocument.class);
    }
}
