package com.test.test.sheetmusic.seed;

/** 시드 적재 기록의 종류 (01_ERD §3-10). */
public enum SeedType {
    COMPOSER,
    WORK,
    /** {@code collection_guide} 1회 백필 (01_ERD §6, 2026-09-08) — 곡 자연키는 WORK 과 같은 imslp_url 이다. */
    COLLECTION_GUIDE
}
