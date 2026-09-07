package com.test.test.community;

import com.test.test.common.exception.AccessDeniedException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.community.dto.CommunityCreateDTO;
import com.test.test.community.dto.CommunityDTO;
import com.test.test.community.dto.CommunityUpdateDTO;
import com.test.test.community.comment.repository.CommentRepository;
import com.test.test.community.repository.CommunityRepository;
import com.test.test.file.entity.RefType;
import com.test.test.file.service.FileService;
import com.test.test.jwt.entity.UserEntity;
import com.test.test.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final FileService fileService;

    /**
     * 게시글 작성
     */
    @Transactional
    public Long createCommunity(CommunityCreateDTO createDTO, String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> EntityNotFoundException.of("사용자", username));
        CommunityEntity community = createDTO.toEntity(user);
        CommunityEntity savedCommunity = communityRepository.save(community);

        // 저장 직후 본문 이미지 reconcile (§5-3-1 ①): 본문에 쓰인 임시 이미지를 글ID로 연결
        fileService.reconcileBodyImages(savedCommunity.getId(), createDTO.getContent());

        return savedCommunity.getId();
    }

    /**
     * 게시글 목록 조회 / 검색 (페이징)
     */
    public Page<CommunityDTO> getCommunityList(String searchType, String keyword, Pageable pageable) {
        // Repository에서 직접 DTO로 조회 (카운트 쿼리 최적화 포함)
        return communityRepository.searchCommunity(searchType, keyword, pageable);
    }

    /**
     * 게시글 상세 조회 (조회수 증가)
     * 파일 정보는 클라이언트에서 별도 API로 조회 (/api/files?refId={id}&refType=COMMUNITY)
     */
    @Transactional
    public CommunityDTO getCommunityDetail(Long communityId) {
        CommunityEntity community = communityRepository.findByIdAndIsDeletedFalse(communityId)
            .orElseThrow(() -> EntityNotFoundException.of("게시글", communityId));

        // 목록 조회와 달리 상세 경로는 Repository의 댓글수 세팅을 거치지 않으므로 여기서 실제 값을 채운다.
        // (미세팅 시 DTO 기본값 0이 그대로 노출되던 버그)
        long commentCount = commentRepository.countByCommunityIdAndIsDeletedFalse(communityId);

        // DTO 변환을 조회수 증가보다 먼저 한다.
        // incrementViewCount 는 @Modifying(clearAutomatically = true) 라 영속성 컨텍스트를 비우고,
        // 그러면 community 와 lazy 로딩된 user 프록시가 detach 된다.
        // open-in-view: false 인 운영에서는 그 뒤의 getUser() 가 LazyInitializationException 으로 500 이 됐다.
        CommunityDTO dto = CommunityDTO.from(community);
        dto.setViewCount(community.getViewCount() + 1); // 방금 증가분을 응답에 반영(엔티티는 증가 전 값 보유)
        dto.setCommentCount(commentCount);

        // 조회수는 DB에서 원자적으로 +1 (동시 조회 시 lost update 방지)
        communityRepository.incrementViewCount(communityId);

        return dto;
    }

    /**
     * 게시글 수정
     */
    @Transactional
    public void updateCommunity(Long communityId, CommunityUpdateDTO updateDTO, String username) {
        CommunityEntity community = communityRepository.findByIdAndIsDeletedFalse(communityId)
                .orElseThrow(() -> EntityNotFoundException.of("게시글", communityId));

        if (!community.isWrittenBy(username)) {
            throw AccessDeniedException.forUpdate("게시글");
        }

        community.update(updateDTO.getTitle(), updateDTO.getContent());

        // 수정 직후 본문 이미지 reconcile (§5-3-1 ①): 새로 추가된 이미지 연결 + 빠진 이미지 연결 해제
        fileService.reconcileBodyImages(communityId, updateDTO.getContent());
    }

    /**
     * 게시글 삭제 (게시글 Soft Delete + 연결 파일 Hard Delete — §5-3-1 ②)
     */
    @Transactional
    public void deleteCommunity(Long communityId, String username) {
        CommunityEntity community = communityRepository.findByIdAndIsDeletedFalse(communityId)
            .orElseThrow(() -> EntityNotFoundException.of("게시글", communityId));

        if (!community.isWrittenBy(username)) {
            throw AccessDeniedException.forDelete("게시글");
        }

        // 글에 연결된 파일(본문 IMAGES + 첨부 ATTACHMENT) 메타행 hard delete + 바이트는 커밋 후 삭제(§1)
        fileService.deleteFilesByRef(communityId, RefType.COMMUNITY);

        // 글 자체는 기존대로 soft delete
        community.softDelete();
    }

}
