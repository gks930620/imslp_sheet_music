package com.test.test.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration_access}")
    private long expirationAccess;


    @Value("${jwt.expiration_refresh}")
    private long expirationRefresh;

    private SecretKey getSigningKey() {
        // charset을 UTF-8로 고정 — 플랫폼 기본 charset에 따라 서명키가 달라지는 것 방지
        // (예: Windows 개발 ↔ Linux 컨테이너 배포 조합에서 동일 시크릿이라도 키가 달라질 수 있음)
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }




    // Access Token 생성
    public String   createAccessToken(String username) {
        return Jwts.builder()
            .subject(username) // ✅ setSubject() -> subject()
            .claim("token_type", "access")   //타입구분을 위해 추가.
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationAccess))
            .signWith(getSigningKey()) // ✅ SignatureAlgorithm.HS256 대신 Jwts.SIG.HS256 사용
            .compact();
    }

    public String   createRefreshToken(String username) {
        return Jwts.builder()
            .subject(username)
            .issuedAt(new Date())
            .claim("token_type" ,"refresh")
            .expiration(new Date(System.currentTimeMillis() + expirationRefresh))
            .signWith(getSigningKey()) // ✅ SignatureAlgorithm.HS256 대신 Jwts.SIG.HS256 사용
            .compact();
    }



    public String getUsername(String token) {
        return extractUsername(token);
    }

    //토큰에서 username 추출.  뭐 subject에 uuid를 넣기도하지만.. 여기서는 subject에 username세팅했었음.
    public String extractUsername(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())  // 0.12.3버전에서는 verifyWith에 Key말고 SecretKey가 와야한다.
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }


    //토큰에서 인증여부 확인.  코드상 문제가없다면 보통 만료됐을 때 false
    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())  // ✅ 서명 검증
                .build()
                .parseSignedClaims(token)    // ✅ JWT 파싱
                .getPayload();               // ✅ claims(토큰 정보) 추출
            //  토큰 만료 확인
            Date expiration = claims.getExpiration();
            return expiration.after(new Date()); // 현재 시간보다 만료 시간이 뒤에 있어야 유효

        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }


    // JWT 검증 후 username 추출 (WebSocket 인터셉터 등에서 사용)
    public String validateAndExtractUsername(String token) {
        try {
            if (!validateToken(token)) return null;
            return extractUsername(token);
        } catch (Exception e) {
            return null;
        }
    }

    // 토큰에서 token_type 클레임 추출
    public  String getTokenType(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return claims.get("token_type", String.class); // token_type 값 반환
        } catch (ExpiredJwtException e) {   //만료되었어도 token type은 return
            Claims claims = e.getClaims();
            return claims != null ? claims.get("token_type", String.class) : null;
        } catch (JwtException | IllegalArgumentException e) {  // 변조, 잘못된 형식 등
            return null;
        }
    }
}

//jjwt 버전에 따라 구현방식이 다르다. 현재는 0.12.3 버전.