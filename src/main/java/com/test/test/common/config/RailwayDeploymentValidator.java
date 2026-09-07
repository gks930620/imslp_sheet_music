package com.test.test.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 운영(prod) 배포 설정 검증 — 필수 버킷 설정 누락 시 기동 실패(fail-fast). (§5-3)
 *
 * <p>Railway 운영에서 {@code BUCKET_*} 가 빠진 채 뜨면 파일 업로드/서빙이 조용히 로컬 디스크로
 * 폴백돼 재배포 시 유실된다. 그런 반쪽 기동을 막기 위해 prod 프로파일에서만 검증한다.
 * (로컬/기본 프로파일은 이 빈이 생성되지 않아 통과.)
 */
@Component
@Profile("prod")
@Slf4j
public class RailwayDeploymentValidator {

    @Value("${app.bucket.endpoint:}")
    private String endpoint;

    @Value("${app.bucket.access-key-id:}")
    private String accessKeyId;

    @Value("${app.bucket.secret-access-key:}")
    private String secretAccessKey;

    @Value("${app.bucket.name:}")
    private String bucketName;

    @PostConstruct
    void validate() {
        List<String> missing = new ArrayList<>();
        if (isBlank(endpoint)) missing.add("BUCKET_ENDPOINT");
        if (isBlank(accessKeyId)) missing.add("BUCKET_ACCESS_KEY_ID");
        if (isBlank(secretAccessKey)) missing.add("BUCKET_SECRET_ACCESS_KEY");
        if (isBlank(bucketName)) missing.add("BUCKET_NAME");

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "운영(prod) 배포에 필수 버킷 설정이 누락되었습니다: " + missing
                            + " — Railway 서비스 변수(BUCKET_*)를 설정하세요.");
        }
        log.info("Railway 배포 설정 검증 통과 - bucket: {}, endpoint: {}", bucketName, endpoint);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
