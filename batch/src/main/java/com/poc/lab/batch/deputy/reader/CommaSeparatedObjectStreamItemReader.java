package com.poc.lab.batch.deputy.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public class CommaSeparatedObjectStreamItemReader<T> extends AbstractItemCountingItemStreamItemReader<T> {

    private final Resource resource;
    private final ObjectReader reader;
    private final Charset charset;

    private InputStream input;
    private BufferedReader br;

    // 버퍼 관련
    private final StringBuilder carry = new StringBuilder(); // 경계 걸친 조각 보관
    private boolean eof = false;

    // 스캐너 상태
    private int depth = 0;              // { ... } 깊이
    private boolean inString = false;   // 문자열 내부 여부
    private boolean escaped = false;    // 이전 문자가 백슬래시인지

    // 성능/안전: 한 번에 읽어올 크기
    private static final int READ_CHUNK = 8192;

    public CommaSeparatedObjectStreamItemReader(Resource resource,
                                                ObjectMapper objectMapper,
                                                Class<T> type) {
        this(resource, objectMapper, type, Charset.forName("UTF-8"));
    }

    public CommaSeparatedObjectStreamItemReader(Resource resource,
                                                ObjectMapper objectMapper,
                                                Class<T> type,
                                                Charset charset) {
        setName("commaSeparatedObjectStreamItemReader");
        Assert.notNull(resource, "resource must not be null");
        Assert.notNull(objectMapper, "objectMapper must not be null");
        Assert.notNull(type, "type must not be null");
        Assert.notNull(charset, "charset must not be null");
        this.resource = resource;
        this.reader = objectMapper.readerFor(type);
        this.charset = charset;
    }

    @Override
    protected void doOpen() throws Exception {
        try {
            this.input = resource.getInputStream();
            this.br = new BufferedReader(new InputStreamReader(this.input, this.charset), READ_CHUNK);
            resetState();
            // 객체 시작 전까지 구분자/공백 소비
            skipSeparators();
        } catch (IOException e) {
            throw new ItemStreamException("Failed to open stream: " + resource, e);
        }
    }

    @Nullable
    @Override
    protected T doRead() throws Exception {
        if (this.br == null) return null;
        String jsonObject = nextCompleteObject();
        if (jsonObject == null) {
            return null;
        }
        return this.reader.readValue(jsonObject);
    }

    @Override
    protected void doClose() throws Exception {
        IOException first = null;
        try {
            if (this.br != null) this.br.close();
        } catch (IOException e) {
            first = e;
        } finally {
            this.br = null;
        }
        try {
            if (this.input != null) this.input.close();
        } catch (IOException e) {
            if (first != null) e.addSuppressed(first);
            throw e;
        } finally {
            this.input = null;
        }
        resetState();
    }

    @Override
    protected void jumpToItem(int itemIndex) throws Exception {
        for (int i = 0; i < itemIndex; i++) {
            if (nextCompleteObject() == null) break;
        }
    }

    private void resetState() {
        this.carry.setLength(0);
        this.eof = false;
        this.depth = 0;
        this.inString = false;
        this.escaped = false;
    }

    private void skipSeparators() throws IOException {
        while (true) {
            if (carry.length() == 0 && !fillCarry()) {
                return; // EOF
            }
            int i = 0;
            while (i < carry.length()) {
                char c = carry.charAt(i);
                if (isWhitespace(c) || c == ',') {
                    i++;
                    continue;
                }
                if (c == '{') {
                    // 바로 객체 시작
                    if (i > 0) {
                        carry.delete(0, i);
                    }
                    return;
                }
                // 예상치 못한 문자면 그냥 전달(파싱 시 에러)
                if (i > 0) {
                    carry.delete(0, i);
                }
                return;
            }
            // 모두 소모
            carry.setLength(0);
        }
    }

    private String nextCompleteObject() throws IOException {
        StringBuilder obj = new StringBuilder();
        boolean started = false;

        while (true) {
            if (carry.length() == 0) {
                if (!fillCarry()) {
                    // EOF
                    if (started && depth == 0 && !inString) {
                        String s = obj.toString().trim();
                        return s.isEmpty() ? null : s;
                    }
                    return null;
                }
            }
            for (int i = 0; i < carry.length(); i++) {
                char c = carry.charAt(i);

                if (!started) {
                    if (isWhitespace(c) || c == ',') {
                        // 객체 시작 전 구분자 skip
                        continue;
                    }
                    if (c == '{') {
                        started = true;
                        depth = 1;
                        obj.append(c);
                        continue;
                    }
                    // 시작이 다른 문자면 에러 유도: 누적하고 즉시 실패하지 않고 다음 루프에서 바인딩 시 예외
                    started = true;
                    obj.append(c);
                    continue;
                }

                obj.append(c);

                if (inString) {
                    if (escaped) {
                        escaped = false; // 이스케이프된 문자 소비
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    // 문자열 중에는 depth 변경 없음
                } else {
                    if (c == '"') {
                        inString = true;
                    } else if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            // 객체 하나 완료
                            // 남은 캐리는 다음 읽기용으로 보존
                            // i 이후에 쉼표/공백이 남아있을 수 있음
                            String result = obj.toString();
                            // carry 에서 i+1 이후를 남기고 앞은 버림
                            carry.delete(0, i + 1);
                            // 객체 종료 후 구분자 소비
                            skipSeparators();
                            return result;
                        }
                    }
                }
            }
            // carry 모두 소비 -> 비움
            carry.setLength(0);
        }
    }

    private boolean fillCarry() throws IOException {
        if (eof) return false;
        char[] buf = new char[READ_CHUNK];
        int n = br.read(buf);
        if (n == -1) {
            eof = true;
            return carry.length() > 0;
        }
        carry.append(buf, 0, n);
        return true;
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\n' || c == '\r' || c == '\t';
    }
}
