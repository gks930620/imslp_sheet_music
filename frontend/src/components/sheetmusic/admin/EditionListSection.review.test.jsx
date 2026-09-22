import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditionListSection } from "./EditionListSection.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../../test/apiMock.js";
import {
  adminEditionCandidate,
  adminEditionRecommended,
  autoRecommendationLog,
  recommendationBlock,
} from "../../../test/fixtures.js";
import { expectNoText, expectText } from "../../../test/text.js";

/**
 * 추천 판본 "확인함" 토글 — 02_API_명세서 §5-6-1, 기획 §3 F6-4 · §8-17 · §10-8
 * (frontend-dev 작성. 계약은 senior-dev 가 확정했고 백엔드 엔드포인트는 아직 없다 — 화면은 계약대로 부른다).
 *
 * <p><b>2026-09-21 이동</b>: "확인함" 은 더 이상 판본 목록의 추천 행에 있지 않다. 화면정의 06 A-1
 * 개정으로 "이 판본을 고른 이유" 상자 안으로 옮겼다(같은 뜻의 표시를 행과 상자 두 곳에 두지 않는다).
 * 이 파일은 그 이동이 EditionListSection 배선에서 실제로 일어났는지 — 상자에 올바른 props 가 들어가고,
 * 행에는 그 버튼이 더 이상 없는지 — 를 확인한다. 문구·상태별 UI 자체의 세부는
 * {@code RecommendationReasonBox.test.jsx} 가 이미 고정했으므로 여기서 다시 재지 않는다.
 */
const REVIEW = /\/api\/admin\/works\/21\/recommended-edition\/review$/;
const composer = { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van", deathYear: 1827 };
const editions = [adminEditionRecommended, adminEditionCandidate];

function renderSection({ recommendedEditionId = 301, recommendationReviewed = false, routes = [] } = {}) {
  const onChanged = vi.fn();
  const onToast = vi.fn();
  mockFetch([{ url: REVIEW, method: "PUT", data: { id: 21, recommendationReviewed: true } }, ...routes]);
  const utils = renderWithProviders(
    <EditionListSection
      workId={21}
      editions={editions}
      recommendedEditionId={recommendedEditionId}
      candidateEditionId={null}
      composer={composer}
      recommendationReviewed={recommendationReviewed}
      recommendation={recommendationBlock({ current: autoRecommendationLog() })}
      onChanged={onChanged}
      onToast={onToast}
    />,
    { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" },
  );
  return { ...utils, onChanged, onToast };
}

function recommendedRow() {
  return screen.getByTestId("admin-edition-row-301");
}

describe("EditionListSection — 확인함은 이제 행이 아니라 A-1 상자 안에 있다 (02 §5-6-1, 화면정의 06 A-1)", () => {
  it("추천 행 안에는 '확인함' 버튼이 없다 — 상자로 옮겼다", () => {
    renderSection();

    expect(within(recommendedRow()).queryByRole("button", { name: "확인함" })).not.toBeInTheDocument();
    expect(within(recommendedRow()).queryByRole("button", { name: "확인 해제" })).not.toBeInTheDocument();
  });

  it("'확인함' 은 상자 안에 있고, 누르면 reviewed: true 로 보내고 곡을 다시 불러온다", async () => {
    const user = userEvent.setup();
    const { onChanged, onToast } = renderSection();

    await user.click(screen.getByRole("button", { name: "확인함" }));

    await waitFor(() => expect(findCalls(REVIEW)).toHaveLength(1));
    expect(findCalls(REVIEW)[0].method).toBe("PUT");
    expect(findCalls(REVIEW)[0].body).toEqual({ reviewed: true });
    expect(onChanged).toHaveBeenCalled();
    expect(onToast).toHaveBeenCalled();
  });

  it("확인이 끝난 곡은 상자 안 표식과 '확인 해제' 로 바뀐다 — 행에는 여전히 없다", () => {
    renderSection({ recommendationReviewed: true });

    expect(screen.queryByRole("button", { name: "확인함" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "확인 해제" })).toBeInTheDocument();
    expect(within(recommendedRow()).queryByRole("button", { name: "확인 해제" })).not.toBeInTheDocument();
    expectText("미리보기를 확인한 추천이에요");
  });

  it("'확인 해제' 는 reviewed: false 로 보낸다 — 추천을 다시 볼 수 있어야 한다", async () => {
    const user = userEvent.setup();
    renderSection({ recommendationReviewed: true });
    await user.click(screen.getByRole("button", { name: "확인 해제" }));

    await waitFor(() => expect(findCalls(REVIEW)).toHaveLength(1));
    expect(findCalls(REVIEW)[0].body).toEqual({ reviewed: false });
  });

  it("추천 판본이 없으면 상자도 확인 버튼도 없다 — 확인할 대상이 없다", () => {
    renderSection({ recommendedEditionId: null });

    expectNoText("확인함");
    expectNoText("이 판본을 고른 이유");
  });

  it("실패하면 상자 안에 안내가 뜬다", async () => {
    const user = userEvent.setup();
    renderSection({ routes: [{ url: REVIEW, method: "PUT", status: 500, error: "INTERNAL_ERROR" }] });
    await user.click(screen.getByRole("button", { name: "확인함" }));

    await waitFor(() => expectText("처리하지 못했어요"));
  });
});
