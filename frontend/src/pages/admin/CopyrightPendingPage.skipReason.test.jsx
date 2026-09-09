import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CopyrightPendingPage } from "./CopyrightPendingPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { adminComposers, pageResponse, pendingCopyright, pendingCopyrightResponse } from "../../test/fixtures.js";
import { findText } from "../../test/text.js";

/**
 * 대기함 행별 "왜 자동으로 안 열렸나" 표시 — 02_API_명세서 §5-8 `autoJudgeSkipReason` · §7,
 * 기획 `02_저작권_판정_지침.md` 부록 A §A-1 · §A-4.
 *
 * <p><b>왜 필요한가.</b> 대기함은 관리자가 <b>손으로</b> 판정하는 곳이다. 자동 판정을 돌린 뒤 남은 것들은
 * 남은 이유가 저마다 다른데(라이선스가 막았나, 작곡가 몰년이 비었나, 출판이 최근인가), 그 이유에 따라
 * 관리자가 할 일이 다르다 — 몰년이 비었으면 <b>작곡가를 고치면 다음 실행에 자동으로 열리고</b>(§A-2 ②),
 * 편집자 미확인이면 사람이 직접 조사해야 한다. 이유가 없으면 관리자는 1,792건을 구분 없이 노려볼 뿐이다.
 *
 * <p><b>문구는 senior-dev 확정(2026-09-08)</b> — 근거는 `lib/format.autoJudge.test.js` 주석 참고
 * (같은 화면 미리보기 모달이 이미 같은 5개 코드에 쓰고 있는 말이다). designer 에게 열려 있는 것은
 * <b>행 안에서의 자리·색·아이콘</b>이라, 이 테스트는 "그 행 안에 그 말이 있다" 까지만 잠근다.
 */
const PENDING = /\/api\/admin\/copyright\/pending/;
const COMPOSERS = /\/api\/admin\/composers(\?|$)/;
const AUTO_JUDGE = /\/api\/admin\/copyright\/auto-judge$/;

const PREVIEW = {
  dryRun: true,
  targetCount: 1792,
  judgedFree: 812,
  remainingUnknown: 980,
  recommendedAssigned: 17,
  byRule: [
    { rule: "CC_REDISTRIBUTABLE", count: 12 },
    { rule: "PD_NO_EDITOR", count: 300 },
    { rule: "PD_OLD_PUBLICATION", count: 500 },
  ],
  skipped: [
    { reason: "LICENSE_NOT_REDISTRIBUTABLE", count: 400 },
    { reason: "COMPOSER_DEATH_YEAR_UNKNOWN", count: 20 },
    { reason: "COMPOSER_COPYRIGHT_ACTIVE", count: 5 },
    { reason: "PUBLICATION_TOO_RECENT", count: 55 },
    { reason: "EDITOR_UNVERIFIABLE", count: 500 },
  ],
};

function renderPage(data = pendingCopyrightResponse()) {
  mockFetch([
    { url: COMPOSERS, method: "GET", data: pageResponse(adminComposers) },
    { url: PENDING, method: "GET", data },
    { url: AUTO_JUDGE, method: "POST", data: PREVIEW },
  ]);
  return renderWithProviders(<CopyrightPendingPage />, {
    route: "/admin/copyright",
    path: "/admin/copyright",
    auth: "admin",
  });
}

function row(editionId) {
  return screen.getByTestId(`pending-row-${editionId}`);
}

/** 사유 하나만 다른 한 줄짜리 목록 */
function oneRow(autoJudgeSkipReason) {
  return pendingCopyrightResponse({ editions: [pendingCopyright({ autoJudgeSkipReason })] });
}

describe("CopyrightPendingPage — 행별 자동 판정 미적용 사유 (02 §5-8 · §7)", () => {
  it("행마다 사유를 사람 말로 보여준다 (기본 픽스처 2행: 규칙 8, 규칙 1)", async () => {
    renderPage();
    await findText("Nocturnes, Op.9");

    expect(within(row(305)).getByText("편집자 생몰 확인 필요")).toBeInTheDocument();
    expect(within(row(306)).getByText("재배포 허용 라이선스가 아님")).toBeInTheDocument();
  });

  it.each([
    ["LICENSE_NOT_REDISTRIBUTABLE", "재배포 허용 라이선스가 아님"],
    ["COMPOSER_DEATH_YEAR_UNKNOWN", "작곡가 몰년을 모름"],
    ["COMPOSER_COPYRIGHT_ACTIVE", "작곡가 사후 70년 미경과"],
    ["PUBLICATION_TOO_RECENT", "출판 70년 미경과"],
    ["EDITOR_UNVERIFIABLE", "편집자 생몰 확인 필요"],
  ])("사유 %s 는 '%s' 로 보인다", async (reason, phrase) => {
    renderPage(oneRow(reason));
    await findText("Nocturnes, Op.9");

    expect(within(row(305)).getByText(phrase)).toBeInTheDocument();
  });

  it("사유가 null 이면 아무 말도 만들지 않는다 — 자동 판정을 돌리면 열릴 판본이다", async () => {
    renderPage(oneRow(null));
    await findText("Nocturnes, Op.9");

    for (const phrase of [
      "재배포 허용 라이선스가 아님",
      "작곡가 몰년을 모름",
      "작곡가 사후 70년 미경과",
      "출판 70년 미경과",
      "편집자 생몰 확인 필요",
    ]) {
      expect(within(row(305)).queryByText(phrase)).not.toBeInTheDocument();
    }
  });

  it("모르는 코드가 오면 코드를 그대로 보인다 — 남은 일감을 조용히 감추지 않는다", async () => {
    renderPage(oneRow("SOMETHING_NEW"));
    await findText("Nocturnes, Op.9");

    expect(within(row(305)).getByText("SOMETHING_NEW")).toBeInTheDocument();
  });

  it("같은 화면 미리보기 모달과 같은 문구를 쓴다 (문구가 두 벌이 되지 않게)", async () => {
    // 모달은 "편집자 생몰 확인 필요 500개"(§5-11 skipped), 행은 "편집자 생몰 확인 필요"(§5-8).
    // 한 화면에서 같은 코드가 다른 말로 보이면 관리자는 그 둘이 같은 것인지 알 수 없다.
    const user = userEvent.setup();
    renderPage();
    await findText("Nocturnes, Op.9");
    expect(screen.getAllByText(/편집자 생몰 확인 필요/)).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: /자동 판정 실행/ }));
    await screen.findByRole("dialog");

    expect(screen.getAllByText(/편집자 생몰 확인 필요/).length).toBeGreaterThanOrEqual(2);
  });
});
