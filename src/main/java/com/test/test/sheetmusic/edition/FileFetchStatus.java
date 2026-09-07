package com.test.test.sheetmusic.edition;

/** IMSLP 파일 받아오기 상태 (01_ERD §3-6). NULL = 요청 없음/완료. */
public enum FileFetchStatus {
    QUEUED, FETCHING, FAILED
}
