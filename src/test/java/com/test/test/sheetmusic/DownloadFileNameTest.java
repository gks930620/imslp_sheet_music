package com.test.test.sheetmusic;

import com.test.test.sheetmusic.common.DownloadFileName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 다운로드 파일명 규칙 — docs/설계/02_API_명세서.md §3-4 (기획 01 §9 8-9, §12-1 확정).
 * {작곡가 한글 또는 원어} - {한국어 제목 또는 원어 제목}{ (대표 작품번호)}{ 편곡}{ N악장}.pdf
 *
 * <p>괄호 생략 조건 ③(작품번호가 여러 개)은 값 하나로 알 수 없어 호출자가 판정한다 —
 * 여기서는 검증하지 않고 {@code DownloadCatalogOmissionIntegrationTest} 가 왕복으로 본다.
 */
class DownloadFileNameTest {

    @Test
    @DisplayName("기본형: 한글 작곡가 - 한국어 제목 (대표 작품번호).pdf")
    void build_basic() {
        assertThat(DownloadFileName.build("모차르트", "Mozart, Wolfgang Amadeus",
                "피아노 소나타 11번 A장조", "Piano Sonata No.11 in A major, K.331/300i", "K.331"))
                .isEqualTo("모차르트 - 피아노 소나타 11번 A장조 (K.331).pdf");

        assertThat(DownloadFileName.build("베토벤", "Beethoven, Ludwig van",
                "월광 소나타", "Piano Sonata No.14, Op.27 No.2", "Op.27 No.2"))
                .isEqualTo("베토벤 - 월광 소나타 (Op.27 No.2).pdf");
    }

    @Test
    @DisplayName("작품번호가 없으면 괄호째 생략 — 빈 괄호 () 를 남기지 않는다 (§12-1 ①)")
    void build_withoutCatalogNumber() {
        assertThat(DownloadFileName.build("사티", "Satie, Erik", "짐노페디", "3 Gymnopédies", null))
                .isEqualTo("사티 - 짐노페디.pdf");
        assertThat(DownloadFileName.build("사티", "Satie, Erik", "짐노페디", "3 Gymnopédies", "  "))
                .isEqualTo("사티 - 짐노페디.pdf");
        assertThat(DownloadFileName.build("조플린", "Joplin, Scott", "엔터테이너", "The Entertainer", ""))
                .isEqualTo("조플린 - 엔터테이너.pdf");
    }

    @Test
    @DisplayName("한국어 제목이 없으면 원어 제목, 작곡가 한글이 없으면 원어 표기로 폴백")
    void build_fallbacks() {
        // 원어 제목으로 폴백해도 비교 대상은 '실제로 쓰인 제목' 이라 Op.9 가 이미 들어 있다 → 괄호 생략 (§12-1 ②)
        assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", null, "Nocturnes, Op.9", "Op.9"))
                .isEqualTo("쇼팽 - Nocturnes, Op.9.pdf");
        assertThat(DownloadFileName.build("", "Chopin, Frédéric", "", "Nocturnes, Op.9", "Op.9"))
                .isEqualTo("Chopin, Frédéric - Nocturnes, Op.9.pdf");
        assertThat(DownloadFileName.build(null, "Chopin, Frédéric", "  ", "Nocturnes, Op.9", null))
                .isEqualTo("Chopin, Frédéric - Nocturnes, Op.9.pdf");
        // 폴백한 원어 제목이 작품번호를 담고 있지 않으면 괄호는 그대로
        assertThat(DownloadFileName.build(null, "Chopin, Frédéric", null, "Ballade No.1", "Op.23"))
                .isEqualTo("Chopin, Frédéric - Ballade No.1 (Op.23).pdf");
    }

    @Test
    @DisplayName("작품번호의 슬래시는 '-' 로: K.331/300i → (K.331-300i)")
    void build_slashInCatalogNumber() {
        assertThat(DownloadFileName.build("모차르트", "Mozart, Wolfgang Amadeus",
                "피아노 소나타 11번 A장조", "Piano Sonata No.11 in A major, K.331/300i", "K.331/300i"))
                .isEqualTo("모차르트 - 피아노 소나타 11번 A장조 (K.331-300i).pdf");
    }

    @Test
    @DisplayName("파일명 금지 문자 \\ / : * ? \" < > | 는 '-' 로 치환")
    void build_replacesForbiddenCharacters() {
        assertThat(DownloadFileName.build("A\\B", "x", "C/D:E*F?G\"H<I>J|K", "y", null))
                .isEqualTo("A-B - C-D-E-F-G-H-I-J-K.pdf");
    }

    @Test
    @DisplayName("연속 공백은 하나로, 앞뒤 공백 제거")
    void build_collapsesWhitespace() {
        assertThat(DownloadFileName.build("  베토벤  ", "Beethoven, Ludwig van",
                " 월광   소나타 ", "Piano Sonata No.14", " Op.27   No.2 "))
                .isEqualTo("베토벤 - 월광 소나타 (Op.27 No.2).pdf");
    }

    @Test
    @DisplayName("악센트·비ASCII 원어 표기는 그대로 둔다 — 정규화는 비교에만 쓰고 출력은 원문이다")
    void build_keepsNonAscii() {
        // 비교는 정규화 후("lapriereduneviergeop4" ∋ "op4") → 괄호 생략, 출력의 악센트는 그대로
        assertThat(DownloadFileName.build(null, "Bądarzewska-Baranowska, Tekla",
                null, "La prière d'une vierge, Op.4", "Op.4"))
                .isEqualTo("Bądarzewska-Baranowska, Tekla - La prière d'une vierge, Op.4.pdf");
    }

    /**
     * §12-1 ② — "괄호는 제목이 말하지 않은 것만 말한다".
     * 파일명에 실제로 쓰인 제목을 SearchNormalizer 로 정규화해 작품번호를 포함하면 괄호를 통째로 생략한다.
     */
    @Nested
    @DisplayName("괄호 생략 ② — 제목에 작품번호가 이미 들어 있다")
    class TitleAlreadyContainsCatalog {

        @Test
        @DisplayName("시드 실데이터: 제목에 작품번호가 든 12곡은 괄호가 사라진다")
        void seedWorks_omitBrackets() {
            assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", "녹턴 Op.9", "Nocturnes, Op.9", "Op.9"))
                    .isEqualTo("쇼팽 - 녹턴 Op.9.pdf");
            assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", "왈츠 Op.64", "Waltzes, Op.64", "Op.64"))
                    .isEqualTo("쇼팽 - 왈츠 Op.64.pdf");
            assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", "24개의 전주곡 Op.28", "Preludes, Op.28", "Op.28"))
                    .isEqualTo("쇼팽 - 24개의 전주곡 Op.28.pdf");
            assertThat(DownloadFileName.build("스크랴빈", "Scriabin, Aleksandr", "12개의 연습곡 Op.8", "12 Etudes, Op.8", "Op.8"))
                    .isEqualTo("스크랴빈 - 12개의 연습곡 Op.8.pdf");
            assertThat(DownloadFileName.build("그리그", "Grieg, Edvard", "서정 소품집 3권 Op.43", "Lyric Pieces, Op.43", "Op.43"))
                    .isEqualTo("그리그 - 서정 소품집 3권 Op.43.pdf");
            assertThat(DownloadFileName.build("브람스", "Brahms, Johannes", "6개의 피아노 소품 Op.118", "6 Klavierstücke, Op.118", "Op.118"))
                    .isEqualTo("브람스 - 6개의 피아노 소품 Op.118.pdf");
        }

        @Test
        @DisplayName("대소문자·공백·마침표는 무시하고 비교한다 (SearchNormalizer)")
        void normalizedComparison_ignoresCaseSpaceAndDot() {
            assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", "녹턴 op. 9", "Nocturnes", "Op.9"))
                    .isEqualTo("쇼팽 - 녹턴 op. 9.pdf");
            assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", "녹턴 OP9", "Nocturnes", "Op. 9"))
                    .isEqualTo("쇼팽 - 녹턴 OP9.pdf");
            assertThat(DownloadFileName.build("베토벤", "Beethoven, Ludwig van", "소나타 op27 no2", "Sonata", "Op.27 No.2"))
                    .isEqualTo("베토벤 - 소나타 op27 no2.pdf");
        }

        @Test
        @DisplayName("매치 앞이 숫자인 것은 정상이다 — #43 즉흥곡 Op.90 (D.899) + 대표 D.899")
        void digitBeforeMatch_stillOmits() {
            assertThat(DownloadFileName.build("슈베르트", "Schubert, Franz",
                    "즉흥곡 Op.90 (D.899)", "4 Impromptus, D.899", "D.899"))
                    .isEqualTo("슈베르트 - 즉흥곡 Op.90 (D.899).pdf");
        }

        @Test
        @DisplayName("생략해도 편곡·악장 접미사는 그대로 붙는다 (§12-1 4)")
        void omission_keepsScopeSuffix() {
            assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", "녹턴 Op.9", "Nocturnes, Op.9", "Op.9", " 편곡"))
                    .isEqualTo("쇼팽 - 녹턴 Op.9 편곡.pdf");
            assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", "녹턴 Op.9", "Nocturnes, Op.9", "Op.9", " 편곡 2악장"))
                    .isEqualTo("쇼팽 - 녹턴 Op.9 편곡 2악장.pdf");
            assertThat(DownloadFileName.build("사티", "Satie, Erik", "짐노페디", "3 Gymnopédies", null, " 2악장"))
                    .isEqualTo("사티 - 짐노페디 2악장.pdf");
        }
    }

    @Nested
    @DisplayName("괄호를 생략하지 않는 경계")
    class BoundaryKeepsBrackets {

        @Test
        @DisplayName("뒤에 숫자가 이어지면 다른 번호다 — 제목 '연습곡 Op.10' 은 작품번호 Op.1 이 아니다")
        void digitAfterMatch_isDifferentNumber() {
            assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", "연습곡 Op.10", "Études, Op.10", "Op.1"))
                    .isEqualTo("쇼팽 - 연습곡 Op.10 (Op.1).pdf");
            assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", "24개의 전주곡 Op.28", "Preludes, Op.28", "Op.2"))
                    .isEqualTo("쇼팽 - 24개의 전주곡 Op.28 (Op.2).pdf");
            assertThat(DownloadFileName.build("바흐", "Bach, Johann Sebastian", "인벤션 BWV 772", "15 Inventions", "BWV 77"))
                    .isEqualTo("바흐 - 인벤션 BWV 772 (BWV 77).pdf");
        }

        @Test
        @DisplayName("제목이 작품번호보다 덜 자세하면 괄호를 남긴다 (§12-1 3) — 뒤 괄호가 범위를 좁혀 준다")
        void titleLessSpecificThanCatalog_keepsBracket() {
            assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", "녹턴 Op.9", "Nocturnes, Op.9", "Op.9 No.2"))
                    .isEqualTo("쇼팽 - 녹턴 Op.9 (Op.9 No.2).pdf");
        }

        @Test
        @DisplayName("제목에 없는 작품번호는 그대로 붙는다 (시드 대다수)")
        void unrelatedCatalog_keepsBracket() {
            assertThat(DownloadFileName.build("체르니", "Czerny, Carl", "체르니 100번", "100 Übungsstücke, Op.139", "Op.139"))
                    .isEqualTo("체르니 - 체르니 100번 (Op.139).pdf");
            assertThat(DownloadFileName.build("베토벤", "Beethoven, Ludwig van", "엘리제를 위하여", "Für Elise, WoO 59", "WoO 59"))
                    .isEqualTo("베토벤 - 엘리제를 위하여 (WoO 59).pdf");
            assertThat(DownloadFileName.build("리스트", "Liszt, Franz", "헝가리 광시곡 2번", "Hungarian Rhapsody No.2", "S.244/2"))
                    .isEqualTo("리스트 - 헝가리 광시곡 2번 (S.244-2).pdf");
        }
    }

    @Test
    @DisplayName("괄호 생략 ③-b: 대표 작품번호 값 안에 또 괄호가 있으면 통째로 생략 (§12-1 ③)")
    void build_omitsCatalog_whenCatalogContainsParenthesis() {
        assertThat(DownloadFileName.build("바흐", "Bach, Johann Sebastian",
                "안나 막달레나 바흐를 위한 음악 수첩", "Notebooks for Anna Magdalena Bach",
                "BWV Anh.113–132 (1725년 수첩)"))
                .isEqualTo("바흐 - 안나 막달레나 바흐를 위한 음악 수첩.pdf");
    }

    @Test
    @DisplayName("괄호를 생략해도 204바이트 상한 규칙은 그대로 — 제목이 먼저 잘리고 접미사는 남는다")
    void build_omissionThenLengthLimit() {
        String longTitle = "가".repeat(120) + " Op.9";   // 정규화하면 Op.9 를 포함 → 괄호 생략
        String fileName = DownloadFileName.build("쇼팽", "Chopin, Frédéric", longTitle, "Nocturnes", "Op.9", " 편곡 2악장");

        assertThat(fileName).doesNotContain("(Op.9)");
        assertThat(fileName).endsWith(" 편곡 2악장.pdf");
        assertThat(fileName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(204);
    }
}
