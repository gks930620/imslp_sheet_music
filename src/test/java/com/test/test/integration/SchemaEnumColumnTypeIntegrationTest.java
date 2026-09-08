package com.test.test.integration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스키마 회귀 가드 — enum 컬럼은 네이티브 `ENUM(...)` 이 아니라 `VARCHAR` 여야 한다
 * (03_기술결정 §17-6, 01_ERD §9-1. 2026-09-08 senior-dev, Red).
 *
 * <p><b>왜 컨트롤러 테스트가 아닌가(컨벤션 §6 예외).</b> 이 결함은 요청/응답 어디에도 드러나지 않는다 —
 * 374개 API 테스트가 전부 초록인 채로, <b>기존 DB 를 쓰는 서버만 기동에 실패</b>했다
 * (`SeedType.COLLECTION_GUIDE` 추가 → `Value not permitted for column "('COMPOSER','WORK')": "COLLECTION_GUIDE" [22030-224]`).
 * 원인은 `@Enumerated(STRING)` 인데 Hibernate 6 이 H2·MySQL 에서 DDL 을 `enum('A','B')` 로 내는 것이고,
 * `ddl-auto: update` 는 그 정의를 넓히지 않는다. 즉 <b>드러나는 곳이 "엔티티가 만드는 DDL" 뿐</b>이라
 * 그 DDL 을 직접 보는 테스트만이 이 사고를 막을 수 있다(03 §17-5 가 말한 "테스트가 못 잡는 변경").
 *
 * <p>이 테스트가 실패하면 해당 필드에 {@code @JdbcTypeCode(SqlTypes.VARCHAR)} 를 붙인다.
 * 기존 DB 에는 01_ERD §9-1 의 ALTER 문장을 함께 실행한다.
 */
class SchemaEnumColumnTypeIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("애플리케이션 스키마에 네이티브 ENUM 컬럼이 하나도 없다 — enum 상수 추가가 비추가형 변경이 되지 않게")
    void noColumnIsCreatedAsNativeEnum() throws Exception {
        List<String> nativeEnumColumns = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("""
                     SELECT table_name, column_name
                       FROM information_schema.columns
                      WHERE data_type = 'ENUM'
                        AND table_schema NOT IN ('INFORMATION_SCHEMA', 'SYSTEM_LOBS')
                      ORDER BY table_name, column_name
                     """)) {
            while (rs.next()) {
                nativeEnumColumns.add(rs.getString(1).toLowerCase() + "." + rs.getString(2).toLowerCase());
            }
        }

        assertThat(nativeEnumColumns)
                .as("네이티브 ENUM 컬럼 — 여기 있는 컬럼은 enum 상수를 하나만 더해도 기존 DB 의 조회가 깨진다 "
                        + "(해결: 필드에 @JdbcTypeCode(SqlTypes.VARCHAR), 기존 DB 는 01_ERD §9-1 의 ALTER)")
                .isEmpty();
    }
}
