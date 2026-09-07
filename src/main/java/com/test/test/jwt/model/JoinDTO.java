package com.test.test.jwt.model;

import com.test.test.jwt.entity.UserEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class JoinDTO {

    @NotBlank(message = "아이디는 필수입니다")
    @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다")
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 4, max = 100, message = "비밀번호는 4자 이상이어야 합니다")
    private String password;

    @Email(message = "이메일 형식이 올바르지 않습니다")
    private String email;

    @NotBlank(message = "닉네임은 필수입니다")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다")
    private String nickname;

    /**
     * 회원가입 요청 → UserEntity 변환.
     * 비밀번호 인코딩은 서비스 책임이므로 인코딩된 값을 인자로 받는다.
     */
    public UserEntity toEntity(String encodedPassword) {
        UserEntity user = UserEntity.builder()
                .username(username)
                .password(encodedPassword)
                .email(email)
                .nickname(nickname)
                .provider("LOCAL")
                .build();
        user.getRoles().add("USER");
        return user;
    }
}