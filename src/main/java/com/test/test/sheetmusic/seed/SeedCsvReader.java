package com.test.test.sheetmusic.seed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 시드 CSV 리더 (01_ERD §6, 03 §8) — RFC 4180 큰따옴표 인용과 여러 줄 필드를 처리한다.
 * 새 CSV 라이브러리를 도입하지 않는다(03 §8).
 */
@Component
public class SeedCsvReader {

    private static final char DELIMITER = ',';
    private static final char QUOTE = '"';

    /** 헤더를 키로 하는 행 목록. 파일이 없으면 빈 목록. */
    public List<Map<String, String>> read(String classpathLocation) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            return List.of();
        }
        String content;
        try (InputStream in = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            int read;
            char[] buffer = new char[8192];
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            content = builder.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("시드 CSV 를 읽을 수 없습니다: " + classpathLocation, e);
        }

        List<List<String>> rows = parse(content);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> header = rows.get(0);
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() == 1 && row.get(0).isBlank()) {
                continue;
            }
            Map<String, String> map = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                map.put(header.get(c).trim(), c < row.size() ? row.get(c) : "");
            }
            result.add(map);
        }
        return result;
    }

    /** 여러 값을 담은 칸을 구분자로 나눈다 (01_ERD §6 — 별칭·작품번호). */
    public static List<String> multi(String value, String separator) {
        List<String> values = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return values;
        }
        for (String part : value.split(Pattern.quote(separator))) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == QUOTE) {
                    if (i + 1 < content.length() && content.charAt(i + 1) == QUOTE) {
                        field.append(QUOTE);
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            switch (ch) {
                case QUOTE -> inQuotes = true;
                case DELIMITER -> {
                    current.add(field.toString());
                    field.setLength(0);
                }
                case '\r' -> {
                    // CRLF 의 CR 은 무시
                }
                case '\n' -> {
                    current.add(field.toString());
                    field.setLength(0);
                    rows.add(current);
                    current = new ArrayList<>();
                }
                default -> field.append(ch);
            }
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(current);
        }
        return rows;
    }
}
