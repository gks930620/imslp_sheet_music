package com.test.test.integration.support;

import com.test.test.integration.SheetMusicFixtureSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>테스트 트랜잭션이 없는</b> 통합테스트 기반.
 *
 * <p>왜 필요한가: 기본 기반인 {@code ApiIntegrationTestSupport} 는 클래스에 {@code @Transactional} 이 걸려 있어
 * 테스트 스레드에 영속성 컨텍스트가 <b>열린 채로</b> 컨트롤러가 돈다. 그래서 실제 운영({@code open-in-view: false},
 * 요청마다 새 영속성 컨텍스트)에서만 터지는 결함 — lazy 프록시 초기화 실패, {@code @Modifying(clearAutomatically)}
 * 로 detach 된 엔티티 접근, 서비스 트랜잭션 밖 DTO 변환 — 을 <b>구조적으로 못 잡는다</b>
 * (qa 결함 D2: {@code GET /api/communities/{id}} 가 운영에서 전건 500 인데 통합테스트는 초록).
 *
 * <p>그래서 <b>사용자에게 노출되는 주요 GET</b> 은 이 기반 위에서 한 번씩 통과해야 한다. 기존 테스트를 전부
 * 비트랜잭션으로 옮기지는 않는다 — 롤백이 주는 격리·속도를 잃는 비용이 크고, 대표 엔드포인트만으로
 * "열린 세션에 기대는 코드" 를 잡을 수 있다.
 *
 * <p>운영 규칙
 * <ul>
 *   <li>{@code @Transactional(NOT_SUPPORTED)} — 이 기반을 상속한 테스트 클래스는 {@code @Transactional} 을 다시 붙이지 않는다.</li>
 *   <li><b>격리된 H2</b>({@code nontxdb}) — 여기서 만든 행은 커밋되므로 다른 테스트 컨텍스트의 DB 와 섞이면 안 된다.</li>
 *   <li>시드(CSV) 끄기 + {@code @AfterEach} 로 시트뮤직 테이블 비우기 — 테스트마다 자기 픽스처를 만든다.
 *       ({@code data-*.sql} 의 사용자·게시글·댓글 시드는 컨텍스트 기동 시 적재되고 지우지 않는다.)</li>
 * </ul>
 *
 * @see CrawlTestSupport 같은 이유(워커 스레드가 봐야 함)로 비트랜잭션인 선례
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:nontxdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "app.seed.enabled=false"
})
public abstract class NonTransactionalApiTestSupport extends SheetMusicFixtureSupport {

    /** 자식 → 부모 순으로 지운다(참조 무결성은 잠시 끈다 — H2). */
    private static final List<String> SHEET_MUSIC_TABLES = List.of(
            "crawl_item", "crawl_job", "download_log", "edition",
            "work_alias", "work_catalog_number", "work", "composer_alias", "composer");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanSheetMusicTables() {
        try {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            SHEET_MUSIC_TABLES.forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE " + table));
            jdbcTemplate.update("DELETE FROM files WHERE ref_type = 'EDITION'");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
