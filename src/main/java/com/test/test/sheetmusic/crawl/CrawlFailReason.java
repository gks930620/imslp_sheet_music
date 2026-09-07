package com.test.test.sheetmusic.crawl;

/** 수집 항목 실패 사유 (01_ERD §3-9). 문구는 02 §6-6 고정. */
public enum CrawlFailReason {
    PAGE_NOT_FOUND("IMSLP에 그 페이지가 없어요"),
    NO_PDF_EDITION("작품 페이지에 PDF 판본이 없어요"),
    FILE_DOWNLOAD_FAILED("파일을 받다가 끊겼어요"),
    IMSLP_UNAVAILABLE("IMSLP가 응답하지 않아요"),
    INTERRUPTED("중단됨 — 서비스가 재시작됐어요"),
    /**
     * 메타 읽기 단계의 예상 밖 오류 = 우리 쪽 버그·파싱 사고 (02 §6-10, 2026-09-07 추가).
     * IMSLP 사정이 아니므로 연속 무응답으로 세지 않는다(PAUSED 로 가지 않는다).
     */
    INTERNAL_ERROR("처리 중 오류가 났어요");

    private final String message;

    CrawlFailReason(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
