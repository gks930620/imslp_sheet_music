import { screen } from "@testing-library/react";
import { WorkCard } from "./WorkCard.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { workSummary, workMoonlight, scopeNote } from "../../test/fixtures.js";
import { expectNoText, expectText } from "../../test/text.js";

// 00_공통_레이아웃_토큰.md §3-2 곡 카드, §3-3 난이도 칩, §3-4 상태 뱃지

describe("WorkCard (full)", () => {
  it("카드 전체가 곡 상세 링크다", () => {
    renderWithProviders(<WorkCard work={workMoonlight} />);
    expect(screen.getByRole("link")).toHaveAttribute("href", "/piano/works/21");
  });

  it("한국어 제목·원어 제목·작곡가(원어)·작품번호·쪽수를 보여준다", () => {
    renderWithProviders(<WorkCard work={workMoonlight} />);
    expect(screen.getByText("월광 소나타")).toBeInTheDocument();
    expect(screen.getByText("Piano Sonata No.14, Op.27 No.2")).toBeInTheDocument();
    expectText("베토벤 (Beethoven, Ludwig van)");
    expectText("Op.27 No.2");
    expectText("14쪽");
  });

  it("작품번호가 여러 개면 ' · ' 로 잇는다", () => {
    renderWithProviders(<WorkCard work={workSummary({ catalogNumbers: ["Op.27 No.2", "WoO 59"] })} />);
    expectText("Op.27 No.2 · WoO 59");
  });

  it.each([
    ["BEGINNER", "입문"],
    ["ELEMENTARY", "초급"],
    ["INTERMEDIATE", "중급"],
    ["ADVANCED", "고급"],
    [null, "난이도 미정"],
  ])("난이도 %s → 칩 '%s'", (level, label) => {
    renderWithProviders(<WorkCard work={workSummary({ level })} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it("READY 는 상태 뱃지가 없다", () => {
    renderWithProviders(<WorkCard work={workSummary({ status: "READY" })} />);
    expectNoText("준비 중");
    expectNoText("한국에서 이용 제한");
    expectNoText("저작권 확인 중");
    expectNoText("자유 이용 가능");
  });

  it.each([
    ["PREPARING", "준비 중"],
    ["RESTRICTED", "한국에서 이용 제한"],
    ["UNKNOWN", "저작권 확인 중"],
  ])("상태 %s → 뱃지 '%s'", (status, label) => {
    renderWithProviders(<WorkCard work={workSummary({ status })} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it("별칭으로 걸리면 \"'월광'으로 찾음\" 줄이 보인다", () => {
    renderWithProviders(<WorkCard work={workSummary({ matchedAlias: "월광" })} />);
    expect(screen.getByText("'월광'으로 찾음")).toBeInTheDocument();
  });

  it("별칭 일치가 아니면 그 줄이 없다", () => {
    renderWithProviders(<WorkCard work={workSummary({ matchedAlias: null })} />);
    expectNoText("으로 찾음");
  });

  it("추천 판본이 없으면 쪽수를 표시하지 않는다", () => {
    renderWithProviders(<WorkCard work={workSummary({ pageCount: null, fileSize: null, previewUrl: null })} />);
    expect(document.body.textContent).not.toMatch(/\d+쪽/);
  });

  it("한국어 제목이 없으면 원어 제목을 제목 자리에 쓰고 원어 줄은 생략한다", () => {
    renderWithProviders(<WorkCard work={workSummary({ titleKo: null })} />);
    expect(screen.getAllByText("Piano Sonata No.14, Op.27 No.2")).toHaveLength(1);
  });

  it("작곡가 한글 표기가 없으면 원어만 쓴다", () => {
    renderWithProviders(<WorkCard work={workSummary({ composer: { id: 4, nameKo: null, nameOriginal: "Beethoven, Ludwig van" } })} />);
    expectText("Beethoven, Ludwig van");
    expectNoText("null");
    expectNoText("(Beethoven");
  });

  it("hideComposer 면 작곡가 줄을 빼고 작품번호만 (작곡가 상세 화면)", () => {
    renderWithProviders(<WorkCard work={workMoonlight} hideComposer />);
    expectNoText("베토벤");
    expectText("Op.27 No.2");
  });

  it("미리보기가 없어도 자리는 둔다 (music_note 아이콘), '미리보기 준비 중' 글자는 없다", () => {
    renderWithProviders(<WorkCard work={workSummary({ previewUrl: null })} />);
    expect(screen.getByText("music_note")).toBeInTheDocument();
    expectNoText("미리보기 준비 중");
  });

  it("미리보기가 있으면 이미지를 보여준다", () => {
    renderWithProviders(<WorkCard work={workMoonlight} />);
    expect(screen.getByRole("img")).toHaveAttribute("src", "/uploads/3f2a-c1.png");
  });
});

describe("WorkCard — 받게 되는 악보의 범위 한 줄 (02 §2-2-1 scopeNote, 기획 §2 F2-5 · §11-2)", () => {
  // 묶음("받는 게 찾은 것보다 크다")과 편곡·악장("작거나 다르다")은 같은 자리·같은 한 줄이다.
  it("추천 판본이 편곡이면 '피아노 편곡 악보예요'", () => {
    renderWithProviders(<WorkCard work={workSummary({ scopeNote: scopeNote(["ARRANGEMENT"]) })} />);
    expectText("피아노 편곡 악보예요");
  });

  it("추천 판본이 2악장만이면 '2악장만 들어 있어요'", () => {
    renderWithProviders(<WorkCard work={workSummary({ scopeNote: scopeNote(["MOVEMENT_ONLY"], 2) })} />);
    expectText("2악장만 들어 있어요");
  });

  it("편곡이면서 2악장만이면 한 줄에 이어 쓴다", () => {
    renderWithProviders(
      <WorkCard work={workSummary({ scopeNote: scopeNote(["ARRANGEMENT", "MOVEMENT_ONLY"], 2) })} />,
    );
    expectText("피아노 편곡 악보예요 · 2악장만 들어 있어요");
  });

  it("묶음 악보를 별칭으로 찾았으면 \"'강아지 왈츠'가 들어 있는 악보\" (…으로 찾음 이 아니다)", () => {
    renderWithProviders(
      <WorkCard work={workSummary({ matchedAlias: "강아지 왈츠", scopeNote: scopeNote(["COLLECTION"]) })} />,
    );
    expectText("'강아지 왈츠'가 들어 있는 악보");
    expectNoText("으로 찾음");
  });

  it("scopeNote 가 null 이면 그 줄이 없다 (대부분의 곡)", () => {
    renderWithProviders(<WorkCard work={workSummary({ scopeNote: null })} />);
    expectNoText("편곡 악보예요");
    expectNoText("들어 있어요");
  });
});

// 02 §2-2-1 "COLLECTION 의 두 번째 쓰임", 기획 §12-2.
// 묶음 악보의 난이도·쪽수는 곡 하나가 아니라 묶음 전체의 값이다. 그대로 두면 '달빛'을 찾은 레슨생이
// '고급 · 62쪽'(= 모음곡 4곡)을 보고 못 치는 곡이라 판단하고 창을 닫는다. 실제 달빛 단독은 중급이다.
describe("WorkCard — 묶음 악보의 '(전곡 기준)' (02 §2-2-1, 기획 §12-2)", () => {
  const tagsRow = () => document.querySelector(".work-card-tags");

  it("묶음 악보면 난이도·쪽수 줄에 '(전곡 기준)' 이 붙는다", () => {
    renderWithProviders(
      <WorkCard work={workSummary({ level: "ADVANCED", pageCount: 62, scopeNote: scopeNote(["COLLECTION"]) })} />,
    );
    expect(tagsRow().textContent).toContain("고급");
    expect(tagsRow().textContent).toContain("62쪽");
    expect(tagsRow().textContent).toContain("(전곡 기준)");
  });

  it("난이도와 쪽수가 같은 줄이므로 '(전곡 기준)' 은 한 번만 쓴다", () => {
    renderWithProviders(
      <WorkCard work={workSummary({ level: "ADVANCED", pageCount: 62, scopeNote: scopeNote(["COLLECTION"]) })} />,
    );
    expect(screen.getAllByText("(전곡 기준)")).toHaveLength(1);
  });

  it("쪽수가 없어도 난이도에 걸린다 (추천 판본이 없는 묶음 곡)", () => {
    renderWithProviders(
      <WorkCard
        work={workSummary({
          level: "ADVANCED",
          pageCount: null,
          fileSize: null,
          previewUrl: null,
          scopeNote: scopeNote(["COLLECTION"]),
        })}
      />,
    );
    expect(tagsRow().textContent).toContain("(전곡 기준)");
  });

  it("작곡가 상세(hideAlias)에서도 검색 결과와 같게 붙는다", () => {
    renderWithProviders(
      <WorkCard
        work={workSummary({ level: "ADVANCED", pageCount: 62, scopeNote: scopeNote(["COLLECTION"]) })}
        hideComposer
        hideAlias
      />,
    );
    expect(tagsRow().textContent).toContain("(전곡 기준)");
  });

  it("묶음이 아니면 붙지 않는다 — 편곡·악장만 있는 곡", () => {
    renderWithProviders(
      <WorkCard work={workSummary({ scopeNote: scopeNote(["ARRANGEMENT", "MOVEMENT_ONLY"], 2) })} />,
    );
    expectNoText("(전곡 기준)");
  });

  it("scopeNote 가 null 인 곡(수록곡 안내가 빈 곡)에는 붙이지 않는다 — 시스템은 묶음인지 모른다", () => {
    renderWithProviders(<WorkCard work={workSummary({ scopeNote: null })} />);
    expectNoText("(전곡 기준)");
  });
});

describe("WorkCard (compact — 홈 인기곡·같은 작곡가의 다른 곡)", () => {
  it("순위 · 한국어 제목 · 작곡가 · 난이도 칩 한 줄, 원어 제목 없음", () => {
    renderWithProviders(<WorkCard work={workMoonlight} variant="compact" rank={1} />);
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("월광 소나타")).toBeInTheDocument();
    expectText("베토벤");
    expect(screen.getByText("중급")).toBeInTheDocument();
    expect(screen.queryByText("Piano Sonata No.14, Op.27 No.2")).not.toBeInTheDocument();
    expect(screen.getByRole("link")).toHaveAttribute("href", "/piano/works/21");
  });

  it("상태 뱃지는 compact 에서도 보인다", () => {
    renderWithProviders(<WorkCard work={workSummary({ status: "PREPARING" })} variant="compact" />);
    expect(screen.getByText("준비 중")).toBeInTheDocument();
  });

  it("rank 를 안 주면 순위 숫자가 없다 (같은 작곡가의 다른 곡)", () => {
    renderWithProviders(<WorkCard work={workMoonlight} variant="compact" hideComposer />);
    expectNoText("베토벤");
    expect(screen.queryByText("1")).not.toBeInTheDocument();
  });
});
