package com.test.test.sheetmusic.work.dto;

import com.test.test.sheetmusic.edition.dto.EditionDTO;
import com.test.test.sheetmusic.work.Level;
import com.test.test.sheetmusic.work.Section;
import com.test.test.sheetmusic.work.WorkStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 곡 상세 (02 §3-3). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkDetailDTO {

    private Long id;
    private String titleKo;
    private String titleOriginal;
    private ComposerRefDTO composer;
    private List<String> catalogNumbers;
    private Level level;
    private WorkStatus status;
    /**
     * 이 곡이 속한 악기 구분 (02 §0-7 · §3-3, 2026-09-10 추가). 화면은 주소의 구분과 다르면 <b>그 구분으로 전환해</b>
     * 보여준다(기획 04 §1-4) — "찾을 수 없음" 으로 보내지 않는다. 목록 응답({@code WorkSummaryDTO})에는 없다:
     * 목록은 전부 한 구분 안이라 화면이 주소에서 이미 안다.
     */
    private Section section;
    private List<String> aliases;
    private String compositionYear;
    private String musicalKey;
    private String movements;
    private String movementPageGuide;
    /**
     * 수록곡 안내 (02 §3-3, 2026-09-08 추가) — 사람이 쓴 완성 문장을 그대로 내려준다(서버가 조립하지 않는다).
     * 이 값이 있는 곡이 묶음 악보이고, 검색 항목 scopeNote 의 COLLECTION 판정 근거다(§2-2-1).
     */
    private String collectionGuide;
    private String imslpUrl;
    private String composerImslpUrl;
    private EditionDTO recommendedEdition;
    /**
     * 추천을 뺀 <b>파일 있는</b> 판본 전부 (02 §3-3, 2026-09-08 계약 통일 / 기획 §F3-4).
     * 편성으로 거르지 않고 잘리지도 않는다 — 줄로 펼치는 것은 "우리가 줄 수 있는 판본" 뿐이다.
     */
    private List<EditionDTO> otherEditions;
    /**
     * 추천을 뺀 <b>파일 없는</b> 판본 수 (02 §3-3, 2026-09-08 신설) —
     * "IMSLP 에는 이 곡의 다른 악보가 N개 더 있어요" 한 줄이 쓰는 값이고 0 이면 화면이 줄을 만들지 않는다.
     * <b>편성으로 거르지 않는다</b>: 이 숫자가 안내하는 곳은 IMSLP 작품 페이지이고 거기 있는 것은
     * 편곡·파트보를 포함한 전부라, 걸러 세면 사용자가 링크를 눌러 보는 것과 어긋난다.
     */
    private int imslpOnlyCount;
    /**
     * 못 주는 곡이 내보내는 IMSLP 파일 페이지의 재료 (02 §3-3-2, 기획 §F3-7 · §10-5).
     * {@code recommendedEdition == null} 일 때만 값이 있고, 전체 악보·전곡 판본이 없으면 null 이다.
     * 파일 없는 판본이므로 {@code hasFile=false}·{@code downloadable=false} — 다운로드 버튼의 근거가 아니다.
     */
    private EditionDTO imslpCandidateEdition;
    /** 전체 판본 중 downloadable=true 수 — 목록과 무관하게 전체 기준이다 (02 §3-3). */
    private int downloadableOtherCount;
    private List<WorkSummaryDTO> sameComposerWorks;
    /**
     * 이 요청의 <b>로그인 주체</b>가 이 곡을 즐겨찾기했는가 (02 §3-3, 2026-09-20 신설).
     * <b>비로그인이면 언제나 false</b> — 곡 상세는 공개 API 그대로이고 401 이 아니다.
     *
     * <p>따로 물으면 버튼이 꺼짐 → 켜짐으로 깜빡이므로 곡 상세가 함께 답한다(화면정의 09 §6 S5).
     * 목록({@code WorkSummaryDTO})에는 <b>넣지 않는다</b> — 곡 카드에 즐겨찾기 표시를 두지 않기로 확정했고,
     * 쓰는 곳 없는 필드를 먼저 두지 않는다(기획 05 §10 8-6).
     */
    private boolean favorited;
}
