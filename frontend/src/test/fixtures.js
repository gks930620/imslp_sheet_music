/**
 * 02_API_명세서.md 의 JSON 예시를 그대로 옮긴 픽스처.
 * 화면 테스트는 이 값을 fetch 목 응답으로 쓴다 — 명세서와 어긋나면 여기부터 고친다(senior-dev).
 */

export const composerBeethoven = { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van" };
export const composerChopin = { id: 9, nameKo: "쇼팽", nameOriginal: "Chopin, Frédéric" };

/** §2-2 WorkSummaryDTO */
export const workMoonlight = {
  id: 21,
  titleKo: "월광 소나타",
  titleOriginal: "Piano Sonata No.14, Op.27 No.2",
  composer: composerBeethoven,
  catalogNumbers: ["Op.27 No.2"],
  level: "INTERMEDIATE",
  status: "READY",
  pageCount: 14,
  fileSize: 1059957,
  previewUrl: "/uploads/3f2a-c1.png",
  matchedAlias: null,
  scopeNote: null,
};

export const workElise = {
  id: 22,
  titleKo: "엘리제를 위하여",
  titleOriginal: "Für Elise, WoO 59",
  composer: composerBeethoven,
  catalogNumbers: ["WoO 59"],
  level: "ELEMENTARY",
  status: "READY",
  pageCount: 4,
  fileSize: 655360,
  previewUrl: null,
  matchedAlias: null,
  scopeNote: null,
};

export const workNocturne2 = {
  id: 23,
  titleKo: "녹턴 2번",
  titleOriginal: "Nocturne in E-flat major, Op.9 No.2",
  composer: composerChopin,
  catalogNumbers: ["Op.9 No.2"],
  level: "INTERMEDIATE",
  status: "PREPARING",
  pageCount: null,
  fileSize: null,
  previewUrl: null,
  matchedAlias: "녹턴",
  scopeNote: null,
};

/** §2-2-1 scopeNote — 할 말이 없으면 필드가 통째로 null 이고, codes 는 비어 있지 않다 */
export function scopeNote(codes, movementNumber = null) {
  return { codes, movementNumber };
}

export function workSummary(overrides = {}) {
  return { ...workMoonlight, ...overrides };
}

export function popularWorks(count = 10) {
  return Array.from({ length: count }, (_, i) =>
    workSummary({ id: 100 + i, titleKo: `인기곡 ${i + 1}`, titleOriginal: `Popular ${i + 1}`, status: "READY" }),
  );
}

/** §3-6 ComposerCardDTO[] */
export function featuredComposers(count = 8) {
  const names = ["쇼팽", "베토벤", "바흐", "모차르트", "슈베르트", "드뷔시", "리스트", "슈만", "브람스", "그리그"];
  return Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    nameKo: names[i],
    nameOriginal: `Composer ${i + 1}`,
    workCount: 24 - i,
  }));
}

/** §3-1 검색 응답 data */
export function searchResponse({
  q = "녹턴",
  works = [workNocturne2],
  page = 0,
  totalElements = works.length,
  size = 20,
  unfilteredTotal = totalElements,
  composers = [],
  composerMatchCount = composers.length,
} = {}) {
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  return {
    q,
    composers,
    composerMatchCount,
    unfilteredTotal,
    works: {
      content: works,
      page,
      size,
      totalElements,
      totalPages,
      first: page === 0,
      last: page >= totalPages - 1,
    },
  };
}

/** §2-3 EditionDTO */
export const editionRecommended = {
  id: 301,
  kind: "COMPLETE_SCORE",
  scope: "COMPLETE",
  movementNumber: null,
  sectionLabel: null,
  pageCount: 12,
  fileSize: 2516582, // 2.4MB
  hasFile: true,
  previewUrl: "/uploads/3f2a-c1.png",
  publisher: "Breitkopf & Härtel",
  publishYear: 1862,
  plateNumber: "B.&H. 1234",
  editor: "Sigmund Lebert",
  arranger: null,
  scanner: "Unknown",
  koreaCopyright: "FREE",
  imslpCopyrightText: "Public Domain",
  ccLicenseName: null,
  ccAttribution: null,
  imslpFileUrl: "https://imslp.org/wiki/Special:ImagefromIndex/00014",
  downloadable: true,
  largeFile: false,
  downloadUrl: "/api/editions/301/download",
};

export const editionOtherFree = {
  ...editionRecommended,
  id: 302,
  scope: "MOVEMENT",
  movementNumber: 2,
  pageCount: 8,
  fileSize: 1153434, // 1.1MB
  previewUrl: null,
  publisher: "Peters",
  publishYear: 1901,
  editor: "Max Pauer",
  downloadUrl: "/api/editions/302/download",
};

export const editionOtherRestricted = {
  ...editionRecommended,
  id: 303,
  pageCount: 12,
  fileSize: 3145728,
  previewUrl: null,
  publisher: "Henle",
  publishYear: 1976,
  editor: "Bertha Antonia Wallner",
  koreaCopyright: "RESTRICTED",
  imslpCopyrightText: "Creative Commons Attribution Non-commercial 3.0",
  downloadable: false,
  downloadUrl: null,
};

/**
 * 파일 없는 판본. 2026-09-08 계약 통일(§3-3, 기획 §F3-4) 이후 공개 곡 상세의 otherEditions 에는
 * <b>들어오지 않는다</b> — 파일 없는 판본은 줄이 아니라 imslpOnlyCount 숫자 한 줄로만 안내된다.
 * 관리 화면(§4-7)은 그대로 전부 보므로 그쪽 픽스처로만 쓴다.
 */
export const editionOtherNoFile = {
  ...editionRecommended,
  id: 304,
  pageCount: null,
  fileSize: null,
  hasFile: false,
  previewUrl: null,
  publisher: "Schirmer",
  publishYear: 1901,
  editor: null,
  koreaCopyright: "UNKNOWN",
  downloadable: false,
  downloadUrl: null,
};

export function edition(overrides = {}) {
  return { ...editionRecommended, ...overrides };
}

/** §3-3 WorkDetailDTO */
export function workDetail(overrides = {}) {
  return {
    id: 21,
    titleKo: "월광 소나타",
    titleOriginal: "Piano Sonata No.14, Op.27 No.2",
    composer: composerBeethoven,
    catalogNumbers: ["Op.27 No.2"],
    level: "INTERMEDIATE",
    status: "READY",
    aliases: ["월광", "월광 소나타", "Moonlight Sonata"],
    compositionYear: "1801",
    musicalKey: "C-sharp minor",
    movements: "3 movements",
    movementPageGuide: "1악장 1쪽 · 2악장 6쪽 · 3악장 9쪽",
    // §3-3 collectionGuide — 기본값은 null(= 묶음이 아님, 화면은 줄을 만들지 않는다).
    // 값을 넣으면 §2-2-1 상 이 곡의 scopeNote.codes 에 COLLECTION 이 있어야 하는데, 같은 곡(id 21)의
    // 요약 픽스처 workMoonlight 는 scopeNote: null 이다 — 기본값을 채우면 두 픽스처가 계약상 서로 어긋난다.
    // 묶음 곡을 시험할 때는 collectionGuide 와 scopeNote(scopeNote(["COLLECTION"])) 를 함께 넘긴다.
    collectionGuide: null,
    imslpUrl: "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)",
    composerImslpUrl: "https://imslp.org/wiki/Category:Beethoven,_Ludwig_van",
    recommendedEdition: editionRecommended,
    otherEditions: [],
    imslpOnlyCount: 0,
    // §3-3-2 imslpCandidateEdition — recommendedEdition 이 있으면 **항상 null** 이다(두 자리에서 같은
    // 링크를 만들 수 있으면 화면마다 어느 쪽을 쓸지 갈린다). 준비 중 곡을 만들려면
    // { status: "PREPARING", recommendedEdition: null, imslpCandidateEdition: edition({ hasFile: false }) }.
    imslpCandidateEdition: null,
    downloadableOtherCount: 0,
    sameComposerWorks: [workElise],
    ...overrides,
  };
}

/** §3-5 작곡가 전체 목록 data */
export const composerListResponse = {
  total: 5,
  composers: [
    { id: 1, nameKo: "그리그", nameOriginal: "Grieg, Edvard", birthYear: 1843, deathYear: 1907, workCount: 12 },
    { id: 2, nameKo: "드뷔시", nameOriginal: "Debussy, Claude", birthYear: 1862, deathYear: 1918, workCount: 9 },
    { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van", birthYear: 1770, deathYear: 1827, workCount: 18 },
    { id: 9, nameKo: "쇼팽", nameOriginal: "Chopin, Frédéric", birthYear: 1810, deathYear: null, workCount: 24 },
    { id: 11, nameKo: null, nameOriginal: "Satie, Erik", birthYear: 1866, deathYear: 1925, workCount: 3 },
  ],
};

/** §3-7 작곡가 상세 data */
export function composerDetail(overrides = {}) {
  return {
    id: 9,
    nameKo: "쇼팽",
    nameOriginal: "Chopin, Frédéric",
    birthYear: 1810,
    deathYear: 1849,
    nationality: "폴란드",
    aliases: ["쇼팡"],
    imslpUrl: "https://imslp.org/wiki/Category:Chopin,_Frédéric",
    workCount: 24,
    ...overrides,
  };
}

/** §3-8 작곡가의 곡 data */
export function composerWorksResponse({ works = [workNocturne2], totalElements = works.length, unfilteredTotal = 24, page = 0 } = {}) {
  const size = 20;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  return {
    unfilteredTotal,
    works: { content: works, page, size, totalElements, totalPages, first: page === 0, last: page >= totalPages - 1 },
  };
}

/** §6-3 CrawlJobDTO */
export function crawlJob(overrides = {}) {
  return {
    id: 12,
    status: "RUNNING",
    stopRequested: false,
    stoppedByRestart: false,
    fetchFiles: true,
    totalCount: 50,
    processedCount: 12,
    successCount: 11,
    failCount: 1,
    skipCount: 0,
    hiddenCount: 0,
    pendingCount: 38,
    currentItem: { seq: 13, url: "https://imslp.org/wiki/Nocturnes,_Op.9_(Chopin,_Frédéric)", title: "Nocturne in E-flat major, Op.9 No.2" },
    currentStage: "DOWNLOADING_FILE",
    currentFileIndex: 2,
    currentFileTotal: 2,
    pausedUntil: null,
    failureReason: null,
    retryOfJobId: null,
    createdBy: "gks930620",
    createdAt: "2026-09-06T05:02:00Z",
    startedAt: "2026-09-06T05:02:01Z",
    finishedAt: null,
    elapsedSeconds: 903,
    estimatedRemainingSeconds: 2700,
    ...overrides,
  };
}

export function completedCrawlJob(overrides = {}) {
  return crawlJob({
    status: "COMPLETED",
    processedCount: 50,
    successCount: 46,
    failCount: 3,
    skipCount: 1,
    pendingCount: 0,
    currentItem: null,
    currentStage: null,
    currentFileIndex: null,
    currentFileTotal: null,
    finishedAt: "2026-09-06T06:05:00Z",
    elapsedSeconds: 3780,
    estimatedRemainingSeconds: null,
    ...overrides,
  });
}

/** §6-6 CrawlItemDTO */
export function crawlItem(overrides = {}) {
  return {
    id: 1201,
    seq: 1,
    url: "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)",
    mode: "CREATE",
    status: "SUCCESS",
    failReason: null,
    message: "판본 14개, 파일 2개 받음",
    workId: 77,
    workTitle: "Piano Sonata No.14, Op.27 No.2",
    editionCount: 14,
    fileCount: 2,
    startedAt: "2026-09-06T05:02:01Z",
    finishedAt: "2026-09-06T05:03:10Z",
    ...overrides,
  };
}

export const crawlItemsRunning = [
  crawlItem(),
  crawlItem({ id: 1202, seq: 2, url: "https://imslp.org/wiki/Fuer_Elise,_WoO_59_(Beethoven,_Ludwig_van)", status: "FAILED", failReason: "PAGE_NOT_FOUND", message: "IMSLP에 그 페이지가 없어요", workId: null, workTitle: null, editionCount: 0, fileCount: 0 }),
  crawlItem({ id: 1203, seq: 3, url: "https://imslp.org/wiki/Nocturnes,_Op.9_(Chopin,_Frédéric)", status: "PROCESSING", message: null, workId: null, workTitle: null, finishedAt: null }),
  crawlItem({ id: 1204, seq: 4, url: "https://imslp.org/wiki/Etudes,_Op.10_(Chopin,_Frédéric)", status: "PENDING", message: null, workId: null, workTitle: null, startedAt: null, finishedAt: null }),
];

export const crawlItemsCompleted = [
  crawlItem(),
  crawlItem({ id: 1202, seq: 2, url: "https://imslp.org/wiki/Fuer_Elise,_WoO_59_(Beethoven,_Ludwig_van)", status: "FAILED", failReason: "PAGE_NOT_FOUND", message: "IMSLP에 그 페이지가 없어요", workId: null, workTitle: null, editionCount: 0, fileCount: 0 }),
  crawlItem({ id: 1217, seq: 17, url: "https://imslp.org/wiki/Etudes,_Op.10_(Chopin,_Frédéric)", status: "FAILED", failReason: "FILE_DOWNLOAD_FAILED", message: "파일을 받다가 끊겼어요", workId: 78, workTitle: "Etudes, Op.10", editionCount: 6, fileCount: 1 }),
  crawlItem({ id: 1233, seq: 33, url: "https://imslp.org/wiki/Symphony_No.5,_Op.67_(Beethoven,_Ludwig_van)", status: "HIDDEN", failReason: null, message: "피아노 독주곡이 아닌 것 같아요", workId: 79, workTitle: "Symphony No.5, Op.67", editionCount: 3, fileCount: 0 }),
  crawlItem({ id: 1240, seq: 40, url: "https://imslp.org/wiki/Nocturnes,_Op.9_(Chopin,_Frédéric)", status: "SKIPPED", mode: "SKIP", message: "이미 있음 — 건너뜀", workId: 23, workTitle: "Nocturnes, Op.9", editionCount: 0, fileCount: 0 }),
];

/** §6-1 주소 확인 응답 data */
export const crawlCheckResponse = {
  items: [
    { seq: 1, inputUrl: "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)", canonicalUrl: "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)", verdict: "NEW", existingWorkId: null, duplicateOfSeq: null },
    { seq: 2, inputUrl: "https://imslp.org/wiki/Fuer_Elise,_WoO_59_(Beethoven,_Ludwig_van)", canonicalUrl: "https://imslp.org/wiki/Fuer_Elise,_WoO_59_(Beethoven,_Ludwig_van)", verdict: "ATTACH", existingWorkId: 22, duplicateOfSeq: null },
    { seq: 3, inputUrl: "https://imslp.org/wiki/Nocturnes,_Op.9_(Chopin,_Frédéric)", canonicalUrl: "https://imslp.org/wiki/Nocturnes,_Op.9_(Chopin,_Frédéric)", verdict: "EXISTS", existingWorkId: 23, duplicateOfSeq: null },
    { seq: 4, inputUrl: "https://example.com/foo", canonicalUrl: null, verdict: "INVALID_URL", existingWorkId: null, duplicateOfSeq: null },
    { seq: 5, inputUrl: "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)", canonicalUrl: "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)", verdict: "DUPLICATE", existingWorkId: null, duplicateOfSeq: 1 },
  ],
  summary: { newCount: 30, attachCount: 18, existsCount: 2, invalidCount: 1, duplicateCount: 1, estimatedSeconds: 4200 },
};

/** §4-1 관리 홈 data */
export function dashboard(overrides = {}) {
  return {
    totalWorks: 312,
    readyWorks: 241,
    preparingWorks: 38,
    needsWorkWorks: 57,
    unknownCopyrightEditions: 19,
    monthlyDownloads: 1204,
    latestJob: null,
    activeJob: null,
    ...overrides,
  };
}

/** §4-7 AdminEditionDTO (EditionDTO + 관리 필드) */
export function adminEdition(overrides = {}) {
  return {
    ...editionRecommended,
    imslpFileId: 14,
    imslpOriginalFileName: "PMLP01458-Beethoven_Op27_No2.pdf",
    imslpDescription: null,
    imslpLicenseCode: "PD",
    imslpDownloadCount: 1204,
    pdfFileId: 501,
    previewFileId: 502,
    copyrightNote: "작곡가 1827 사망, 편집자 Lebert 1884 사망 → 사후 70년 경과",
    copyrightJudgedAt: "2026-09-06T05:02:00Z",
    copyrightJudgedBy: "gks930620",
    fileFetchStatus: null,
    fileFetchError: null,
    fileFetchedAt: null,
    downloadCount: 312,
    isRecommended: true,
    isCandidate: false,
    createdAt: "2026-09-06T05:02:00Z",
    updatedAt: "2026-09-06T05:02:00Z",
    ...overrides,
  };
}

/** §5-1 업로드 응답 data */
export const editionFileUploadResponse = {
  fileId: 501,
  previewFileId: 502,
  fileName: "beethoven_op27-2.pdf",
  fileSize: 1059957,
  pageCount: 14,
  previewUrl: "/uploads/preview-501.png",
};

/** 공통 PageResponse 포장 (§0-1) */
export function pageResponse(content, { page = 0, size = 20, totalElements = content.length } = {}) {
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  return { content, page, size, totalElements, totalPages, first: page === 0, last: page >= totalPages - 1 };
}

/** §4-2 AdminComposerDTO */
export function adminComposer(overrides = {}) {
  return {
    id: 4,
    nameKo: "베토벤",
    nameOriginal: "Beethoven, Ludwig van",
    birthYear: 1770,
    deathYear: 1827,
    workCount: 18,
    updatedAt: "2026-09-05T04:00:00Z",
    ...overrides,
  };
}

export const adminComposers = [
  adminComposer(),
  adminComposer({ id: 9, nameKo: "쇼팽", nameOriginal: "Chopin, Frédéric", birthYear: 1810, deathYear: 1849, workCount: 24 }),
  adminComposer({ id: 11, nameKo: null, nameOriginal: "Satie, Erik", birthYear: 1866, deathYear: 1925, workCount: 3 }),
];

/** §4-3 AdminComposerDetailDTO */
export function adminComposerDetail(overrides = {}) {
  return {
    id: 4,
    nameKo: "베토벤",
    nameOriginal: "Beethoven, Ludwig van",
    aliases: ["루트비히 판 베토벤"],
    birthYear: 1770,
    deathYear: 1827,
    nationality: "독일",
    imslpUrl: "https://imslp.org/wiki/Category:Beethoven,_Ludwig_van",
    workCount: 0,
    createdAt: "2026-09-05T04:00:00Z",
    updatedAt: "2026-09-05T04:00:00Z",
    ...overrides,
  };
}

/** §4-6 AdminWorkSummaryDTO */
export function adminWorkSummary(overrides = {}) {
  return {
    id: 21,
    titleKo: "월광 소나타",
    titleOriginal: "Piano Sonata No.14, Op.27 No.2",
    composer: composerBeethoven,
    catalogNumbers: ["Op.27 No.2"],
    level: "INTERMEDIATE",
    editionCount: 14,
    hasRecommended: true,
    // §4-6 · §5-6-1 — 같은 곡(id 21)의 상세 픽스처 adminWorkDetail 과 같은 값이어야 한다.
    // hasRecommended: false 로 덮을 때는 이 값도 반드시 false 다("검수할 대상이 없다").
    recommendationReviewed: false,
    status: "READY",
    needsWork: false,
    hidden: false,
    updatedAt: "2026-09-05T04:00:00Z",
    ...overrides,
  };
}

export const adminWorkNeedsWork = adminWorkSummary({
  id: 23,
  titleKo: null,
  titleOriginal: "Nocturne in E-flat major, Op.9 No.2",
  composer: composerChopin,
  catalogNumbers: ["Op.9 No.2"],
  level: null,
  editionCount: 6,
  hasRecommended: false,
  recommendationReviewed: false,
  status: "PREPARING",
  needsWork: true,
  updatedAt: "2026-09-06T04:00:00Z",
});

export const adminWorkHidden = adminWorkSummary({
  id: 24,
  titleKo: "교향곡 5번",
  titleOriginal: "Symphony No.5, Op.67",
  editionCount: 3,
  hasRecommended: false,
  recommendationReviewed: false,
  status: "PREPARING",
  needsWork: true,
  hidden: true,
});

/** §4-6 응답 data */
export function adminWorksResponse({ works = [adminWorkSummary(), adminWorkNeedsWork], unfilteredTotal = 312, page = 0, totalElements = works.length } = {}) {
  return { unfilteredTotal, works: pageResponse(works, { page, totalElements }) };
}

/** §4-7 AdminEditionDTO 변형 3종 (추천 / 추천 후보 / 파일 없음) */
export const adminEditionRecommended = adminEdition();

export const adminEditionCandidate = adminEdition({
  id: 302,
  isRecommended: false,
  isCandidate: true,
  pageCount: 11,
  fileSize: 3251200, // 3.1MB
  previewUrl: null,
  previewFileId: null,
  publisher: "Peters",
  publishYear: 1880,
  editor: "Louis Köhler",
  koreaCopyright: "UNKNOWN",
  copyrightNote: null,
  copyrightJudgedAt: null,
  copyrightJudgedBy: null,
  downloadable: false,
  downloadUrl: null,
  downloadCount: 0,
  imslpDownloadCount: 1204,
});

export const adminEditionNoFile = adminEdition({
  id: 304,
  isRecommended: false,
  isCandidate: false,
  scope: "MOVEMENT",
  movementNumber: 2,
  hasFile: false,
  pageCount: null,
  fileSize: null,
  previewUrl: null,
  previewFileId: null,
  pdfFileId: null,
  publisher: "Schirmer",
  publishYear: 1901,
  editor: null,
  koreaCopyright: "UNKNOWN",
  copyrightNote: null,
  copyrightJudgedAt: null,
  copyrightJudgedBy: null,
  downloadable: false,
  downloadUrl: null,
  downloadCount: 0,
  imslpDownloadCount: 0,
  imslpFileId: 77,
  imslpLicenseCode: "PD",
});

/** §4-7 AdminWorkDetailDTO */
export function adminWorkDetail(overrides = {}) {
  return {
    id: 21,
    titleKo: "월광 소나타",
    titleOriginal: "Piano Sonata No.14, Op.27 No.2",
    composer: { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van", deathYear: 1827, nameKoMissing: false },
    catalogNumbers: ["Op.27 No.2"],
    aliases: ["월광", "Moonlight Sonata"],
    level: "INTERMEDIATE",
    compositionYear: "1801",
    musicalKey: "C-sharp minor",
    movements: "3 movements",
    movementPageGuide: null,
    collectionGuide: "이 악보에는 3개 악장이 들어 있어요 — 흔히 아는 느린 선율은 1악장이에요",
    imslpUrl: "https://imslp.org/wiki/Piano_Sonata_No.14,_Op.27_No.2_(Beethoven,_Ludwig_van)",
    hidden: false,
    hiddenReason: null,
    status: "READY",
    needsWork: false,
    missing: [],
    recommendedEditionId: 301,
    candidateEditionId: null,
    // §4-7 · §5-6-1 — 추천이 바뀌거나 해제되면 서버가 false 로 되돌린다. 요약 픽스처와 같은 값.
    recommendationReviewed: false,
    downloadCount: 312,
    hasDownloadHistory: true,
    editions: [adminEditionRecommended],
    createdAt: "2026-09-06T05:02:00Z",
    updatedAt: "2026-09-06T05:02:00Z",
    ...overrides,
  };
}

/** §5-8 PendingCopyrightDTO */
export function pendingCopyright(overrides = {}) {
  return {
    editionId: 305,
    work: { id: 23, titleKo: null, titleOriginal: "Nocturnes, Op.9" },
    composer: { id: 9, nameKo: "쇼팽", nameOriginal: "Chopin, Frédéric", deathYear: 1849 },
    kind: "COMPLETE_SCORE",
    scope: "COMPLETE",
    movementNumber: null,
    editor: "Ignacy Paderewski",
    arranger: null,
    publisher: "Warsaw: Instytut Fryderyka Chopina, 1949.",
    publishYear: 1949,
    imslpCopyrightText: "Public Domain",
    imslpFileUrl: "https://imslp.org/wiki/Special:ImagefromIndex/00014",
    hasFile: true,
    // §5-8 autoJudgeSkipReason — "지금 자동 판정(§5-11)을 돌리면 이 판본이 왜 안 열리는지".
    // 이 행의 값이 §A-1 규칙 8 인 이유: PD 표기(1 통과) + 쇼팽 몰년 1849(2·3 통과) + 편집자 표기 있음(5 아님)
    // + 1949년 출판이라 120년 미경과(7 아님) → EDITOR_UNVERIFIABLE. 명세서 §5-8 예시와 같은 행이다.
    // 자동 판정으로 FREE 가 될 수 있는 판본이면 null 이고, 그때 화면은 아무 문구도 만들지 않는다.
    autoJudgeSkipReason: "EDITOR_UNVERIFIABLE",
    ...overrides,
  };
}

// 몰년이 없는 데다 표기가 NC(재배포 불가)다. §A-1 은 위에서부터 먼저 걸리는 곳에서 끝나므로
// 사유는 COMPOSER_DEATH_YEAR_UNKNOWN(규칙 2)이 아니라 규칙 1 이다 — 사유는 "그 판본의 모든 문제"가
// 아니라 "제일 먼저 막은 하나"다.
export const pendingNoDeathYear = pendingCopyright({
  editionId: 306,
  work: { id: 21, titleKo: "월광 소나타", titleOriginal: "Piano Sonata No.14, Op.27 No.2" },
  composer: { id: 4, nameKo: "베토벤", nameOriginal: "Beethoven, Ludwig van", deathYear: null },
  editor: null,
  imslpCopyrightText: "Creative Commons Attribution Non-commercial 3.0",
  autoJudgeSkipReason: "LICENSE_NOT_REDISTRIBUTABLE",
});

/**
 * §5-8 응답 data.
 *
 * `autoJudged`(§5-8-1)는 **항상 있는 객체**다 — 되돌리기 버튼의 노출 조건이라 키가 없으면 화면이 분기를 못 한다.
 * 기본값은 "되돌릴 것이 없음"(0/0): 대부분의 테스트는 자동 판정을 돌리지 않은 상태를 가정한다.
 */
export function pendingCopyrightResponse({
  editions = [pendingCopyright(), pendingNoDeathYear],
  unfilteredTotal = 19,
  autoJudged = { revertibleEditions: 0, revertibleRecommendedWorks: 0 },
  page = 0,
  totalElements = editions.length,
} = {}) {
  return { unfilteredTotal, autoJudged, editions: pageResponse(editions, { page, totalElements }) };
}
