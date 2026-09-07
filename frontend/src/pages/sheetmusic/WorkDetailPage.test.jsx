import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { WorkDetailPage } from "./WorkDetailPage.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { mockFetch, findCalls } from "../../test/apiMock.js";
import {
  workDetail,
  edition,
  editionOtherFree,
  editionOtherRestricted,
  editionOtherNoFile,
  workElise,
} from "../../test/fixtures.js";
import { expectNoText, expectText, findText } from "../../test/text.js";

// 03_곡상세.md — 주인공은 추천 판본 카드 하나와 다운로드 버튼 하나.
// 다운로드는 <a href={downloadUrl} download> (02_API §7: fetch 로 blob 받지 않는다)

function renderDetail(data, { status = 200, reject = false } = {}) {
  mockFetch([
    // 다운로드 사전 확인(HEAD, §3-4)은 성공이 기본값 — 실패 안내는 아래 "다운로드 실패 안내" 묶음에서 따로 본다
    { url: "/api/editions/301/download", method: "HEAD", raw: "" },
    reject
      ? { url: /\/api\/works\/21$/, reject: true }
      : status === 200
        ? { url: /\/api\/works\/21$/, data }
        : { url: /\/api\/works\/21$/, status, error: "NOT_FOUND", message: "곡을 찾을 수 없어요" },
  ]);
  return renderWithProviders(<WorkDetailPage />, { route: "/works/21", path: "/works/:id" });
}

function downloadLink() {
  return screen.queryByRole("link", { name: /PDF 받기/ });
}

describe("WorkDetailPage — 곡 정보 영역", () => {
  it("제목·원어·작곡가 링크·작품번호·난이도·별칭·연도/조성/악장·악장 안내", async () => {
    renderDetail(workDetail());
    await findText("월광 소나타");
    expect(screen.getByRole("heading", { level: 1, name: "월광 소나타" })).toBeInTheDocument();
    expect(screen.getByText("Piano Sonata No.14, Op.27 No.2")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /베토벤 \(Beethoven, Ludwig van\)/ })).toHaveAttribute("href", "/composers/4");
    expectText("Op.27 No.2");
    expect(screen.getByText("중급")).toBeInTheDocument();
    expectText("이렇게도 불러요: 월광, 월광 소나타, Moonlight Sonata");
    expectText("1801");
    expectText("C-sharp minor");
    expectText("악장 안내: 1악장 1쪽 · 2악장 6쪽 · 3악장 9쪽 (추천 판본 기준)");
  });

  it("별칭 0개면 '이렇게도 불러요' 줄 생략, 악장 안내 없으면 그 줄 없음", async () => {
    renderDetail(workDetail({ aliases: [], movementPageGuide: null }));
    await findText("월광 소나타");
    expectNoText("이렇게도 불러요");
    expectNoText("악장 안내");
  });

  it("한국어 제목이 없으면 원어 제목이 h1, 원어 줄 생략", async () => {
    renderDetail(workDetail({ titleKo: null }));
    await findText("Piano Sonata No.14, Op.27 No.2");
    expect(screen.getByRole("heading", { level: 1, name: "Piano Sonata No.14, Op.27 No.2" })).toBeInTheDocument();
    expect(screen.getAllByText("Piano Sonata No.14, Op.27 No.2")).toHaveLength(1);
  });
});

describe("WorkDetailPage — 추천 판본 카드 (바로 받기 가능)", () => {
  it("라벨·종류·쪽수/크기·판본 정보·뱃지·IMSLP 표기·출처 링크", async () => {
    renderDetail(workDetail());
    await findText("추천 판본");
    expectText("전체 악보 · 전곡");
    expectText("12쪽 · 2.4MB");
    expectText("Breitkopf & Härtel · 1862 · 편집 Sigmund Lebert · 스캔 Unknown");
    expect(screen.getByText("한국에서 자유 이용 가능")).toBeInTheDocument();
    expectText("IMSLP 표기: Public Domain");
    const source = screen.getByRole("link", { name: /원본 페이지 보기/ });
    expect(source).toHaveAttribute("href", "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)");
    expect(source).toHaveAttribute("target", "_blank");
    expect(source).toHaveAttribute("rel", expect.stringContaining("noreferrer"));
    expect(screen.getByRole("link", { name: /파일 페이지/ })).toHaveAttribute("href", "https://imslp.org/wiki/Special:ImagefromIndex/00014");
  });

  // 02_API §7: 파일은 브라우저가 링크로 받는다 — 화면이 fetch(GET)로 바이트를 가져오지 않는다(메모리·진행 표시).
  // 클릭할 때 나가는 요청은 사전 확인용 HEAD 뿐이다(§3-4, 아래 "다운로드 실패 안내" 묶음).
  it("'PDF 받기 · 2.4MB' 는 <a href={downloadUrl} download> 이고, 파일을 fetch(GET)로 받지 않는다", async () => {
    const user = userEvent.setup();
    renderDetail(workDetail());
    await findText("추천 판본");
    const link = screen.getByRole("link", { name: /PDF 받기 · 2\.4MB/ });
    expect(link).toHaveAttribute("href", "/api/editions/301/download");
    expect(link).toHaveAttribute("download");
    document.addEventListener("click", (e) => e.preventDefault(), { once: true, capture: true });
    await user.click(link);

    await waitFor(() => expect(findCalls(/\/api\/editions\/301\/download/).length).toBeGreaterThan(0));
    const nonHead = findCalls(/\/api\/editions\/301\/download/).filter((call) => call.method !== "HEAD");
    expect(nonHead).toHaveLength(0);
    // 확인 창·대기 화면 없음
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("20MB 이상이면 큰 파일 경고 한 줄, 버튼은 그대로 활성", async () => {
    renderDetail(workDetail({ recommendedEdition: edition({ fileSize: 26214400, largeFile: true }) }));
    await findText("추천 판본");
    expect(document.body.textContent).toMatch(/파일이 큽니다 \(25(\.0)?MB\)\. 모바일 데이터에 주의하세요/);
    expect(screen.getByRole("link", { name: /PDF 받기 · 25(\.0)?MB/ })).toHaveAttribute("href", "/api/editions/301/download");
  });

  it("20MB 미만이면 경고 없음", async () => {
    renderDetail(workDetail());
    await findText("추천 판본");
    expectNoText("파일이 큽니다");
  });

  it("미리보기 있음: alt 문구, 누르면 라이트박스(캡션) 열리고 닫기 버튼으로 닫힌다", async () => {
    const user = userEvent.setup();
    renderDetail(workDetail());
    await findText("추천 판본");
    const img = screen.getByAltText("월광 소나타 첫 페이지 미리보기");
    expect(img).toHaveAttribute("src", "/uploads/3f2a-c1.png");
    await user.click(img);
    await findText("월광 소나타 — 전체 악보 · 전곡 (1쪽/12쪽)");
    await user.click(screen.getByRole("button", { name: "닫기" }));
    expectNoText("(1쪽/12쪽)");
  });

  it("미리보기 없음(파일은 있음): '미리보기 준비 중' 자리, 다운로드는 활성", async () => {
    renderDetail(workDetail({ recommendedEdition: edition({ previewUrl: null }) }));
    await findText("추천 판본");
    expectText("미리보기 준비 중");
    expect(downloadLink()).toHaveAttribute("href", "/api/editions/301/download");
  });

  it("판본 정보는 있는 것만 ' · ' 로, 모두 없으면 줄 생략", async () => {
    renderDetail(workDetail({ recommendedEdition: edition({ publisher: null, publishYear: null, editor: null, scanner: null }) }));
    await findText("추천 판본");
    expectNoText("편집");
    expectNoText("스캔");
  });

  it("CC 판본은 라이선스명 · 저작자 줄", async () => {
    renderDetail(workDetail({ recommendedEdition: edition({ ccLicenseName: "CC BY-SA 4.0", ccAttribution: "홍길동" }) }));
    await findText("추천 판본");
    expectText("CC BY-SA 4.0 · 편집: 홍길동");
  });
});

describe("WorkDetailPage — 상태별 추천 판본 카드", () => {
  it("준비 중(추천 판본 없음): 안내 문구 + 'IMSLP에서 보기', 다운로드 버튼·'추천 판본' 라벨 없음", async () => {
    renderDetail(workDetail({ status: "PREPARING", recommendedEdition: null, sameComposerWorks: [] }));
    await findText("악보를 준비하고 있어요");
    expectText("IMSLP 원본 페이지에서 먼저 볼 수 있어요");
    const link = screen.getByRole("link", { name: /IMSLP에서 보기/ });
    expect(link).toHaveAttribute("href", "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)");
    expect(link).toHaveAttribute("target", "_blank");
    expect(downloadLink()).not.toBeInTheDocument();
    expectNoText("추천 판본");
    expect(screen.getByRole("heading", { level: 1, name: "월광 소나타" })).toBeInTheDocument();
  });

  it("준비 중인데 IMSLP 링크조차 없으면 버튼 생략", async () => {
    renderDetail(workDetail({ status: "PREPARING", recommendedEdition: null, imslpUrl: null }));
    await findText("악보를 준비하고 있어요");
    expect(screen.queryByRole("link", { name: /IMSLP에서 보기/ })).not.toBeInTheDocument();
  });

  it("준비 중(추천은 있는데 파일만 없음): 안내 아래 판본 정보 표시", async () => {
    renderDetail(
      workDetail({
        status: "PREPARING",
        recommendedEdition: edition({ hasFile: false, fileSize: null, pageCount: null, previewUrl: null, downloadable: false, downloadUrl: null }),
      }),
    );
    await findText("악보를 준비하고 있어요");
    expectText("Breitkopf & Härtel");
    expect(downloadLink()).not.toBeInTheDocument();
  });

  it("한국에서 이용 제한(RESTRICTED): 제한 안내 + 'IMSLP에서 보기', 뱃지, 다운로드 없음", async () => {
    renderDetail(
      workDetail({
        status: "RESTRICTED",
        recommendedEdition: edition({ koreaCopyright: "RESTRICTED", downloadable: false, downloadUrl: null }),
      }),
    );
    await findText("한국 저작권 기준으로 아직 자유 이용이 어려운 판본이에요. IMSLP 원본 페이지에서 각자 판단해 이용해 주세요");
    expect(screen.getByText("한국에서 이용 제한")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /IMSLP에서 보기/ })).toBeInTheDocument();
    expect(downloadLink()).not.toBeInTheDocument();
    expectText("12쪽 · 2.4MB");
    expectText("IMSLP 표기: Public Domain");
  });

  it("저작권 확인 중(UNKNOWN): '이용 가능 여부를 확인하는 중이에요' + 'IMSLP에서 보기', 다운로드 없음", async () => {
    renderDetail(
      workDetail({
        status: "UNKNOWN",
        recommendedEdition: edition({ koreaCopyright: "UNKNOWN", downloadable: false, downloadUrl: null }),
      }),
    );
    await findText("이용 가능 여부를 확인하는 중이에요");
    expect(screen.getByText("저작권 확인 중")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /IMSLP에서 보기/ })).toBeInTheDocument();
    expect(downloadLink()).not.toBeInTheDocument();
  });

  it("추천은 제한인데 다른 판본에 바로 받기 가능이 있으면 안내 + 다른 판본 자동 펼침", async () => {
    renderDetail(
      workDetail({
        status: "RESTRICTED",
        recommendedEdition: edition({ koreaCopyright: "RESTRICTED", downloadable: false, downloadUrl: null }),
        otherEditions: [editionOtherFree, editionOtherRestricted],
        downloadableOtherCount: 1,
      }),
    );
    await findText("바로 받을 수 있는 다른 판본이 1개 있어요");
    expect(screen.getByRole("button", { name: "보기" })).toBeInTheDocument();
    // 자동 펼침: 행이 이미 렌더돼 있다
    expectText("Peters");
    expect(screen.getByRole("link", { name: /PDF 받기/ })).toHaveAttribute("href", "/api/editions/302/download");
  });
});

describe("WorkDetailPage — 다른 판본 보기", () => {
  it("다른 판본이 없으면 영역 자체가 없다", async () => {
    renderDetail(workDetail({ otherEditions: [] }));
    await findText("추천 판본");
    expectNoText("다른 판본 보기");
  });

  it("기본 접힘: '다른 판본 보기 (3개)' 만 보이고 펼치면 행이 나온다", async () => {
    const user = userEvent.setup();
    renderDetail(workDetail({ otherEditions: [editionOtherFree, editionOtherRestricted, editionOtherNoFile] }));
    await findText("다른 판본 보기 (3개)");
    expectNoText("Peters");
    await user.click(screen.getByRole("button", { name: /다른 판본 보기 \(3개\)/ }));
    expectText("전체 악보 · 2악장만");
    expectText("8쪽 · 1.1MB");
    expectText("Peters");
    expectText("파일 없음");
    // 추천 카드는 "PDF 받기 · 2.4MB", 판본 행은 정확히 "PDF 받기"(03_곡상세.md §판본 행) — 행 버튼만 정확 일치로 집는다
    expect(screen.getByRole("link", { name: "PDF 받기" })).toHaveAttribute("href", "/api/editions/302/download");
    expect(screen.getAllByRole("link", { name: /IMSLP에서 보기/ })).toHaveLength(2);
    expect(screen.getByText("한국에서 이용 제한")).toBeInTheDocument();
    expect(screen.getByText("저작권 확인 중")).toBeInTheDocument();
  });

  it("추천 판본이 바로 받기 가능이면 다른 판본이 있어도 접힌 채로 시작한다", async () => {
    renderDetail(workDetail({ otherEditions: [editionOtherFree], downloadableOtherCount: 1 }));
    await findText("다른 판본 보기 (1개)");
    expectNoText("Peters");
    expectNoText("바로 받을 수 있는 다른 판본이");
  });
});

describe("WorkDetailPage — 같은 작곡가의 다른 곡·통신 상태", () => {
  it("같은 작곡가의 다른 곡 최대 5개, 링크", async () => {
    renderDetail(workDetail({ sameComposerWorks: [workElise] }));
    await findText("같은 작곡가의 다른 곡");
    const section = screen.getByRole("heading", { name: "같은 작곡가의 다른 곡" }).parentElement;
    expect(within(section).getByRole("link", { name: /엘리제를 위하여/ })).toHaveAttribute("href", "/works/22");
  });

  it("같은 작곡가 곡 0개면 영역 생략", async () => {
    renderDetail(workDetail({ sameComposerWorks: [] }));
    await findText("추천 판본");
    expectNoText("같은 작곡가의 다른 곡");
  });

  it("404(없는 곡·숨김 곡) → '찾을 수 없는 페이지예요' 화면 (주소 유지)", async () => {
    const { getLocation } = renderDetail(null, { status: 404 });
    await findText("찾을 수 없는 페이지예요");
    expectText("주소가 틀렸거나 내려간 곡이에요");
    expect(screen.getByRole("link", { name: "홈으로" })).toHaveAttribute("href", "/");
    expect(getLocation().pathname).toBe("/works/21");
  });

  it("불러오기 실패 → '연결을 확인해 주세요' + '다시 시도' 로 재요청", async () => {
    const user = userEvent.setup();
    renderDetail(null, { reject: true });
    await findText("연결을 확인해 주세요");
    mockFetch([{ url: /\/api\/works\/21$/, data: workDetail() }]);
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await findText("추천 판본");
  });
});

// ── 다운로드 실패 안내 (03_곡상세.md §동작·통신 상태, 02_API §3-4 HEAD + §7) ─────────────────
//
// `<a href download>` 는 실패를 감지할 수 없다. 그래서 클릭 시 같은 주소로 HEAD 를 한 번 보내
// 받을 수 있는지 확인하고, 실패면 버튼 아래 InlineAlert danger 를 띄운다.
// (네이티브 다운로드는 막지 않는다 — preventDefault 로 가로챈 뒤 다시 트리거하는 방식은
//  브라우저마다 취급이 달라 링크의 기본 동작을 그대로 두고 확인만 곁들인다. 03 §13)
function renderWithDownloadCheck(headRule) {
  mockFetch([{ url: /\/api\/works\/21$/, data: workDetail() }, { method: "HEAD", ...headRule }]);
  return renderWithProviders(<WorkDetailPage />, { route: "/works/21", path: "/works/:id" });
}

/** 클릭이 jsdom 의 미구현 내비게이션으로 새지 않게 한 번만 기본 동작을 막는다 (다른 테스트와 같은 방식) */
async function clickDownload(user) {
  document.addEventListener("click", (e) => e.preventDefault(), { once: true, capture: true });
  await user.click(screen.getByRole("link", { name: /PDF 받기/ }));
}

describe("WorkDetailPage — 다운로드 실패 안내", () => {
  it("PDF 받기를 누르면 같은 주소로 HEAD 를 보내 받을 수 있는지 확인한다", async () => {
    const user = userEvent.setup();
    renderWithDownloadCheck({ url: "/api/editions/301/download", raw: "" });
    await findText("추천 판본");
    await clickDownload(user);

    await waitFor(() => expect(findCalls(/\/api\/editions\/301\/download/)).toHaveLength(1));
    expect(findCalls(/\/api\/editions\/301\/download/)[0].method).toBe("HEAD");
  });

  it("확인이 503(FILE_UNAVAILABLE)이면 버튼 아래 danger 안내 + 'IMSLP에서 보기', 버튼은 그대로 남는다", async () => {
    const user = userEvent.setup();
    renderWithDownloadCheck({ url: "/api/editions/301/download", status: 503, error: "FILE_UNAVAILABLE" });
    await findText("추천 판본");
    await clickDownload(user);

    await findText("지금은 파일을 받을 수 없어요. 잠시 후 다시 시도해 주세요");
    expect(document.querySelector(".inline-alert-danger")).not.toBeNull();
    const imslpLink = screen.getByRole("link", { name: /IMSLP에서 보기/ });
    expect(imslpLink).toHaveAttribute("href", workDetail().imslpUrl);
    expect(imslpLink).toHaveAttribute("target", "_blank");
    // 버튼은 다시 활성 (다시 눌러볼 수 있어야 한다)
    expect(screen.getByRole("link", { name: /PDF 받기/ })).toHaveAttribute("href", "/api/editions/301/download");
  });

  it("확인이 네트워크 오류여도 같은 안내를 띄운다", async () => {
    const user = userEvent.setup();
    renderWithDownloadCheck({ url: "/api/editions/301/download", reject: true });
    await findText("추천 판본");
    await clickDownload(user);

    await findText("지금은 파일을 받을 수 없어요. 잠시 후 다시 시도해 주세요");
  });

  it("확인이 성공하면 안내를 띄우지 않는다", async () => {
    const user = userEvent.setup();
    renderWithDownloadCheck({ url: "/api/editions/301/download", raw: "" });
    await findText("추천 판본");
    await clickDownload(user);

    await waitFor(() => expect(findCalls(/\/api\/editions\/301\/download/)).toHaveLength(1));
    expectNoText("지금은 파일을 받을 수 없어요");
    expect(document.querySelector(".inline-alert-danger")).toBeNull();
  });

  it("확인하는 동안 버튼 문구는 '받는 중…', 끝나면 원래 문구로 돌아온다", async () => {
    const user = userEvent.setup();
    renderWithDownloadCheck({ url: "/api/editions/301/download", raw: "", delay: 50 });
    await findText("추천 판본");
    await clickDownload(user);

    await findText("받는 중…");
    await findText("PDF 받기 · 2.4MB");
  });
});
