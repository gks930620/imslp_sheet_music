import { screen } from "@testing-library/react";
import { WorkDetailPage } from "./WorkDetailPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch } from "../../test/apiMock.js";
import { workDetail, edition } from "../../test/fixtures.js";
import { findText } from "../../test/text.js";

/**
 * 못 주는 곡이 내보내는 IMSLP 링크 — 기획 §F3-7 · §10-5, 02_API_명세서 §3-3-2
 * (frontend-dev 작성. 계약은 senior-dev 가 확정했고 backend 가 `imslpCandidateEdition` 을 넣는 중이라
 *  화면은 필드가 아직 없어도 안전해야 한다 — 옵셔널 체이닝 + 작품 페이지 폴백).
 *
 * <p>작품 페이지로 보내면 사용자를 "판본 70개 중 고르기" 앞에 그대로 내려놓는다. 그래서
 * <b>우리가 고른 판본의 파일 페이지</b>로 보낸다.
 * - 추천이 있으면(이용 제한·확인 중이어도) 추천 판본의 `imslpFileUrl`
 * - 추천이 없으면(준비 중) `imslpCandidateEdition.imslpFileUrl`
 * - 둘 다 없으면 곡의 `imslpUrl`(작품 페이지), 그것도 없으면 버튼 생략
 *
 * <p>후보 판본은 파일이 없을 수 있다(`hasFile=false`) — <b>이걸로 다운로드 버튼을 만들지 않는다.</b>
 */
const WORK_URL = "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)";
const CANDIDATE_FILE_URL = "https://imslp.org/wiki/Special:ImagefromIndex/77777";
const RECOMMENDED_FILE_URL = "https://imslp.org/wiki/Special:ImagefromIndex/00014";

function renderDetail(data) {
  mockFetch([
    { url: "/api/editions/301/download", method: "HEAD", raw: "" },
    { url: /\/api\/works\/21$/, data },
  ]);
  return renderWithProviders(<WorkDetailPage />, { route: "/works/21", path: "/works/:id" });
}

function imslpButton() {
  return screen.queryByRole("link", { name: /IMSLP에서 보기/ });
}

const candidate = edition({
  id: 401,
  hasFile: false,
  pageCount: null,
  fileSize: null,
  previewUrl: null,
  koreaCopyright: "UNKNOWN",
  imslpFileUrl: CANDIDATE_FILE_URL,
  downloadable: false,
  downloadUrl: null,
});

describe("WorkDetailPage — 못 주는 곡의 IMSLP 링크 (기획 §F3-7)", () => {
  it("준비 중(추천 없음) + 후보 판본: 후보 판본의 파일 페이지로 보낸다", async () => {
    renderDetail(
      workDetail({
        status: "PREPARING",
        recommendedEdition: null,
        imslpCandidateEdition: candidate,
      }),
    );
    await findText("악보를 준비하고 있어요");

    expect(imslpButton()).toHaveAttribute("href", CANDIDATE_FILE_URL);
    expect(imslpButton()).toHaveAttribute("target", "_blank");
  });

  it("후보 판본으로 다운로드 버튼을 만들지 않는다 — 파일이 없는 판본이다", async () => {
    renderDetail(
      workDetail({
        status: "PREPARING",
        recommendedEdition: null,
        imslpCandidateEdition: candidate,
      }),
    );
    await findText("악보를 준비하고 있어요");

    expect(screen.queryByRole("link", { name: /PDF 받기/ })).not.toBeInTheDocument();
  });

  it("후보 판본이 null 이면 곡의 작품 페이지로 폴백한다", async () => {
    renderDetail(workDetail({ status: "PREPARING", recommendedEdition: null, imslpCandidateEdition: null }));
    await findText("악보를 준비하고 있어요");

    expect(imslpButton()).toHaveAttribute("href", WORK_URL);
  });

  it("서버가 아직 그 필드를 안 내려도(필드 자체 없음) 화면이 깨지지 않고 작품 페이지로 보낸다", async () => {
    const data = workDetail({ status: "PREPARING", recommendedEdition: null });
    delete data.imslpCandidateEdition;
    renderDetail(data);
    await findText("악보를 준비하고 있어요");

    expect(imslpButton()).toHaveAttribute("href", WORK_URL);
  });

  it("후보 판본에 파일 페이지 주소가 없으면 작품 페이지로 폴백한다", async () => {
    renderDetail(
      workDetail({
        status: "PREPARING",
        recommendedEdition: null,
        imslpCandidateEdition: edition({ id: 401, hasFile: false, imslpFileUrl: null }),
      }),
    );
    await findText("악보를 준비하고 있어요");

    expect(imslpButton()).toHaveAttribute("href", WORK_URL);
  });

  it("이용 제한(RESTRICTED): 추천 판본의 파일 페이지로 보낸다 (§3-3-2 — 추천이 있으면 추천을 쓴다)", async () => {
    renderDetail(
      workDetail({
        status: "RESTRICTED",
        recommendedEdition: edition({ koreaCopyright: "RESTRICTED", downloadable: false, downloadUrl: null }),
      }),
    );
    await findText("한국 저작권 기준으로 아직 자유 이용이 어려운 판본이에요");

    expect(imslpButton()).toHaveAttribute("href", RECOMMENDED_FILE_URL);
  });

  it("저작권 확인 중(UNKNOWN): 추천 판본의 파일 페이지로 보낸다", async () => {
    renderDetail(
      workDetail({
        status: "UNKNOWN",
        recommendedEdition: edition({ koreaCopyright: "UNKNOWN", downloadable: false, downloadUrl: null }),
      }),
    );
    await findText("이용 가능 여부를 확인하는 중이에요");

    expect(imslpButton()).toHaveAttribute("href", RECOMMENDED_FILE_URL);
  });

  it("추천 판본에 파일 페이지 주소가 없으면 작품 페이지로 폴백한다", async () => {
    renderDetail(
      workDetail({
        status: "UNKNOWN",
        recommendedEdition: edition({
          koreaCopyright: "UNKNOWN",
          imslpFileUrl: null,
          downloadable: false,
          downloadUrl: null,
        }),
      }),
    );
    await findText("이용 가능 여부를 확인하는 중이에요");

    expect(imslpButton()).toHaveAttribute("href", WORK_URL);
  });

  it("보낼 곳이 아무 데도 없으면 버튼을 만들지 않는다", async () => {
    renderDetail(
      workDetail({ status: "PREPARING", recommendedEdition: null, imslpCandidateEdition: null, imslpUrl: null }),
    );
    await findText("악보를 준비하고 있어요");

    expect(imslpButton()).not.toBeInTheDocument();
  });
});
