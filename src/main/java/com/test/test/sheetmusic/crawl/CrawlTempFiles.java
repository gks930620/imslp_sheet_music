package com.test.test.sheetmusic.crawl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 수집·받아오기가 함께 쓰는 잔일 (03 §3).
 * {@link CrawlWorker} 와 {@link EditionFileFetcher} 가 같은 코드를 각각 들고 있던 것을 모았다.
 */
final class CrawlTempFiles {

    private CrawlTempFiles() {
    }

    /** 임시 디렉터리를 통째로 지운다. 정리 실패는 무시한다 — 남아도 OS 임시 디렉터리다. */
    static void deleteDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 임시 파일 정리 실패는 무시
                }
            });
        } catch (IOException ignored) {
            // 임시 디렉터리 정리 실패는 무시
        }
    }

    /** 실패 사유로 남길 사람 읽는 메시지 — 메시지가 없는 예외는 클래스명으로 대신한다. */
    static String message(Exception e) {
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
    }
}
