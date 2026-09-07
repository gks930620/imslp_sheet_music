package com.test.test.jwt.service;

import com.test.test.common.exception.DuplicateResourceException;
import com.test.test.jwt.entity.UserEntity;
import com.test.test.jwt.model.JoinDTO;
import com.test.test.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JoinService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void joinProcess(JoinDTO joinDTO) {
        // 중복 체크: 이미 존재하면 예외 발생
        if (userRepository.existsByUsername(joinDTO.getUsername())) {
            throw new DuplicateResourceException("이미 사용 중인 아이디입니다: " + joinDTO.getUsername());
        }

        // 이메일 중복 체크 (선택)
        if (joinDTO.getEmail() != null && userRepository.existsByEmail(joinDTO.getEmail())) {
            throw new DuplicateResourceException("이미 사용 중인 이메일입니다: " + joinDTO.getEmail());
        }

        UserEntity user = joinDTO.toEntity(passwordEncoder.encode(joinDTO.getPassword()));
        userRepository.save(user);
    }
}