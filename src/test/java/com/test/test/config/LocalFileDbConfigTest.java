package com.test.test.config;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 파일 H2 설정으로 <b>실제로 커넥션이 열리는지</b> (03_기술결정 §17) — TDD Red.
 *
 * <p>컨트롤러를 거치지 않는 설정 검증이라 작은 단위테스트로 덮는다(컨벤션 §6 예외).
 * 통합테스트는 전부 인메모리 H2 라 {@code application.yml} 의 <b>파일 DB URL 은 어떤 테스트도 밟지 않는다</b> —
 * 그래서 URL 이 깨져도 323건이 전부 통과하고, 깨진 사실은 {@code java -jar} 로 띄우는 순간에야 드러난다.
 * 실제로 2026-09-07 빌드는 기동 즉시 죽었다:
 * {@code Feature not supported: "AUTO_SERVER=TRUE && DB_CLOSE_ON_EXIT=FALSE"} (H2 2.x 가 금지하는 조합).
 *
 * <p>이 테스트는 URL 문자열을 다시 적지 않고 {@code application.yml} 을 읽어 그대로 쓴다 — 설정을 고치면
 * 테스트가 따라오고, 문자열만 베껴 두면 잡지 못할 회귀를 잡는다. DB 경로만 임시 폴더로 바꾼다.
 */
class LocalFileDbConfigTest {

    /** application.yml 의 {@code spring.datasource.url} 은 {@code ${LOCAL_DB_PATH:./data/devdb}} 로 경로를 받는다. */
    private static final String DB_PATH_PROPERTY = "LOCAL_DB_PATH";
    private static final String URL_PROPERTY = "spring.datasource.url";

    @Test
    @DisplayName("application.yml 의 로컬 파일 H2 URL 로 커넥션이 열린다")
    void local_file_h2_url_can_open_a_connection(@TempDir Path tempDir) throws Exception {
        String url = localDatasourceUrl(tempDir.resolve("devdb").toString().replace(File.separatorChar, '/'));

        assertThat(url).startsWith("jdbc:h2:file:");
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    /**
     * 운영 코드의 {@code application.yml}(= {@code build/resources/main})을 읽어 placeholder 까지 해석한 datasource URL.
     * 테스트 리소스에도 같은 이름의 파일이 있고 그쪽이 클래스패스 앞이라, 이름만으로 찾으면 인메모리 URL 을 읽는다.
     */
    private String localDatasourceUrl(String dbPath) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new MapPropertySource("testDbPath", Map.of(DB_PATH_PROPERTY, dbPath)));
        List<PropertySource<?>> loaded =
                new YamlPropertySourceLoader().load("application.yml", mainApplicationYml());
        loaded.forEach(environment.getPropertySources()::addLast);
        return environment.getProperty(URL_PROPERTY);
    }

    private Resource mainApplicationYml() throws IOException {
        Enumeration<URL> candidates = getClass().getClassLoader().getResources("application.yml");
        while (candidates.hasMoreElements()) {
            URL candidate = candidates.nextElement();
            if (!candidate.getPath().contains("/resources/test/")) {
                return new UrlResource(candidate);
            }
        }
        throw new IllegalStateException("운영 application.yml 을 클래스패스에서 찾지 못했다");
    }
}
