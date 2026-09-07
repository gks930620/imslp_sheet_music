package com.test.test.sheetmusic.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDF 매직바이트 검사 (03 §3 파일 검증) — 업로드(02 §5-1)와 수집·받아오기가 같은 규칙을 쓴다.
 * 확장자·Content-Type 은 원격이 준 값이라 믿지 않고, 파일 앞 4바이트가 {@code %PDF} 인지만 본다.
 */
public final class PdfBytes {

    private static final byte[] MAGIC = {'%', 'P', 'D', 'F'};

    private PdfBytes() {
    }

    /** @return 파일 앞 4바이트가 {@code %PDF} 이면 true (읽을 수 없거나 더 짧으면 false) */
    public static boolean isPdf(Path path) throws IOException {
        if (path == null || Files.size(path) < MAGIC.length) {
            return false;
        }
        byte[] head = new byte[MAGIC.length];
        try (InputStream in = Files.newInputStream(path)) {
            if (in.read(head) < MAGIC.length) {
                return false;
            }
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (head[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
