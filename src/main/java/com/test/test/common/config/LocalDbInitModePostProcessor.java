package com.test.test.common.config;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 로컬 파일 H2 를 쓸 때 시드 스크립트({@code data-*.sql})를 <b>새 DB 에만</b> 돌린다 (03_기술결정 §17-3).
 *
 * <p><b>문제.</b> {@code spring.sql.init.mode: always} 는 매 기동 시드를 다시 실행한다.
 * 인메모리에서는 매번 빈 DB 라 문제가 없지만 파일 DB 에서는 전부 깨진다 —
 * {@code data-users.sql}(명시 id 1~111)은 PK 충돌로 <b>기동 자체가 실패</b>하고,
 * {@code data-comment.sql}(id 없음)은 매 기동 댓글이 중복 누적되며,
 * {@code data-rooms.sql}(선 DELETE)은 메시지가 있으면 FK 위반이 난다.
 *
 * <p><b>해법.</b> 스크립트를 멱등하게 고치는 대신(그건 원래 "새 DB 에 1회" 성격인 데모 데이터다)
 * <b>DB 파일이 이미 있으면 건너뛴다</b>. {@code never} 로 못 박지 않는 이유는 새로 만든 파일 DB 에
 * 시드 계정이 없으면 로컬 관리자 로그인이 불가능하기 때문이다.
 *
 * <p><b>파일 DB 가 아니면 아무 property 도 넣지 않는다</b> — 테스트(인메모리)와 운영(MySQL)에 영향 0.
 * {@code if (로컬)} 코드 분기가 아니라 "이 URL 이면 이 설정" 이라는 설정 계산이라 컨벤션 §5 취지에 맞는다.
 *
 * <p><b>순서.</b> {@code ConfigDataEnvironmentPostProcessor}(order {@code HIGHEST_PRECEDENCE + 10}) 가 yml 을
 * 읽은 <b>뒤</b>에 돌아야 {@code spring.datasource.url} 을 볼 수 있으므로 {@link #getOrder()} 는
 * {@code LOWEST_PRECEDENCE} 다. 계산 결과는 {@code addFirst} 로 넣어야 yml 의 {@code always} 를 덮는다.
 *
 * <p><b>등록: {@code META-INF/spring.factories}</b> (03 §17-3).
 */
public class LocalDbInitModePostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "localDbInitMode";
    private static final String URL_PROPERTY = "spring.datasource.url";
    private static final String INIT_MODE_PROPERTY = "spring.sql.init.mode";
    private static final String FILE_URL_PREFIX = "jdbc:h2:file:";
    /** H2 MVStore 데이터 파일 확장자 — 이 파일이 있으면 이미 만들어진 DB 다. */
    private static final String H2_DATA_FILE_SUFFIX = ".mv.db";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty(URL_PROPERTY);
        if (url == null || !url.startsWith(FILE_URL_PREFIX)) {
            return;   // 인메모리·MySQL — 설정 계산이 개입하지 않는다
        }
        Path dataFile = dataFileOf(url);
        if (dataFile == null) {
            return;   // 경로를 읽을 수 없으면 아무것도 바꾸지 않는다(yml 값 그대로)
        }
        String mode = Files.exists(dataFile) ? "never" : "always";
        environment.getPropertySources().addFirst(
                new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(INIT_MODE_PROPERTY, mode)));
    }

    /** {@code jdbc:h2:file:./data/devdb;MODE=MySQL;…} → {@code ./data/devdb.mv.db}. */
    private Path dataFileOf(String url) {
        String path = url.substring(FILE_URL_PREFIX.length());
        int optionsAt = path.indexOf(';');
        if (optionsAt >= 0) {
            path = path.substring(0, optionsAt);
        }
        path = path.trim();
        if (path.isEmpty()) {
            return null;
        }
        try {
            return Paths.get(path + H2_DATA_FILE_SUFFIX);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    @Override
    public int getOrder() {
        // ConfigData(yml) 가 로드된 뒤여야 spring.datasource.url 을 볼 수 있다.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
