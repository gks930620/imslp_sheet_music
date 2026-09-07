package com.test.test.jwt.model;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

public enum OAuthProvider {
    GOOGLE("google") {
        @Override
        public UserDTO toUserEntity(Map<String, Object> attributes, PasswordEncoder passwordEncoder) {
            UserDTO user = new UserDTO();
            user.setProvider(this.getRegistrationId());
            user.setUsername(this.getRegistrationId()+(String) attributes.get("sub"));  //google15432323
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setEmail((String) attributes.get("email"));
            user.setNickname((String) attributes.get("name"));
            user.getRoles().add("USER");
            return user;
        }
    },
    KAKAO("kakao") {
        @Override
        public UserDTO toUserEntity(Map<String, Object> attributes, PasswordEncoder passwordEncoder) {
            // 카카오 데이터 계층 구조 분해 (스코프 미동의/미제공 시 null 방어)
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = kakaoAccount != null
                    ? (Map<String, Object>) kakaoAccount.get("profile")
                    : null;
            Long id = (Long) attributes.get("id");

            UserDTO user = new UserDTO();
            user.setProvider(this.getRegistrationId());
            user.setUsername(this.getRegistrationId() + id);
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setEmail(kakaoAccount != null ? (String) kakaoAccount.get("email") : null);
            user.setNickname(profile != null ? (String) profile.get("nickname") : "카카오사용자"); // profile 안에서 nickname 추출
            user.getRoles().add("USER");
            return user;
        }
    };





    private final String registrationId;
    OAuthProvider(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public static OAuthProvider from(String registrationId) {
        for (OAuthProvider provider : OAuthProvider.values()) {
            if (provider.getRegistrationId().equalsIgnoreCase(registrationId)) { //대소문자구분없이.
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown provider: " + registrationId);
    }
    public abstract UserDTO toUserEntity(Map<String, Object> attributes, PasswordEncoder passwordEncoder);

}