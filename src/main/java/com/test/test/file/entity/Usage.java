package com.test.test.file.entity;

/**
 * 파일의 용도 구분.
 * (엔티티 밖 최상위 enum — Controller/Service에서 엔티티 의존 없이 사용)
 */
public enum Usage {
    THUMBNAIL,   // 썸네일/대표 이미지
    IMAGES,      // 본문 내용 이미지
    ATTACHMENT   // 첨부 파일
}
