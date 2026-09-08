package com.test.test.sheetmusic.crawl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 작품 페이지 파싱 결과 (00 조사 §1). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedWorkPage {

    /**
     * 건반 독주로 인정하는 악기 (02 §6-11). {@code organ} 은 넣지 않는다 —
     * 페달 성부가 별도 보표라 손만으로 그대로 칠 수 없다.
     */
    private static final Set<String> KEYBOARD_SOLO = Set.of(
            "piano", "pianoforte", "fortepiano", "harpsichord", "clavichord", "cembalo", "keyboard");

    private String canonicalUrl;
    private String titleOriginal;
    private String composerNameOriginal;
    private String composerImslpUrl;
    private List<String> catalogNumbers;
    private List<String> aliases;
    private String musicalKey;
    private String compositionYear;
    private String movements;
    private String instrumentation;
    private List<ParsedEdition> editions;

    public List<String> getCatalogNumbers() {
        return catalogNumbers == null ? new ArrayList<>() : catalogNumbers;
    }

    public List<String> getAliases() {
        return aliases == null ? new ArrayList<>() : aliases;
    }

    public List<ParsedEdition> getEditions() {
        return editions == null ? new ArrayList<>() : editions;
    }

    /**
     * 건반 독주 판정 (02 §6-11). 이름은 {@code isPianoSolo} 그대로 두되 판정 대상은 건반악기 전반이다
     * (바흐 건반곡이 harpsichord/keyboard 로 분류돼 숨겨지던 문제).
     *
     * <ol>
     *   <li><b>카테고리 정확 일치</b> — {@code For {건반악기}} 와 정확히 같은 카테고리가 하나라도 있으면 건반 독주다.
     *       악기가 여럿이면 카테고리 이름 자체가 길어지므로({@code For violin, harpsichord}) 정확 일치가 곧 독주 신호다.
     *       걸리면 {@code Instrumentation} 은 보지 않는다 — 모음집(안나 막달레나 노트북)은 편성에 voice 가 섞여도
     *       단일 건반 카테고리가 있으면 그 안에 건반 독주곡이 들어 있다는 뜻이다.</li>
     *   <li><b>Instrumentation 전 토큰 일치</b> — 카테고리가 말이 없으면 편성을 본다.
     *       비어 있지 않고 모든 토큰이 건반악기여야 한다(하나라도 섞이면 반주·앙상블).</li>
     * </ol>
     */
    public boolean isPianoSolo(List<String> categories) {
        return hasSoloKeyboardCategory(categories) || instrumentationIsKeyboardOnly();
    }

    /** 1단계 — {@code For_harpsichord} → {@code for harpsichord} 로 눌러 정확 일치를 본다. */
    private boolean hasSoloKeyboardCategory(List<String> categories) {
        if (categories == null) {
            return false;
        }
        for (String category : categories) {
            if (category == null) {
                continue;
            }
            String normalized = squeeze(category.replace('_', ' '));
            for (String instrument : KEYBOARD_SOLO) {
                if (normalized.equals("for " + instrument)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 2단계 — {@code ,} {@code ;} {@code /} 로 나눈 토큰이 하나 이상이고 전부 건반악기인가. */
    private boolean instrumentationIsKeyboardOnly() {
        if (instrumentation == null || instrumentation.isBlank()) {
            return false;
        }
        boolean any = false;
        for (String rawToken : instrumentation.split("[,;/]")) {
            String token = keyboardToken(rawToken);
            if (token.isEmpty()) {
                continue;
            }
            if (!KEYBOARD_SOLO.contains(token)) {
                return false;
            }
            any = true;
        }
        return any;
    }

    /** 괄호 부분과 {@code or …} 뒤를 떼어낸다: {@code harpsichord (or piano)} → {@code harpsichord}. */
    private String keyboardToken(String rawToken) {
        String token = rawToken.replaceAll("\\([^)]*\\)", " ");
        token = token.replaceAll("(?i)\\bor\\b.*$", " ");
        return squeeze(token);
    }

    /** 소문자 + 앞뒤 공백 제거 + 연속 공백 하나로. */
    private static String squeeze(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
