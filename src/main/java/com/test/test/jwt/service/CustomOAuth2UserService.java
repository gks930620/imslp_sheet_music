package com.test.test.jwt.service;

import com.test.test.jwt.entity.UserEntity;
import com.test.test.jwt.model.CustomUserAccount;
import com.test.test.jwt.model.OAuthProvider;
import com.test.test.jwt.model.UserDTO;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * 브라우저 OAuth2 로그인 시 provider userinfo를 받아 회원을 upsert 하는 서비스.
 * <p>§1 준수: {@link #loadUser} 자체엔 {@code @Transactional} 을 두지 않는다.
 * 외부 HTTP({@code super.loadUser} = userinfo 조회)는 트랜잭션 밖에서 수행하고,
 * 그 결과 attributes로 조립한 사용자만 {@link OAuthUserWriter#upsert} 의 짧은 트랜잭션으로 저장한다.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final PasswordEncoder passwordEncoder;
    private final OAuthUserWriter oAuthUserWriter;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // ① 외부 HTTP: provider userinfo 조회 (트랜잭션 밖)
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 제공자별 엔티티 생성 로직 (ENUM 활용)
        OAuthProvider currentLoginProvider = OAuthProvider.from(registrationId);
        UserDTO oauth2UserDTO = currentLoginProvider.toUserEntity(attributes, passwordEncoder);
        UserEntity candidate = oauth2UserDTO.toEntity();

        // ② DB upsert만 짧은 트랜잭션으로 (신규 저장 또는 이메일·닉네임 갱신)
        UserEntity userEntity = oAuthUserWriter.upsert(candidate);

        UserDTO userDTO = UserDTO.from(userEntity);
        return new CustomUserAccount(userDTO, attributes);
    }
}
