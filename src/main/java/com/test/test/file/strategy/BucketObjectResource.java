package com.test.test.file.strategy;

import java.io.InputStream;
import org.springframework.core.io.InputStreamResource;

/**
 * 버킷 객체 스트림 + <b>미리 아는 길이</b> (§5-3 서빙 프록시).
 *
 * <p>{@link InputStreamResource} 는 {@code contentLength()} 를 구할 때 스트림을 끝까지 읽어버린다
 * (그러면 한 번만 읽을 수 있는 스트림이 소진돼 응답 본문이 비고, 길이를 재느라 전부 메모리로 넘어온다).
 * S3 응답이 이미 {@code contentLength} 를 주므로 그 값을 그대로 쓴다 —
 * 명세 §3-4 의 {@code Content-Length} 필수 조건을 스트리밍에서도 지키기 위해서다.
 */
class BucketObjectResource extends InputStreamResource {

    private final long contentLength;

    BucketObjectResource(InputStream inputStream, long contentLength, String key) {
        super(inputStream, "버킷 객체 [" + key + "]");
        this.contentLength = contentLength;
    }

    @Override
    public long contentLength() {
        return contentLength;
    }
}
