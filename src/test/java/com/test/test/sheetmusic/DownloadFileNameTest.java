package com.test.test.sheetmusic;

import com.test.test.sheetmusic.common.DownloadFileName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 다운로드 파일명 규칙 — docs/설계/02_API_명세서.md §3-4 (기획 01 §9 8-9 확정).
 * {작곡가 한글 또는 원어} - {한국어 제목 또는 원어 제목}{ (대표 작품번호)}.pdf
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
    @DisplayName("작품번호가 없으면 괄호째 생략: 사티 - 짐노페디.pdf")
    void build_withoutCatalogNumber() {
        assertThat(DownloadFileName.build("사티", "Satie, Erik", "짐노페디", "3 Gymnopédies", null))
                .isEqualTo("사티 - 짐노페디.pdf");
        assertThat(DownloadFileName.build("사티", "Satie, Erik", "짐노페디", "3 Gymnopédies", "  "))
                .isEqualTo("사티 - 짐노페디.pdf");
    }

    @Test
    @DisplayName("한국어 제목이 없으면 원어 제목, 작곡가 한글이 없으면 원어 표기로 폴백")
    void build_fallbacks() {
        assertThat(DownloadFileName.build("쇼팽", "Chopin, Frédéric", null, "Nocturnes, Op.9", "Op.9"))
                .isEqualTo("쇼팽 - Nocturnes, Op.9 (Op.9).pdf");
        assertThat(DownloadFileName.build("", "Chopin, Frédéric", "", "Nocturnes, Op.9", "Op.9"))
                .isEqualTo("Chopin, Frédéric - Nocturnes, Op.9 (Op.9).pdf");
        assertThat(DownloadFileName.build(null, "Chopin, Frédéric", "  ", "Nocturnes, Op.9", null))
                .isEqualTo("Chopin, Frédéric - Nocturnes, Op.9.pdf");
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
    @DisplayName("악센트·비ASCII 원어 표기는 그대로 둔다(파일명은 UTF-8, 정규화 대상 아님)")
    void build_keepsNonAscii() {
        assertThat(DownloadFileName.build(null, "Bądarzewska-Baranowska, Tekla",
                null, "La prière d'une vierge, Op.4", "Op.4"))
                .isEqualTo("Bądarzewska-Baranowska, Tekla - La prière d'une vierge, Op.4 (Op.4).pdf");
    }
}
