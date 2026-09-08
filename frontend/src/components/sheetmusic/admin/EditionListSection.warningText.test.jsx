import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { EditionListSection } from "./EditionListSection.jsx";
import { renderWithProviders } from "../../../test/renderWithProviders.jsx";
import { mockFetch } from "../../../test/apiMock.js";
import { adminEdition } from "../../../test/fixtures.js";
import { expectNoText, findText } from "../../../test/text.js";

// frontend-dev 가 직접 쓴 보완 테스트 (TDD 내부 구현 세부).
// senior-dev 의 EditionListSection.test.jsx 는 경고 문구의 앞부분만 고정한다.
// 02 §5-6 표의 완성 문구와, 악장 번호를 못 읽은 판본(movementNumber=null)의 폴백을 여기서 고정한다.
const RECOMMEND = /\/api\/admin\/works\/21\/recommended-edition$/;
const composer = { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van", deathYear: 1827 };

function renderWithWarnings(edition, warnings) {
  mockFetch([
    {
      url: RECOMMEND,
      method: "PUT",
      data: { workId: 21, previousEditionId: null, editionId: edition.id, workStatus: "UNKNOWN", warnings },
    },
  ]);
  return renderWithProviders(
    <EditionListSection
      workId={21}
      editions={[edition]}
      recommendedEditionId={null}
      candidateEditionId={null}
      composer={composer}
      onChanged={vi.fn()}
      onToast={vi.fn()}
    />,
    { route: "/admin/works/21", path: "/admin/works/:id", auth: "admin" },
  );
}

describe("EditionListSection — 추천 지정 경고 문구 (02 §5-6)", () => {
  it("ARRANGEMENT · PARTIAL_SCOPE 는 02 §5-6 표의 완성 문구를 그대로 쓴다", async () => {
    const user = userEvent.setup();
    const edition = adminEdition({ id: 302, isRecommended: false, kind: "ARRANGEMENT", scope: "MOVEMENT", movementNumber: 2 });
    renderWithWarnings(edition, ["ARRANGEMENT", "PARTIAL_SCOPE"]);
    await user.click(within(screen.getByTestId("admin-edition-row-302")).getByRole("button", { name: "추천으로 지정" }));
    await findText("이 판본은 편곡이에요. 사용자가 원곡 악보를 기대하고 받을 수 있어요");
    await findText("이 판본은 2악장만 들어 있어요. 곡 전체가 아니에요");
  });

  it("악장 번호를 못 읽은 판본이면 '이 판본은 일부만 들어 있어요'", async () => {
    const user = userEvent.setup();
    const edition = adminEdition({ id: 302, isRecommended: false, scope: "MOVEMENT", movementNumber: null, sectionLabel: null });
    renderWithWarnings(edition, ["PARTIAL_SCOPE"]);
    await user.click(within(screen.getByTestId("admin-edition-row-302")).getByRole("button", { name: "추천으로 지정" }));
    await findText("이 판본은 일부만 들어 있어요. 곡 전체가 아니에요");
    expectNoText("null악장");
  });

  it("warnings 가 빈 배열이면 경고 줄이 없다", async () => {
    const user = userEvent.setup();
    const edition = adminEdition({ id: 302, isRecommended: false });
    renderWithWarnings(edition, []);
    await user.click(within(screen.getByTestId("admin-edition-row-302")).getByRole("button", { name: "추천으로 지정" }));
    await findText("판본 (1개)");
    expectNoText("이 판본은");
  });
});
