package com.test.test.file.service;

/**
 * 판본 미리보기 PNG 를 공개해도 되는가 (02 §0-4 표 — 2026-09-08 확정).
 *
 * <p>판단 근거인 저작권 판정은 {@code edition} 에 있고 {@code files} 행에는 없다. 그렇다고 파일 모듈이
 * 판본 모듈을 직접 참조하면 두 모듈이 서로를 가리키므로(판본 저장이 이미 {@code file} 을 쓴다),
 * <b>필요한 질문만</b> 여기에 규격으로 선언하고 답은 {@code sheetmusic.edition} 이 구현한다.
 */
public interface EditionPreviewGate {

    /**
     * 이 판본의 미리보기를 비관리자에게 내줘도 되는가 — {@code korea_copyright = FREE} 일 때만 참.
     * 판본이 없으면(삭제된 판본을 가리키는 파일 행) 거짓이다.
     */
    boolean isPreviewOpenToPublic(long editionId);
}
