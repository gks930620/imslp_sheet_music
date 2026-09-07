package com.test.test.community;

import com.test.test.common.dto.ApiResponse;
import com.test.test.common.dto.PageResponse;
import com.test.test.community.dto.CommunityCreateDTO;
import com.test.test.community.dto.CommunityDTO;
import com.test.test.community.dto.CommunityUpdateDTO;
import com.test.test.jwt.model.CustomUserAccount;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/communities")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CommunityDTO>>> getCommunityList(
            @RequestParam(required = false) String searchType,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {

        Page<CommunityDTO> communities = communityService.getCommunityList(searchType, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("Community list fetched", PageResponse.from(communities)));
    }

    @GetMapping("/{communityId}")
    public ResponseEntity<ApiResponse<CommunityDTO>> getCommunityDetail(@PathVariable Long communityId) {
        CommunityDTO community = communityService.getCommunityDetail(communityId);
        return ResponseEntity.ok(ApiResponse.success("Community fetched", community));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createCommunity(
            @Valid @RequestBody CommunityCreateDTO createDTO,
            @AuthenticationPrincipal CustomUserAccount userAccount) {

        Long communityId = communityService.createCommunity(createDTO, userAccount.getUsername());
        return ResponseEntity.created(URI.create("/api/communities/" + communityId))
                .body(ApiResponse.success("Community created", communityId));
    }

    @PutMapping("/{communityId}")
    public ResponseEntity<ApiResponse<Void>> updateCommunity(
            @PathVariable Long communityId,
            @Valid @RequestBody CommunityUpdateDTO updateDTO,
            @AuthenticationPrincipal CustomUserAccount userAccount) {

        communityService.updateCommunity(communityId, updateDTO, userAccount.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Community updated"));
    }

    @DeleteMapping("/{communityId}")
    public ResponseEntity<ApiResponse<Void>> deleteCommunity(
            @PathVariable Long communityId,
            @AuthenticationPrincipal CustomUserAccount userAccount) {

        communityService.deleteCommunity(communityId, userAccount.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Community deleted"));
    }
}
