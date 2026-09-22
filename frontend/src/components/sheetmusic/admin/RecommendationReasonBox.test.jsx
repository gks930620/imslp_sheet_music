import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RecommendationReasonBox } from "./RecommendationReasonBox.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../../test/apiMock.js";
import {
  adminEditionRecommended,
  adminRecommendationLog,
  autoRecommendationLog,
  clearedRecommendationLog,
  recommendationBlock,
  recommendationEditionRef,
} from "../../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../../test/text.js";
import { formatAdminDateTime } from "../../../lib/format.js";

/** 시각은 지역 시각으로 찍히므로 값을 손으로 적지 않는다(같은 포맷터로 기대값을 만든다 — format.datetime.test.js 와 같은 방침). */
const ADMIN_DECIDED_AT = formatAdminDateTime(adminRecommendationLog().decidedAt);

/**
 * 화면 A-1 "이 판본을 고른 이유" — 화면정의 `06_관리자_판본관리.md` A-1, 계약 02 §4-7-2 · §4-7-1 · §5-6-1.
 *
 * <p>모양은 <b>셋뿐</b>이다: 자동으로 골랐을 때 / 사람이 골랐을 때 / 기록이 없을 때(실데이터 42곡).
 * 그리고 <b>추천이 없는 곡에는 이 상자가 아예 없다</b>(빈 제목도 만들지 않는다 — 8-A 6) —
 * 다만 이력이 남아 있으면 제목 없는 접힘 줄만 남는다(화면정의 A-0).
 *
 * <p>가장 틀리기 쉬운 자리: <b>"후보 1개" 와 "다운로드 수 없음" 은 서로 다른 분기</b>다(기획 §1-3).
 * 후보가 하나였다면 그건 고른 게 아니라 남은 것이라 "1위" 가 거짓 안심을 준다.
 */
const HISTORY = /\/api\/admin\/works\/21\/recommendation-history$/;
const REVIEW = /\/api\/admin\/works\/21\/recommended-edition\/review$/;

function renderBox({
  recommendation = recommendationBlock(),
  hasRecommendation = true,
  reviewed = false,
  recommendedEdition = adminEditionRecommended,
  routes = [],
} = {}) {
  const onChanged = vi.fn();
  const onToast = vi.fn();
  mockFetch([
    { url: REVIEW, method: "PUT", data: { id: 21, recommendationReviewed: true } },
    ...routes,
  ]);
  const utils = renderWithProviders(
    <RecommendationReasonBox
      workId={21}
      hasRecommendation={hasRecommendation}
      reviewed={reviewed}
      recommendation={recommendation}
      recommendedEdition={recommendedEdition}
      onChanged={onChanged}
      onToast={onToast}
    />,
    { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" },
  );
  return { ...utils, onChanged, onToast };
}

describe("A-1 (A) 자동으로 골랐을 때", () => {
  it("규칙 한 줄과 그때의 다운로드 수·후보 수·순위가 보인다 (8-A 2)", () => {
    renderBox({ recommendation: recommendationBlock({ current: autoRecommendationLog() }) });

    expectText("이 판본을 고른 이유");
    expectText("자동으로 골랐어요");
    expectText("IMSLP 다운로드가 가장 많은 것");
    expectText("IMSLP 다운로드 1,204회");
    expectText("후보 3개 중 1위");
  });

  it("후보가 1개뿐이었으면 '1위' 라고 쓰지 않는다 (8-A 3) — 고른 게 아니라 남은 것이다", () => {
    renderBox({
      recommendation: recommendationBlock({
        current: autoRecommendationLog({
          auto: { rule: "MOST_IMSLP_DOWNLOADS", imslpDownloadCount: 1204, candidateCount: 1, rank: 1 },
        }),
      }),
    });

    expectText("고를 수 있는 판본이 이것 하나뿐이었어요");
    expectNoText("1위");
  });

  it("다운로드 수가 없던 판본은 '적혀 있지 않은 판본' 으로 말한다 — 0회가 아니다 (후보 수 분기와 별개다)", () => {
    renderBox({
      recommendation: recommendationBlock({
        current: autoRecommendationLog({
          auto: { rule: "MOST_IMSLP_DOWNLOADS", imslpDownloadCount: null, candidateCount: 3, rank: 1 },
        }),
      }),
    });

    expectText("IMSLP 다운로드 수가 적혀 있지 않은 판본이에요");
    expectText("후보 3개 중 1위");
    expectNoText("0회");
    expectNoText("고를 수 있는 판본이 이것 하나뿐이었어요");
  });

  it("자동에는 사유·메모·이전 추천 줄이 없다 — 자동 지정은 언제나 첫 지정이다", () => {
    renderBox({ recommendation: recommendationBlock({ current: autoRecommendationLog() }) });

    expectNoText("이전 추천:");
    expectNoText("님이 골랐어요");
  });
});

describe("A-1 (B) 사람이 골랐을 때", () => {
  it("닉네임·시각·사유 문장·메모·이전 추천이 보인다 (8-A 1·4)", () => {
    renderBox({ recommendation: recommendationBlock({ current: adminRecommendationLog() }) });

    expectText("창희 님이 골랐어요");
    expectText(ADMIN_DECIDED_AT);
    expectText("앞 추천이 이 곡의 악보가 아니었어요");
    expectText("앞 추천은 관현악 총보였음");
    expectText("이전 추천: 전체 악보 · 전곡 · Breitkopf / Lebert / 1862");
  });

  it("닉네임이 비어 있으면 '관리자가 골랐어요' — 로그인 아이디로 떨어지지 않는다", () => {
    renderBox({
      recommendation: recommendationBlock({ current: adminRecommendationLog({ decidedByNickname: null }) }),
    });

    expectText("관리자가 골랐어요");
    expectNoText("님이 골랐어요");
  });

  it("메모가 없으면 메모 줄이 없다", () => {
    renderBox({ recommendation: recommendationBlock({ current: adminRecommendationLog({ note: null }) }) });

    expectNoText("앞 추천은 관현악 총보였음");
    expectText("앞 추천이 이 곡의 악보가 아니었어요");
  });

  it("이전 추천이 없으면 '처음 지정한 추천이에요' 라고 말한다", () => {
    renderBox({
      recommendation: recommendationBlock({ current: adminRecommendationLog({ previousEdition: null }) }),
    });

    expectText("처음 지정한 추천이에요");
    expectNoText("이전 추천:");
  });

  it("사유 6가지가 전부 화면 문구를 갖는다 — 서버는 코드만 주고 문구는 화면이 갖는다", () => {
    const labels = {
      NOT_THIS_WORK: "앞 추천이 이 곡의 악보가 아니었어요",
      BETTER_READABILITY: "이 판본이 더 읽기 좋아요",
      BETTER_FOR_LEARNERS: "이 판본의 편집·운지가 배우는 사람에게 맞아요",
      PREVIOUS_UNAVAILABLE: "앞 추천은 지금 받을 수 없어요",
      COVERS_WHOLE_WORK: "이 판본이 곡 전체를 담고 있어요",
      OTHER: "기타",
    };
    Object.entries(labels).forEach(([code, label]) => {
      const { unmount } = renderBox({
        recommendation: recommendationBlock({ current: adminRecommendationLog({ reason: code, note: null }) }),
      });
      expectText(label);
      unmount();
    });
  });
});

describe("A-1 (C) 기록이 없을 때 — 실데이터 42곡 (8-A 5)", () => {
  it("'기록이 없어요' 안내가 보이고 빈 칸·빈 표가 아니다", () => {
    renderBox({ recommendation: recommendationBlock() });

    expectText("이 판본을 고른 이유");
    expectText("기록이 없어요");
    expectText("근거를 남기는 기능이 생기기 전에 정해졌어요");
    // 지어낸 근거가 보이면 결함이다 (8-E 1)
    expectNoText("후보");
    expectNoText("자동으로 골랐어요");
  });

  it("이력 줄을 두지 않는다 — '기록이 없어요' 가 이미 그 말을 했다", () => {
    renderBox({ recommendation: recommendationBlock() });

    expect(screen.queryByRole("button", { name: /바뀐 이력/ })).not.toBeInTheDocument();
  });

  it("미검수면 확인하라는 안내, 확인했으면 다른 안내가 나온다 — 두 줄이 같은 말로 겹치지 않는다", () => {
    const { unmount } = renderBox({ recommendation: recommendationBlock(), reviewed: false });
    expectText("미리보기를 열어 확인하고");
    unmount();

    renderBox({ recommendation: recommendationBlock(), reviewed: true });
    expectText("이미 미리보기를 확인한 곡이에요");
    expectNoText("미리보기를 열어 확인하고");
  });
});

describe("추천이 없는 곡 (8-A 6)", () => {
  it("추천이 없으면 '이 판본을 고른 이유' 상자가 아예 없다 — 빈 제목도 만들지 않는다", () => {
    renderBox({ hasRecommendation: false, recommendedEdition: null, recommendation: recommendationBlock() });

    expectNoText("이 판본을 고른 이유");
    expectNoText("기록이 없어요");
  });

  it("추천은 없는데 이력이 있으면 접힘 줄만 남는다 — '추천을 뺐어요' 를 볼 자리가 여기뿐이다 (8-B 7)", async () => {
    const user = userEvent.setup();
    renderBox({
      hasRecommendation: false,
      recommendedEdition: null,
      recommendation: recommendationBlock({
        current: null,
        history: [clearedRecommendationLog(), adminRecommendationLog()],
        historyCount: 2,
      }),
    });

    expectNoText("이 판본을 고른 이유");
    await user.click(screen.getByRole("button", { name: /바뀐 이력 \(2\)/ }));
    expectText("추천을 뺐어요");
    expectText("추천 없음");
  });
});

describe("바뀐 이력 (8-D 1·2·3·5)", () => {
  const history = [adminRecommendationLog(), autoRecommendationLog()];

  it("2건 이상일 때만 접힘 헤더가 생기고 기본은 접혀 있다", async () => {
    const user = userEvent.setup();
    renderBox({
      recommendation: recommendationBlock({ current: adminRecommendationLog(), history, historyCount: 2 }),
    });

    const toggle = screen.getByRole("button", { name: /바뀐 이력 \(2\)/ });
    expectNoText("Schirmer");
    await user.click(toggle);
    // 펼치면 한 줄에 언제 · 누가 · 어디서 어디로 · 사유가 있다 (8-D 2)
    expectText(ADMIN_DECIDED_AT);
    expectText("창희 님");
    expectText("Breitkopf / Lebert / 1862 → Peters / Köhler / 1880");
  });

  it("1건이면 헤더를 만들지 않는다 — 펼쳐도 위에 보이는 같은 줄이다 (8-D 5)", () => {
    renderBox({ recommendation: recommendationBlock({ current: adminRecommendationLog() }) });

    expect(screen.queryByRole("button", { name: /바뀐 이력/ })).not.toBeInTheDocument();
  });

  it("자동 지정도 이력에 남고 '누가' 는 '자동' 이다 (8-D 3), 처음 지정이면 왼쪽이 '(처음 지정)'", async () => {
    const user = userEvent.setup();
    renderBox({
      recommendation: recommendationBlock({ current: adminRecommendationLog(), history, historyCount: 2 }),
    });

    await user.click(screen.getByRole("button", { name: /바뀐 이력 \(2\)/ }));
    expectText("자동");
    expectText("(처음 지정) → Breitkopf / Lebert / 1862");
  });

  it("지워진 판본도 그때 표기로 보인다 — 스냅샷이라 id 가 없어도 말할 수 있다", async () => {
    const user = userEvent.setup();
    renderBox({
      hasRecommendation: false,
      recommendedEdition: null,
      recommendation: recommendationBlock({
        current: null,
        history: [clearedRecommendationLog(), adminRecommendationLog({
          edition: recommendationEditionRef({ editionId: null, publisher: "Peters", editor: "Köhler", publishYear: 1880 }),
        })],
        historyCount: 2,
      }),
    });

    await user.click(screen.getByRole("button", { name: /바뀐 이력 \(2\)/ }));
    expectText("Peters / Köhler / 1880");
  });

  it("'더 보기' 는 §4-7-1 을 불러 같은 자리에서 전부 펼친다", async () => {
    const user = userEvent.setup();
    const all = [
      adminRecommendationLog({ id: 96 }), adminRecommendationLog({ id: 95 }), adminRecommendationLog({ id: 94 }),
      adminRecommendationLog({ id: 93 }), adminRecommendationLog({ id: 92 }),
      adminRecommendationLog({ id: 91, note: "여섯 번째 줄" }),
    ];
    renderBox({
      recommendation: recommendationBlock({
        current: all[0], history: all.slice(0, 5), historyCount: 6, hasMore: true,
      }),
      routes: [{ url: HISTORY, data: { workId: 21, historyCount: 6, history: all } }],
    });

    await user.click(screen.getByRole("button", { name: /바뀐 이력 \(6\)/ }));
    expectNoText("여섯 번째 줄");

    await user.click(screen.getByRole("button", { name: "더 보기" }));
    await waitFor(() => expect(findCalls(HISTORY)).toHaveLength(1));
    await findText("여섯 번째 줄");
    expect(screen.queryByRole("button", { name: "더 보기" })).not.toBeInTheDocument();
  });

  it("hasMore 가 false 면 '더 보기' 가 없다", async () => {
    const user = userEvent.setup();
    renderBox({
      recommendation: recommendationBlock({ current: adminRecommendationLog(), history, historyCount: 2 }),
    });

    await user.click(screen.getByRole("button", { name: /바뀐 이력 \(2\)/ }));
    expect(screen.queryByRole("button", { name: "더 보기" })).not.toBeInTheDocument();
  });
});

describe("미검수 줄 (화면정의 A-1) — 같은 뜻의 표시를 하나 더 만들지 않는다", () => {
  it("미검수면 안내 + '미리보기 열기' + '확인함' 이 이 상자 안에 있다", () => {
    renderBox({ recommendation: recommendationBlock({ current: autoRecommendationLog() }) });

    expectText("아직 아무도 미리보기를 확인하지 않았어요");
    expect(screen.getByRole("button", { name: "미리보기 열기" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "확인함" })).toBeInTheDocument();
  });

  it("'확인함' 은 reviewed: true 로 보내고 곡을 다시 부른다 (§5-6-1)", async () => {
    const user = userEvent.setup();
    const { onChanged, onToast } = renderBox({
      recommendation: recommendationBlock({ current: autoRecommendationLog() }),
    });

    await user.click(screen.getByRole("button", { name: "확인함" }));

    await waitFor(() => expect(findCalls(REVIEW)).toHaveLength(1));
    expect(findCalls(REVIEW)[0].method).toBe("PUT");
    expect(findCalls(REVIEW)[0].body).toEqual({ reviewed: true });
    expect(onChanged).toHaveBeenCalled();
    expect(onToast).toHaveBeenCalled();
  });

  it("확인이 끝났으면 표식만 남고 버튼이 없다 — 되돌리는 '확인 해제' 는 유지한다(§5-6-1 D4)", () => {
    renderBox({ recommendation: recommendationBlock({ current: autoRecommendationLog() }), reviewed: true });

    expectText("미리보기를 확인한 추천이에요");
    expect(screen.queryByRole("button", { name: "확인함" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "확인 해제" })).toBeInTheDocument();
  });

  it("미리보기가 없는 추천이면 '미리보기 열기' 가 막히고 이유를 말한다", () => {
    renderBox({
      recommendation: recommendationBlock({ current: autoRecommendationLog() }),
      recommendedEdition: { ...adminEditionRecommended, previewUrl: null },
    });

    expect(screen.getByRole("button", { name: "미리보기 열기" })).toBeDisabled();
    expectText("미리보기가 아직 없어요");
  });
});

describe("불러오지 못했을 때 (화면정의 A-1 상태별 UI)", () => {
  it("근거를 못 받으면 그 자리만 한 줄로 알리고 다시 시도를 준다 — 판본 목록은 막지 않는다", () => {
    renderBox({ recommendation: null });

    expectText("고른 이유를 불러오지 못했어요");
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
    expectNoText("기록이 없어요");
  });
});
