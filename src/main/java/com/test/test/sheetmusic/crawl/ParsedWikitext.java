package com.test.test.sheetmusic.crawl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;

/**
 * 위키텍스트 파싱 결과 (00 조사 §3-3).
 * HTML 이 마스터이고 이 값들은 <b>비어 있는 칸을 채우는 용도</b>다.
 */
@Getter
public class ParsedWikitext {

    /** {@code | *****WORK INFO***** } 아래의 {@code |키=값}. */
    private final Map<String, String> workFields = new LinkedHashMap<>();

    /** {@code {{#fte:imslpfile …}}} 블록들. */
    private final List<FileBlock> fileBlocks = new ArrayList<>();

    public String workField(String key) {
        return workFields.get(key);
    }

    /** 원본 파일명(HTML)과 같은 블록을 찾는다 — 밑줄/공백 차이는 무시. */
    public FileBlock findByFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return null;
        }
        String key = normalizeFileName(originalFileName);
        for (FileBlock block : fileBlocks) {
            for (String name : block.getFileNames()) {
                if (normalizeFileName(name).equals(key)) {
                    return block;
                }
            }
        }
        return null;
    }

    static String normalizeFileName(String name) {
        return name.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }

    /** 파일 블록 하나 — 파일명 목록 + 판본 필드. */
    @Getter
    public static class FileBlock {
        private final List<String> fileNames = new ArrayList<>();
        private final Map<String, String> fields = new LinkedHashMap<>();

        public String field(String key) {
            return fields.get(key);
        }
    }
}
