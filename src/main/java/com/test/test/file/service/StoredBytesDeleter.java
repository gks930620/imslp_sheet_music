package com.test.test.file.service;

import com.test.test.file.strategy.FileStorageStrategy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 저장 바이트 삭제를 <b>커밋 이후</b>로 미룬다 (컨벤션 §1: DB 커넥션을 잡은 채 외부 I/O 금지, §5-3-1 ②③).
 *
 * <p>커뮤니티 첨부({@link FileService})와 판본 파일({@code EditionFileService})이 같은 규칙을 쓰므로 한 곳에 모았다.
 * 트랜잭션 밖에서 호출되면 즉시 삭제한다. 삭제 실패는 로그만 남긴다 — 남은 고아 바이트는 정리 배치가 맡는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoredBytesDeleter {

    private final FileStorageStrategy fileStorageStrategy;

    /** @param storedPaths {@code files.file_path} 값들(/uploads/{저장파일명}) */
    public void deleteAfterCommit(List<String> storedPaths) {
        if (storedPaths == null || storedPaths.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(storedPaths);
                }
            });
        } else {
            delete(storedPaths);
        }
    }

    private void delete(List<String> storedPaths) {
        for (String path : storedPaths) {
            try {
                fileStorageStrategy.deleteFile(path);
            } catch (Exception e) {
                log.warn("저장 바이트 삭제 실패 - path: {}, 사유: {}", path, e.getMessage());
            }
        }
    }
}
