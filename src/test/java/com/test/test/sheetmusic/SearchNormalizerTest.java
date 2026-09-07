package com.test.test.sheetmusic;

import com.test.test.sheetmusic.common.SearchNormalizer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색 정규화 규칙 — docs/설계/01_ERD.md §2 를 그대로 옮긴 것.
 * 저장(정규화 컬럼)과 검색(단어별)이 같은 함수를 써야 하므로 이 표가 곧 계약이다.
 */
class SearchNormalizerTest {

    @ParameterizedTest(name = "normalize(\"{0}\") = \"{1}\"")
    @DisplayName("01_ERD §2 예시 표: 악센트·대소문자·공백·구두점·기호 제거")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "Op. 27, No. 2            | op27no2",
            "Op.27 No.2               | op27no2",
            "BWV 846                  | bwv846",
            "K.545                    | k545",
            "K.331/300i               | k331300i",
            "Beethoven, Ludwig van    | beethovenludwigvan",
            "엘리제를 위하여            | 엘리제를위하여",
            "Fantaisie-impromptu      | fantaisieimpromptu",
            "C-sharp minor            | csharpminor",
            "Für Elise                | furelise",
            "FUR ELISE                | furelise",
            "Frédéric                 | frederic",
            "Chopin, Frédéric         | chopinfrederic",
            "Bądarzewska-Baranowska   | badarzewskabaranowska",
            "100 Übungsstücke, Op.139 | 100ubungsstuckeop139",
            "Études, Op.10            | etudesop10",
            "월광 소나타               | 월광소나타",
            "녹턴 E♭                   | 녹턴e",
            "Nocturne in E-flat       | nocturneineflat",
            "Waltz Op.64 No.1         | waltzop64no1",
            "(없음)                    | 없음",
            "L'adieu                  | ladieu",
            "Op.49-2                  | op492",
    })
    void normalize_examples(String input, String expected) {
        assertThat(SearchNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("NFD 로 분해되지 않는 문자 치환표: ß→ss, ø→o, ł→l, æ→ae, œ→oe, đ→d, ð→d, þ→th, ı→i")
    void normalize_substitutionTable() {
        assertThat(SearchNormalizer.normalize("Straße")).isEqualTo("strasse");
        assertThat(SearchNormalizer.normalize("Søren Ørsted")).isEqualTo("sorenorsted");
        assertThat(SearchNormalizer.normalize("Łódź")).isEqualTo("lodz");
        assertThat(SearchNormalizer.normalize("Æolian")).isEqualTo("aeolian");
        assertThat(SearchNormalizer.normalize("Œuvre")).isEqualTo("oeuvre");
        assertThat(SearchNormalizer.normalize("Đorđe")).isEqualTo("dorde");
        assertThat(SearchNormalizer.normalize("Guðrún")).isEqualTo("gudrun");
        assertThat(SearchNormalizer.normalize("Þór")).isEqualTo("thor");
        assertThat(SearchNormalizer.normalize("Işık")).isEqualTo("isik");
    }

    @Test
    @DisplayName("한글은 NFD 분해 후 NFC 재결합돼 음절이 보존된다")
    void normalize_keepsHangulSyllables() {
        String out = SearchNormalizer.normalize("월광");
        assertThat(out).isEqualTo("월광");
        assertThat(out).hasSize(2);
        assertThat(out.codePointAt(0)).isEqualTo('월');
    }

    @Test
    @DisplayName("null / 빈 문자열 / 공백만 / 기호만 → \"\"")
    void normalize_blankAndSymbols() {
        assertThat(SearchNormalizer.normalize(null)).isEmpty();
        assertThat(SearchNormalizer.normalize("")).isEmpty();
        assertThat(SearchNormalizer.normalize("   ")).isEmpty();
        assertThat(SearchNormalizer.normalize("... , - / ♯♭# ()")).isEmpty();
    }

    @Test
    @DisplayName("멱등: normalize(normalize(s)) == normalize(s)")
    void normalize_isIdempotent() {
        String once = SearchNormalizer.normalize("Für Elise, WoO 59");
        assertThat(SearchNormalizer.normalize(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("normalizeWords: 공백으로 나눠 단어별 정규화, 빈 단어 제거")
    void normalizeWords_splitsAndDropsEmpty() {
        assertThat(SearchNormalizer.normalizeWords("쇼팽 녹턴")).containsExactly("쇼팽", "녹턴");
        assertThat(SearchNormalizer.normalizeWords("  Op.27   No.2 ")).containsExactly("op27", "no2");
        assertThat(SearchNormalizer.normalizeWords("op27no2")).containsExactly("op27no2");
        assertThat(SearchNormalizer.normalizeWords("Für Elise")).containsExactly("fur", "elise");
        // 기호만인 단어는 정규화 후 비므로 제거된다
        assertThat(SearchNormalizer.normalizeWords("녹턴 - Op.9")).containsExactly("녹턴", "op9");
    }

    @Test
    @DisplayName("normalizeWords: null / 공백 / 기호만 → 빈 목록")
    void normalizeWords_blank() {
        assertThat(SearchNormalizer.normalizeWords(null)).isEqualTo(List.of());
        assertThat(SearchNormalizer.normalizeWords("   ")).isEqualTo(List.of());
        assertThat(SearchNormalizer.normalizeWords("...")).isEqualTo(List.of());
    }
}
