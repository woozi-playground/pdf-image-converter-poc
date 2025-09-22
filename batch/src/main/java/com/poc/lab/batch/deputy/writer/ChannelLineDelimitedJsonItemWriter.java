package com.poc.lab.batch.deputy.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.core.io.WritableResource;
import org.springframework.util.Assert;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 출력 포맷: {..}\n, {..}\n, {..}
 * - JSON 배열로 감싸지 않음
 * - 아이템 사이에 쉼표와 개행 추가
 */
public class ChannelLineDelimitedJsonItemWriter<T> implements ItemStreamWriter<T> {

    private final WritableResource resource;
    private final ObjectWriter writer;
    private final Charset charset;

    private BufferedWriter out;
    private boolean firstWritten = false;

    public ChannelLineDelimitedJsonItemWriter(WritableResource resource, ObjectMapper mapper, Class<T> type) {
        this(resource, mapper, type, Charset.forName("UTF-8"));
    }

    public ChannelLineDelimitedJsonItemWriter(WritableResource resource, ObjectMapper mapper, Class<T> type, Charset charset) {
        Assert.notNull(resource, "resource must not be null");
        Assert.notNull(mapper, "mapper must not be null");
        Assert.notNull(type, "type must not be null");
        Assert.notNull(charset, "charset must not be null");
        this.resource = resource;
        this.writer = mapper.writerFor(type);
        this.charset = charset;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            if (resource.exists()) {
                resource.getFile().getParentFile().mkdirs();
                resource.getFile().delete(); // 새로 생성
            }
            this.out = new BufferedWriter(new OutputStreamWriter(resource.getOutputStream(), charset));
            this.firstWritten = false;
        } catch (IOException e) {
            throw new ItemStreamException("Failed to open writer for " + resource, e);
        }
    }

    @Override
    public void write(Chunk<? extends T> chunk) throws Exception {
        for (T item : chunk) {
            String json = writer.writeValueAsString(item);
            if (firstWritten) {
                out.write(", ");
                out.newLine();
            }
            out.write(json);
            firstWritten = true;
        }
        out.flush();
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        // 필요 시 상태 저장 로직 추가 가능(여기서는 생략)
    }

    @Override
    public void close() throws ItemStreamException {
        if (out != null) {
            try {
                out.flush();
                out.close();
            } catch (IOException e) {
                throw new ItemStreamException("Failed to close writer for " + resource, e);
            } finally {
                out = null;
            }
        }
    }
}
