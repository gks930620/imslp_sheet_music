import { WorkCard } from "./WorkCard.jsx";
import { renderWithProviders } from "../../test/renderWithProviders.jsx";
import { workSummary, scopeNote } from "../../test/fixtures.js";
import { expectNoText, expectText } from "../../test/text.js";

// frontend-dev 가 직접 쓴 보완 테스트 (TDD 내부 구현 세부).
// senior-dev 의 WorkCard.test.jsx 가 고정한 문구 밖의 조합 — 계약(02 §2-2-1)상 가능한데 문구가 정해지지 않은 자리다.
// 여기서 고른 폴백 문구는 designer 확정 전 잠정값이다.

describe("WorkCard 범위 한 줄 — 계약상 가능한 나머지 조합", () => {
  it("MOVEMENT_ONLY 인데 악장 번호를 못 읽었으면(null) '일부 악장만 들어 있어요'", () => {
    renderWithProviders(<WorkCard work={workSummary({ scopeNote: scopeNote(["MOVEMENT_ONLY"], null) })} />);
    expectText("일부 악장만 들어 있어요");
    expectNoText("null악장");
  });

  it("묶음 + 편곡이면 한 줄에 이어 쓴다 (COLLECTION → ARRANGEMENT 순서)", () => {
    renderWithProviders(
      <WorkCard
        work={workSummary({ matchedAlias: "강아지 왈츠", scopeNote: scopeNote(["COLLECTION", "ARRANGEMENT"]) })}
      />,
    );
    expectText("'강아지 왈츠'가 들어 있는 악보 · 피아노 편곡 악보예요");
  });

  it("별칭 일치 + 편곡(묶음 아님)이면 '으로 찾음' 뒤에 이어 쓴다 (같은 자리·같은 줄)", () => {
    renderWithProviders(
      <WorkCard work={workSummary({ matchedAlias: "월광", scopeNote: scopeNote(["ARRANGEMENT"]) })} />,
    );
    expectText("'월광'으로 찾음 · 피아노 편곡 악보예요");
  });

  it("COLLECTION 인데 별칭이 없으면(직접 제목으로 찾음) 묶음 문구를 만들지 않는다", () => {
    renderWithProviders(<WorkCard work={workSummary({ matchedAlias: null, scopeNote: scopeNote(["COLLECTION"]) })} />);
    expectNoText("들어 있는 악보");
    expectNoText("undefined");
  });

  it("compact(홈 인기곡)에는 범위 줄을 넣지 않는다 — 한 줄짜리 자리다", () => {
    renderWithProviders(
      <WorkCard work={workSummary({ scopeNote: scopeNote(["ARRANGEMENT"]) })} variant="compact" rank={1} />,
    );
    expectNoText("피아노 편곡 악보예요");
  });
});
