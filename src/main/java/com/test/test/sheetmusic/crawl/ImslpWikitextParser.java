package com.test.test.sheetmusic.crawl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 위키텍스트 파서 (03 §1 — 라이브러리 없이 줄 단위로 읽는다).
 *
 * <p>{@code {{#fte:imslpfile …}}} 블록의 {@code |키=값} 과 {@code | *****WORK INFO*****} 아래 작품 필드를 뽑는다.
 * 오디오 블록({@code {{#fte:imslpaudio}}})은 건너뛴다.
 */
@Component
public class ImslpWikitextParser {

    private static final String FILE_BLOCK_START = "{{#fte:imslpfile";
    private static final String AUDIO_BLOCK_START = "{{#fte:imslpaudio";
    private static final String WORK_INFO_MARKER = "*****WORK INFO*****";
    private static final String END_MARKER = "*****END OF TEMPLATE*****";
    private static final Pattern FILE_NAME_KEY = Pattern.compile("^File Name\\s*\\d*$");
    private static final Pattern LINK_TEMPLATE =
            Pattern.compile("\\{\\{Link(?:Ed|Arr|Cmp)\\|([^}]*)\\}\\}");
    private static final Pattern ANY_TEMPLATE = Pattern.compile("\\{\\{[^{}]*\\}\\}");
    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]");
    private static final Pattern SIMPLE_LINK = Pattern.compile("\\[\\[([^\\]]*)\\]\\]");
    private static final Pattern BR_TAG = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    public ParsedWikitext parse(String wikitext) {
        ParsedWikitext parsed = new ParsedWikitext();
        if (wikitext == null || wikitext.isBlank()) {
            return parsed;
        }

        ParsedWikitext.FileBlock current = null;
        boolean inAudio = false;
        boolean inWorkInfo = false;

        for (String rawLine : wikitext.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.contains(END_MARKER)) {
                break;
            }
            if (line.contains(WORK_INFO_MARKER)) {
                inWorkInfo = true;
                current = null;
                inAudio = false;
                continue;
            }
            if (line.startsWith(AUDIO_BLOCK_START)) {
                inAudio = true;
                current = null;
                continue;
            }
            if (line.startsWith(FILE_BLOCK_START)) {
                inAudio = false;
                current = new ParsedWikitext.FileBlock();
                parsed.getFileBlocks().add(current);
                continue;
            }
            if (line.startsWith("}}")) {
                current = null;
                inAudio = false;
                continue;
            }
            if (!line.startsWith("|")) {
                continue;
            }

            int equals = line.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = line.substring(1, equals).trim();
            String value = cleanValue(line.substring(equals + 1));

            if (inWorkInfo) {
                parsed.getWorkFields().put(key, value);
            } else if (current != null && !inAudio) {
                if (FILE_NAME_KEY.matcher(key).matches()) {
                    if (!value.isEmpty()) {
                        current.getFileNames().add(value);
                    }
                } else {
                    current.getFields().put(stripIndex(key), value);
                }
            }
        }
        return parsed;
    }

    /** {@code Scanner 2} 처럼 붙는 파일 순번을 떼어 첫 값만 남긴다. */
    private String stripIndex(String key) {
        return key.replaceAll("\\s+\\d+$", "").trim();
    }

    /** 템플릿·위키링크·HTML 태그를 사람이 읽는 문자열로 정리한다. */
    String cleanValue(String raw) {
        String value = raw == null ? "" : raw;
        value = BR_TAG.matcher(value).replaceAll(", ");

        Matcher link = LINK_TEMPLATE.matcher(value);
        StringBuilder builder = new StringBuilder();
        while (link.find()) {
            String[] parts = link.group(1).split("\\|");
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < Math.min(parts.length, 2); i++) {
                String part = parts[i].trim();
                if (!part.isEmpty()) {
                    if (name.length() > 0) {
                        name.append(' ');
                    }
                    name.append(part);
                }
            }
            link.appendReplacement(builder, Matcher.quoteReplacement(name.toString()));
        }
        link.appendTail(builder);
        value = builder.toString();

        value = ANY_TEMPLATE.matcher(value).replaceAll("");
        value = WIKI_LINK.matcher(value).replaceAll("$1");
        value = SIMPLE_LINK.matcher(value).replaceAll("$1");
        value = HTML_TAG.matcher(value).replaceAll("");
        value = value.replace("''", "");
        return value.replaceAll("\\s+", " ").trim();
    }
}
