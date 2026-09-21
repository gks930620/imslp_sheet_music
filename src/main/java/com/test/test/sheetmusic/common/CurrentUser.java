package com.test.test.sheetmusic.common;

import com.test.test.common.exception.AccessDeniedException;
import com.test.test.jwt.model.CustomUserAccount;

/**
 * 요청 주체의 계정 id — <b>토큰(SecurityContext)에서만</b> 꺼낸다 (02 §10-0, 컨벤션 §4-1).
 *
 * <p>주소·쿼리·본문 어디에서도 사용자 id 를 받지 않기 때문에 "남의 것 보기" 는 시도할 주소가 없다.
 * 비로그인 요청에서는 {@code @AuthenticationPrincipal} 이 null 이므로(익명 주체는 타입이 다르다)
 * 공개 API 는 {@link #idOrNull}, 로그인 전용 API 는 {@link #id} 를 쓴다.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** 공개 API 용 — 비로그인이면 null(오류가 아니다. 예: 곡 상세의 {@code favorited}). */
    public static Long idOrNull(CustomUserAccount account) {
        return account == null || account.getUserDTO() == null ? null : account.getUserDTO().getId();
    }

    /**
     * 로그인 전용 API 용. 여기까지 왔는데 주체가 없으면 보안 설정이 열린 것이므로
     * 조용히 빈 결과를 주지 않고 막는다({@code anyRequest().authenticated()} 가 먼저 401 을 준다).
     */
    public static Long id(CustomUserAccount account) {
        Long id = idOrNull(account);
        if (id == null) {
            throw new AccessDeniedException("로그인이 필요해요");
        }
        return id;
    }
}
