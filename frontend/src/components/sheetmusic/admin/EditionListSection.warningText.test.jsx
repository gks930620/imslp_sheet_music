import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditionListSection } from "./EditionListSection.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { mockFetch } from "../../../test/apiMock.js";
import { adminEdition, adminEditionRecommended } from "../../../test/fixtures.js";
import { expectNoText, findText } from "../../../test/text.js";

// frontend-dev 가 직접 쓴 보완 테스트 (TDD 내부 구현 세부).
// senior-dev 의 EditionListSection.test.jsx 는 경고 문구를 다루지 않는다(패널로 옮겼으므로).
// 02 §5-6-2 표의 완성 문구와, 악장 번호를 못 읽은 판본(movementNumber=null)의 폴백을
// **A-3 패널 기준**(02 §5-6-2 예고, 즉시 PUT 이 아니라 행 아래 패널을 연 뒤에 읽는 경고)으로 고정한다.
//
// 2026-09-21 이동: "추천으로 지정"이 즉시 PUT 하던 시절엔 §5-6 응답의 warnings 로 행 아래에 경고가
// 떴다. 지금은 패널이 열릴 때 §5-6-2 GET 으로 미리 읽는다(RecommendChangePanel.test.jsx 가 그 계약을
// 고정) — 여기서는 EditionListSection 이 실제 판본 데이터(악장 번호 등)를 패널에 올바르게 넘겨,
// 표의 문구가 이 조합에서도 그대로 나오는지만 본다.
const PREVIEW = /\/api\/admin\/works\/21\/recommended-edition\/preview\?/;
const composer = { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van", deathYear: 1827 };

function renderWithWarnings(edition, warnings) {
  mockFetch([{ url: PREVIEW, data: { workId: 21, editionId: edition.id, warnings } }]);
  return renderWithProviders(
    <EditionListSection
      workId={21}
      editions={[adminEditionRecommended, edition]}
      recommendedEditionId={301}
      candidateEditionId={null}
      composer={composer}
      onChanged={vi.fn()}
      onToast={vi.fn()}
    />,
    { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" },
  );
}

async function openPanel(user, editionId) {
  await user.click(
    within(screen.getByTestId(`admin-edition-row-${editionId}`)).getByRole("button", { name: "추천으로 지정" }),
  );
}

describe("EditionListSection → RecommendChangePanel — 추천 지정 경고 문구 (02 §5-6-2)", () => {
  it("ARRANGEMENT · PARTIAL_SCOPE 는 02 §5-6-2 표의 완성 문구를 그대로 쓴다", async () => {
    const user = userEvent.setup();
    const edition = adminEdition({
      id: 302, isRecommended: false, kind: "ARRANGEMENT", scope: "MOVEMENT", movementNumber: 2,
    });
    renderWithWarnings(edition, ["ARRANGEMENT", "PARTIAL_SCOPE"]);

    await openPanel(user, 302);
    await findText("이 판본은 편곡이에요 — 사용자가 원곡 악보를 기대하고 받을 수 있어요");
    await findText("이 판본은 2악장만 들어 있어요 — 곡 전체가 아니에요");
  });

  it("악장 번호를 못 읽은 판본이면 '일부만 들어 있어요'", async () => {
    const user = userEvent.setup();
    const edition = adminEdition({ id: 302, isRecommended: false, scope: "MOVEMENT", movementNumber: null, sectionLabel: null });
    renderWithWarnings(edition, ["PARTIAL_SCOPE"]);

    await openPanel(user, 302);
    await findText("이 판본은 일부만 들어 있어요 — 곡 전체가 아니에요");
    expectNoText("null악장");
  });

  it("warnings 가 빈 배열이면 경고 상자가 없다", async () => {
    const user = userEvent.setup();
    const edition = adminEdition({ id: 302, isRecommended: false });
    renderWithWarnings(edition, []);

    await openPanel(user, 302);
    await findText("이 판본으로 바꾸기");
    expectNoText("바꾸기 전에 확인할 것");
  });
});
