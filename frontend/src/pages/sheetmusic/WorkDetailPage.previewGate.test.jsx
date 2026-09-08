import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { WorkDetailPage } from "./WorkDetailPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { workDetail, edition } from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

/**
 * 미리보기 노출 규칙 — 기획 §F3-6 · §5 예외표, 02_API_명세서 §2-3 (2026-09-08 senior-dev. Red).
 *
 * 서버는 비 FREE 판본의 `previewUrl` 을 null 로 내린다. 그런데 `previewUrl == null` 인 이유는 두 가지이고
 * 기획이 정한 문구가 서로 다르다:
 *   - 파일이 아직 없다        → "미리보기 준비 중"
 *   - 판정이 안 끝났다(비 FREE) → "저작권을 확인하는 중이라 미리보기도 아직 보여드릴 수 없어요"
 * 응답에 "왜 없는지" 를 알려주는 별도 필드는 두지 않는다 — `koreaCopyright` 가 이미 그 답이고,
 * 필드를 하나 더 두면 두 값이 어긋날 때 어느 쪽이 참인지 아무도 모른다.
 * 따라서 화면의 분기 규칙은 **koreaCopyright !== "FREE" 이면 저작권 문구**, 그 밖이면 "미리보기 준비 중" 이다.
 */
function renderDetail(data) {
  mockFetch([
    { url: "/api/editions/301/download", method: "HEAD", raw: "" },
    { url: /\/api\/works\/21$/, data },
  ]);
  return renderWithProviders(<WorkDetailPage />, { route: "/works/21", path: "/works/:id" });
}

const COPYRIGHT_HIDDEN = "저작권을 확인하는 중이라 미리보기도 아직 보여드릴 수 없어요";
const NO_PREVIEW_YET = "미리보기 준비 중";

describe("WorkDetailPage — 미리보기 노출 규칙 (기획 §F3-6)", () => {
  it("확인 중(UNKNOWN) 추천 판본: 회색 자리 + 저작권 문구, 이미지는 없다", async () => {
    renderDetail(
      workDetail({
        recommendedEdition: edition({
          koreaCopyright: "UNKNOWN",
          previewUrl: null,
          downloadable: false,
          downloadUrl: null,
        }),
      }),
    );
    await findText("월광 소나타");

    expectText(COPYRIGHT_HIDDEN);
    expectNoText(NO_PREVIEW_YET);
    expect(screen.queryByRole("img", { name: /미리보기/ })).toBeNull();
  });

  it("이용 제한(RESTRICTED) 추천 판본도 같은 문구다 — 다운로드를 막는 이유가 1쪽 이미지에도 그대로 적용된다", async () => {
    renderDetail(
      workDetail({
        recommendedEdition: edition({
          koreaCopyright: "RESTRICTED",
          previewUrl: null,
          downloadable: false,
          downloadUrl: null,
        }),
      }),
    );
    await findText("월광 소나타");

    expectText(COPYRIGHT_HIDDEN);
    expectNoText(NO_PREVIEW_YET);
  });

  it("FREE 인데 미리보기 파일이 없으면 문구가 다르다 — '미리보기 준비 중' (다운로드는 그대로 가능)", async () => {
    renderDetail(workDetail({ recommendedEdition: edition({ previewUrl: null }) }));
    await findText("월광 소나타");

    expectText(NO_PREVIEW_YET);
    expectNoText(COPYRIGHT_HIDDEN);
    expect(screen.getByRole("link", { name: /PDF 받기/ })).toBeInTheDocument();
  });

  it("FREE + 미리보기 있음: 이미지가 그대로 보인다 (회귀 가드 — 받기 전에 화질을 본다)", async () => {
    renderDetail(workDetail());
    await findText("월광 소나타");

    expect(screen.getByRole("img", { name: "월광 소나타 첫 페이지 미리보기" })).toHaveAttribute(
      "src",
      "/uploads/3f2a-c1.png",
    );
    expectNoText(COPYRIGHT_HIDDEN);
  });

  it("'다른 판본' 줄에서는 문구를 반복하지 않는다 — 같은 줄의 저작권 뱃지가 이미 그 사실을 말한다", async () => {
    renderDetail(
      workDetail({
        otherEditions: [edition({ id: 302, koreaCopyright: "UNKNOWN", previewUrl: null, downloadable: false, downloadUrl: null })],
      }),
    );
    await findText("월광 소나타");
    await userEvent.click(await screen.findByRole("button", { name: /다른 판본 보기/ }));

    // 추천 판본은 FREE 라 카드 쪽 문구도 없어야 한다 — 즉 이 문구는 화면 어디에도 없다.
    expectNoText(COPYRIGHT_HIDDEN);
    expectText("저작권 확인 중");
    expect(screen.queryByRole("img", { name: /미리보기/ })).not.toBeNull(); // 추천(FREE) 이미지 하나뿐
    expect(screen.getAllByRole("img", { name: /미리보기/ })).toHaveLength(1);
  });
});
