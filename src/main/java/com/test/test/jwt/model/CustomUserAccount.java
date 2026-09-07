package com.test.test.jwt.model;

import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
public class CustomUserAccount implements UserDetails, OAuth2User {


    private UserDTO userDTO;
    private final Map<String, Object> attributes; // OAuth2 로그인

    public CustomUserAccount(UserDTO userDTO) {  //일반 사용자로 로그인 한 경우
        this.userDTO = userDTO;
        this.attributes=null;

    }


    public CustomUserAccount(UserDTO userDTO,  Map<String, Object> attributes) {  //Oauth2로 로그인한 경우
        this.userDTO = userDTO;
        this.attributes=attributes;
    }


    public UserDTO getUserDTO() {
        return userDTO;
    }

    // userDetails
    @Override
    public String getUsername() {
        return userDTO.getUsername();
    }

    @Override
    public String getPassword() {
        return userDTO.getPassword();
    }

    //이 메소드는 oauth2User와 UserDetails의 getAuthroties()를 전부 override
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (userDTO.getRoles() == null) {
            return java.util.Collections.emptyList();
        }
        return userDTO.getRoles().stream()
            .map(SimpleGrantedAuthority::new)
            .toList();
    }


    // Oauth2User
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }


    // 카카오로그인이든 폼로그인이든 똑같은 서비스를 제공하기 위해 DB 저장했던 userEntity 사용


    /**
     * oauth2User는  사용자를 getName()으로 식별하지만, 통합User객체를 사용하기때문에
     * userDetails와 맞춰줌.
     */
    @Override
    public String getName() {   //Spring SecurityContext에서 OAuth2 로그인한 사용자의 식별자로 사용됨.
        return getUsername();
    }

    public String getEmail() {
        return userDTO.getEmail();
    }

    public String getNickname() {
        return userDTO.getNickname();
    }

    public String getProvider(){
        return userDTO.getProvider();
    }

}