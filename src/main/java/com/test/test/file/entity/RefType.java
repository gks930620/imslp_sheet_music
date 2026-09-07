package com.test.test.file.entity;

/**
 * 파일이 어떤 도메인 리소스를 참조하는지 구분하는 타입.
 * (엔티티 밖 최상위 enum — Controller/Service에서 엔티티 의존 없이 사용)
 */
public enum RefType {
    COMMUNITY,  // 커뮤니티 게시글
    USER,       // 사용자 프로필 등
    EDITION     // 악보 판본 (PDF=ATTACHMENT, 미리보기 PNG=THUMBNAIL) — 01_ERD §5
}
