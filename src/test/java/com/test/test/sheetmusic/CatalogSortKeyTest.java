package com.test.test.sheetmusic;

import com.test.test.sheetmusic.common.CatalogSortKey;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작품번호 정렬 키 — docs/설계/01_ERD.md §3-5 sort_key.
 * "정규화 값의 숫자 구간을 6자리 0-패딩" 이라 문자열 정렬로 Op.9 &lt; Op.10 이 된다.
 */
class CatalogSortKeyTest {

    @ParameterizedTest(name = "of(\"{0}\") = \"{1}\"")
    @DisplayName("표시 원문 → 정규화 → 숫자 6자리 패딩")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "Op.27 No.2        | op000027no000002",
            "Op. 27, No. 2     | op000027no000002",
            "BWV 846           | bwv000846",
            "K.545             | k000545",
            "K.331/300i        | k000331000300i",
            "WoO 59            | woo000059",
            "D.899             | d000899",
            "Op.90             | op000090",
            "B.150             | b000150",
            "S.244/2           | s000244000002",
            "Op.37a            | op000037a",
            "CD 74             | cd000074",
            "BWV 772–786       | bwv000772000786",
            "BWV Anh.113–132   | bwvanh000113000132",
            "Op.9              | op000009",
            "Op.10             | op000010",
            "Op.101            | op000101",
    })
    void of_examples(String input, String expected) {
        assertThat(CatalogSortKey.of(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("문자열 정렬이 작품번호 순과 같다: B.49 < B.150 < Op.9 < Op.10 < Op.27 No.2 < Op.101")
    void of_sortsNumerically() {
        List<String> display = List.of("Op.101", "Op.27 No.2", "Op.10", "Op.9", "B.150", "B.49");
        List<String> keys = new ArrayList<>();
        for (String d : display) {
            keys.add(CatalogSortKey.of(d));
        }
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(String::compareTo);
        assertThat(sorted).containsExactly(
                CatalogSortKey.of("B.49"),
                CatalogSortKey.of("B.150"),
                CatalogSortKey.of("Op.9"),
                CatalogSortKey.of("Op.10"),
                CatalogSortKey.of("Op.27 No.2"),
                CatalogSortKey.of("Op.101"));
    }

    @Test
    @DisplayName("같은 작품번호의 표기 변형은 같은 키를 낸다")
    void of_isStableAcrossNotationVariants() {
        assertThat(CatalogSortKey.of("Op.27 No.2"))
                .isEqualTo(CatalogSortKey.of("op 27 no 2"))
                .isEqualTo(CatalogSortKey.of("Op.27, No.2"))
                .isEqualTo(CatalogSortKey.of("OP.27-NO.2"));
    }

    @Test
    @DisplayName("6자리를 넘는 숫자는 잘리지 않고 그대로 둔다")
    void of_keepsLongNumbers() {
        assertThat(CatalogSortKey.of("X 1234567")).isEqualTo("x1234567");
    }

    @Test
    @DisplayName("null / 빈 값 / 기호만 → \"\"")
    void of_blank() {
        assertThat(CatalogSortKey.of(null)).isEmpty();
        assertThat(CatalogSortKey.of("")).isEmpty();
        assertThat(CatalogSortKey.of("  ")).isEmpty();
        assertThat(CatalogSortKey.of("(-)")).isEmpty();
    }

    @Test
    @DisplayName("결과 길이는 sort_key VARCHAR(120) 안에 든다 (시드 최장값 기준)")
    void of_fitsColumn() {
        assertThat(CatalogSortKey.of("Opp.19b·30·38·53·62·67·85·102").length()).isLessThanOrEqualTo(120);
    }
}
