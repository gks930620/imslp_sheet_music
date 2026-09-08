package com.test.test.sheetmusic;

import com.test.test.sheetmusic.crawl.ParsedWorkPage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 건반 독주 판정 (02_API_명세서 §6-11) — TDD Red.
 *
 * <p>컨트롤러를 거치지 않는 순수 판정 함수라 작은 단위테스트로 덮는다(컨벤션 §6 예외).
 * 통합 경로(수집 → 숨김 여부)는 {@code CrawlKeyboardSoloIntegrationTest} 가 본다.
 *
 * <p>배경: 1차 수집에서 바흐 인벤션·평균율·안나 막달레나 노트북이 IMSLP 의 harpsichord/keyboard 분류 때문에
 * `NOT_PIANO_SOLO` 로 숨겨졌다. 이 곡들은 한국 피아노 학습자의 핵심 레퍼토리다.
 */
class KeyboardSoloRuleTest {

    // ===== 카테고리 신호 (주 신호) =====

    @Test
    @DisplayName("단일 건반악기 카테고리와 정확히 일치하면 건반 독주다")
    void single_keyboard_instrument_category_is_solo() {
        assertThat(page(null).isPianoSolo(List.of("Bach,_Johann_Sebastian", "For_piano"))).isTrue();
        assertThat(page(null).isPianoSolo(List.of("Bach,_Johann_Sebastian", "For_harpsichord"))).isTrue();
        assertThat(page(null).isPianoSolo(List.of("For_clavichord"))).isTrue();
        assertThat(page(null).isPianoSolo(List.of("For_keyboard"))).isTrue();
        assertThat(page(null).isPianoSolo(List.of("For_cembalo"))).isTrue();
        assertThat(page(null).isPianoSolo(List.of("For_fortepiano"))).isTrue();
        // 밑줄 없는 표기도 같은 값이다
        assertThat(page(null).isPianoSolo(List.of("For harpsichord"))).isTrue();
    }

    @Test
    @DisplayName("오르간은 건반 독주로 보지 않는다 — 페달 성부가 있어 피아노로 그대로 칠 수 없다")
    void organ_is_not_keyboard_solo() {
        assertThat(page("organ").isPianoSolo(List.of("For_organ", "Scores_featuring_the_organ"))).isFalse();
        assertThat(page(null).isPianoSolo(List.of("For_organ"))).isFalse();
    }

    @Test
    @DisplayName("악기가 여럿인 카테고리는 정확히 일치하지 않으므로 독주가 아니다")
    void multi_instrument_categories_are_not_solo() {
        assertThat(page(null).isPianoSolo(List.of("For_violin,_harpsichord"))).isFalse();
        assertThat(page(null).isPianoSolo(List.of("For_voice,_piano"))).isFalse();
        assertThat(page(null).isPianoSolo(List.of("For_piano_4_hands"))).isFalse();
        assertThat(page(null).isPianoSolo(List.of("For_2_pianos"))).isFalse();
    }

    @Test
    @DisplayName("편곡 카테고리(arr)만 있으면 그 곡 자체는 건반 독주가 아니다")
    void arrangement_only_categories_are_not_solo() {
        assertThat(page("orchestra").isPianoSolo(List.of("For_orchestra", "For_piano_(arr)"))).isFalse();
        assertThat(page(null).isPianoSolo(List.of("For_piano_(arr)", "Scores_featuring_the_piano_(arr)"))).isFalse();
    }

    // ===== Instrumentation 신호 (보조) =====

    @Test
    @DisplayName("Instrumentation 이 건반악기 하나면 카테고리가 없어도 건반 독주다")
    void instrumentation_alone_can_decide() {
        assertThat(page("piano").isPianoSolo(null)).isTrue();
        assertThat(page("harpsichord").isPianoSolo(List.of())).isTrue();
        assertThat(page("keyboard").isPianoSolo(List.of())).isTrue();
        assertThat(page("Harpsichord").isPianoSolo(List.of())).isTrue();
        assertThat(page("  piano  ").isPianoSolo(List.of())).isTrue();
    }

    @Test
    @DisplayName("'harpsichord (or piano)' 처럼 대체 악기를 괄호로 적은 표기도 건반 독주다")
    void alternative_instrument_in_parentheses_is_solo() {
        assertThat(page("harpsichord (or piano)").isPianoSolo(List.of())).isTrue();
        assertThat(page("keyboard (harpsichord or piano)").isPianoSolo(List.of())).isTrue();
        assertThat(page("piano or harpsichord").isPianoSolo(List.of())).isTrue();
    }

    @Test
    @DisplayName("건반이 아닌 악기가 하나라도 섞이면 독주가 아니다")
    void any_non_keyboard_token_disqualifies() {
        assertThat(page("voice, harpsichord").isPianoSolo(List.of())).isFalse();
        assertThat(page("violin, piano").isPianoSolo(List.of())).isFalse();
        assertThat(page("orchestra").isPianoSolo(List.of())).isFalse();
        assertThat(page("2 pianos").isPianoSolo(List.of())).isFalse();
        assertThat(page("piano 4 hands").isPianoSolo(List.of())).isFalse();
    }

    @Test
    @DisplayName("편성 정보가 아예 없으면 판정하지 않는다")
    void empty_signals_are_not_solo() {
        assertThat(page(null).isPianoSolo(null)).isFalse();
        assertThat(page("").isPianoSolo(List.of())).isFalse();
        assertThat(page("   ").isPianoSolo(List.of())).isFalse();
    }

    // ===== 회귀: 기존 판정이 바뀌지 않는다 =====

    @Test
    @DisplayName("기존에 통과하던 피아노곡·막히던 관현악곡의 판정은 그대로다")
    void existing_verdicts_are_unchanged() {
        assertThat(page("piano").isPianoSolo(List.of("Beethoven,_Ludwig_van", "For_piano",
                "Scores_featuring_the_piano", "For_1_player"))).isTrue();
        assertThat(page("orchestra").isPianoSolo(List.of("Beethoven,_Ludwig_van", "Symphonies",
                "For_orchestra", "Scores_featuring_the_orchestra"))).isFalse();
    }

    // ===== 실제로 숨겨졌던 세 곡 =====

    @Test
    @DisplayName("1차 수집에서 숨겨진 바흐 3곡이 이제 건반 독주로 판정된다")
    void the_three_hidden_bach_works_are_now_keyboard_solo() {
        // 15 Inventions, BWV 772-786
        assertThat(page("harpsichord").isPianoSolo(List.of("Bach,_Johann_Sebastian", "For_harpsichord",
                "For_piano_(arr)"))).isTrue();
        // Prelude and Fugue in C major, BWV 846 (평균율 1권 1번)
        assertThat(page("harpsichord").isPianoSolo(List.of("Bach,_Johann_Sebastian", "Preludes", "Fugues",
                "For_harpsichord", "Scores_featuring_the_harpsichord", "For_1_player"))).isTrue();
        // Notebooks for Anna Magdalena Bach — 건반 소품 + 아리아가 섞인 모음집이라
        // Instrumentation 에는 voice 가 함께 적히지만, 단일 건반악기 카테고리가 붙어 있으면 카테고리 신호가 이긴다
        // (모음집 안에 건반 독주 곡이 들어 있다는 뜻이므로 사용자에게 보여야 한다 — 02 §6-11)
        assertThat(page("keyboard, voice").isPianoSolo(List.of("Bach,_Johann_Sebastian", "For_harpsichord",
                "For_voice,_continuo", "Scores_featuring_the_harpsichord"))).isTrue();
        assertThat(page("keyboard").isPianoSolo(List.of("Bach,_Johann_Sebastian", "For_keyboard"))).isTrue();
    }

    private ParsedWorkPage page(String instrumentation) {
        ParsedWorkPage page = new ParsedWorkPage();
        page.setInstrumentation(instrumentation);
        return page;
    }
}
