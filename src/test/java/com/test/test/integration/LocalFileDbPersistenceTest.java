package com.test.test.integration;

import com.test.test.SheetMusicApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 파일 H2 영속화 (03_기술결정 §17) — TDD Red.
 *
 * <p><b>왜 이 형태인가.</b> 이건 컨트롤러 계약이 아니라 <b>기동 설정</b>의 계약이라 MockMvc 로 볼 수 없다.
 * 검증할 것은 "서버를 껐다 켜도 데이터가 남고, 두 번째 기동이 시드 스크립트 재실행으로 죽지 않는다" 뿐이므로
 * 임시 폴더의 파일 H2 를 대상으로 <b>앱을 실제로 두 번 띄운다</b>. 다른 테스트의 인메모리 격리는 건드리지 않는다
 * ({@code src/test/resources/application.yml} 은 그대로).
 *
 * <p>지금은 두 번째 기동에서 {@code data-users.sql}(명시 id 1~111)이 다시 실행돼 PK 충돌로 죽는다 = 의도된 Red.
 * 기대 동작은 §17-3: DB 파일이 이미 있으면 {@code spring.sql.init.mode} 를 {@code never} 로 계산한다.
 */
class LocalFileDbPersistenceTest {

    /** 시드 사용자 수 (data-users.sql id 1~111). 두 번째 기동에서 늘어나면 안 된다. */
    private static final int SEED_USER_COUNT = 111;
    /** CSV 시드 곡 수 (01_ERD §6, works.csv). 두 번째 기동에서 늘어나면 안 된다(로더 멱등). */
    private static final int SEED_WORK_COUNT = 50;

    @Test
    void data_survives_a_restart_and_seed_scripts_do_not_run_twice(@TempDir Path dir) throws Exception {
        String jdbcUrl = "jdbc:h2:file:" + dir.resolve("devdb").toAbsolutePath().toString().replace('\\', '/')
                + ";MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE";

        long markerComposerId;
        try (ConfigurableApplicationContext first = boot(jdbcUrl)) {
            // 새 파일 DB → 시드 스크립트가 돈다
            assertThat(first.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("always");

            JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "users")).isEqualTo(SEED_USER_COUNT);
            assertThat(count(jdbc, "work")).isEqualTo(SEED_WORK_COUNT);

            // "수집으로 들어온 데이터" 대역 — 재시작 뒤에도 남아야 하는 행
            jdbc.update("INSERT INTO composer (name_original, name_original_normalized, created_at, updated_at) "
                    + "VALUES ('Restart, Marker', 'restartmarker', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            markerComposerId = jdbc.queryForObject(
                    "SELECT id FROM composer WHERE name_original_normalized = 'restartmarker'", Long.class);
        }
        assertThat(Files.exists(dir.resolve("devdb.mv.db"))).isTrue();

        // 두 번째 기동: 시드 스크립트 재실행으로 죽지 않아야 한다
        ConfigurableApplicationContext second = null;
        try {
            second = boot(jdbcUrl);   // 지금은 여기서 data-users.sql PK 충돌로 기동이 실패한다 (Red)

            assertThat(second.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("never");

            JdbcTemplate jdbc = second.getBean(JdbcTemplate.class);
            // 데이터가 살아 있다
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM composer WHERE id = ?", Integer.class, markerComposerId)).isEqualTo(1);
            // 시드가 중복 적재되지 않았다
            assertThat(count(jdbc, "users")).isEqualTo(SEED_USER_COUNT);
            assertThat(count(jdbc, "work")).isEqualTo(SEED_WORK_COUNT);
        } finally {
            if (second != null) {
                second.close();
            }
        }
    }

    @Test
    void in_memory_url_is_left_alone_so_tests_keep_their_isolation() throws Exception {
        try (ConfigurableApplicationContext context = boot("jdbc:h2:mem:initmodeprobe;MODE=MySQL;DB_CLOSE_DELAY=-1")) {
            // 파일 DB 가 아니면 설정 계산이 개입하지 않는다 → application.yml 값 그대로
            assertThat(context.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("always");
            assertThat(count(context.getBean(JdbcTemplate.class), "users")).isEqualTo(SEED_USER_COUNT);
        }
    }

    private ConfigurableApplicationContext boot(String jdbcUrl) {
        return new SpringApplicationBuilder(SheetMusicApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--spring.datasource.url=" + jdbcUrl,
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--server.port=0",
                        "--file.upload-dir=./build/test-uploads",
                        "--app.cookie.secure=false");
    }

    private int count(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
