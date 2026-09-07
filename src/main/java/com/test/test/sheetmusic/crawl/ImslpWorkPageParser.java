package com.test.test.sheetmusic.crawl;

import com.test.test.sheetmusic.common.ImslpUrlNormalizer;
import com.test.test.sheetmusic.edition.EditionKind;
import com.test.test.sheetmusic.edition.EditionScope;
import com.test.test.sheetmusic.edition.LicenseCode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Component;

/**
 * 작품 페이지 HTML 파서 (00 조사 §1, 03 §1 jsoup).
 *
 * <p>판본 종류는 탭 컨테이너 끝의 {@code span.na-marker[data-name]} 으로 정한다(탭 id 가 아니라 표시명).
 * Scores / Parts / Arrangements and Transcriptions 외의 탭(스케치·점자 등)은 통째로 제외한다.
 * 파일은 <b>원본 파일명이 .pdf 인 것만</b> 판본으로 만든다.
 */
@Component
@Slf4j
public class ImslpWorkPageParser {

    private static final Pattern PAGE_COUNT = Pattern.compile("(\\d+)\\s*pp\\.");
    private static final Pattern DOWNLOAD_COUNT = Pattern.compile("Total number of downloads:\\s*(\\d+)");
    private static final Pattern FILE_ID = Pattern.compile("IMSLP(\\w+)");
    private static final Pattern MOVEMENT_IN_PARENS = Pattern.compile("\\(No\\.(\\d+)\\)");
    private static final Pattern MOVEMENT_PREFIX = Pattern.compile("^(\\d+)\\s*\\.");
    private static final Pattern LIFE_YEARS = Pattern.compile("\\s*\\(\\s*\\d{0,4}\\s*[\\-\\u2013\\u2014]\\s*\\d{0,4}\\s*\\)");
    private static final Pattern YEAR = Pattern.compile("(1[0-9]{3}|20[0-9]{2})");
    private static final Pattern PLATE = Pattern.compile("Plate\\s+(.+?)\\.\\s*$");
    private static final Pattern SCANNED_BY = Pattern.compile("(?:scanned|typeset)\\s+by\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final int MOVEMENTS_MAX_LENGTH = 500;

    public ParsedWorkPage parse(ImslpWorkPage page) {
        Document document = Jsoup.parse(page.getHtml(), ImslpUrlNormalizer.WIKI_PREFIX);
        ParsedWorkPage parsed = ParsedWorkPage.builder()
                .canonicalUrl(page.getCanonicalUrl())
                .titleOriginal(parseTitle(document, page.getCanonicalUrl()))
                .catalogNumbers(new ArrayList<>())
                .aliases(new ArrayList<>())
                .editions(new ArrayList<>())
                .build();
        parseGeneralInformation(document, parsed);
        parsed.setEditions(parseEditions(document));
        return parsed;
    }

    // ===== 제목 =====

    /** {@code <title>제목 (작곡가) - IMSLP</title>} 에서 마지막 " (작곡가)" 를 뗀다. */
    private String parseTitle(Document document, String canonicalUrl) {
        String title = document.title();
        if (title == null || title.isBlank()) {
            String fromUrl = ImslpUrlNormalizer.titleOf(canonicalUrl);
            title = fromUrl == null ? "" : fromUrl.replace('_', ' ');
        }
        int suffix = title.lastIndexOf(" - IMSLP");
        if (suffix > 0) {
            title = title.substring(0, suffix);
        }
        return stripComposerSuffix(title.trim());
    }

    private String stripComposerSuffix(String title) {
        if (title.endsWith(")")) {
            int open = title.lastIndexOf(" (");
            if (open > 0) {
                return title.substring(0, open).trim();
            }
        }
        return title;
    }

    // ===== General Information =====

    private void parseGeneralInformation(Document document, ParsedWorkPage parsed) {
        Element body = document.selectFirst("div.wi_body");
        if (body == null) {
            return;
        }
        Set<String> aliases = new LinkedHashSet<>();
        for (Element row : body.select("tr")) {
            Element th = row.selectFirst("th");
            Element td = row.selectFirst("td");
            if (th == null || td == null) {
                continue;
            }
            String label = label(th);
            String value = clean(td.text());
            if (label.equals("Composer")) {
                parsed.setComposerNameOriginal(value);
                parsed.setComposerImslpUrl(ImslpUrlNormalizer.composerCategoryUrl(value));
            } else if (label.startsWith("Alternative Title")) {
                aliases.addAll(splitSemicolon(value));
            } else if (label.startsWith("Name Translations") || label.startsWith("Name Aliases")) {
                aliases.addAll(koreanValues(td));
            } else if (label.startsWith("Opus/Catalogue Number")) {
                parsed.getCatalogNumbers().addAll(splitSemicolon(value));
            } else if (label.equals("Key")) {
                parsed.setMusicalKey(value);
            } else if (label.startsWith("Mov")) {
                parsed.setMovements(truncate(value, MOVEMENTS_MAX_LENGTH));
            } else if (label.startsWith("Year/Date of Comp")) {
                parsed.setCompositionYear(truncate(value, 20));
            } else if (label.equals("Instrumentation")) {
                parsed.setInstrumentation(value);
            }
        }
        parsed.getAliases().addAll(aliases);
    }

    /** 모바일 축약 스팬({@code span.ms555})을 뺀 라벨 텍스트. */
    private String label(Element th) {
        Element copy = th.clone();
        copy.select("span.ms555").remove();
        return clean(copy.text());
    }

    /** {@code <span title="ko">…</span>} — title 이 "ko" 토큰을 포함하는 값만. */
    private List<String> koreanValues(Element td) {
        List<String> values = new ArrayList<>();
        for (Element span : td.select("span[title]")) {
            for (String code : span.attr("title").split(",")) {
                if (code.trim().equalsIgnoreCase("ko")) {
                    String text = clean(span.text());
                    if (!text.isEmpty()) {
                        values.add(text);
                    }
                    break;
                }
            }
        }
        return values;
    }

    // ===== 판본 =====

    private List<ParsedEdition> parseEditions(Document document) {
        List<ParsedEdition> editions = new ArrayList<>();
        for (Element tab : document.select("div.jq-ui-tabs")) {
            EditionKind kind = kindOf(tab);
            if (kind == null) {
                continue;
            }
            String sectionLabel = null;
            for (Element child : tab.children()) {
                if (child.normalName().equals("h4")) {
                    sectionLabel = clean(child.text());
                } else if (child.hasClass("we")) {
                    editions.addAll(parseBlock(child, kind, sectionLabel));
                }
            }
        }
        return editions;
    }

    /** 탭 표시명 → 판본 종류. 목록에 없는 탭(스케치·점자·Full Scores 등)은 수집하지 않는다. */
    private EditionKind kindOf(Element tab) {
        Element marker = tab.selectFirst("span.na-marker[data-name]");
        if (marker == null) {
            return null;
        }
        String name = marker.attr("data-name").trim();
        return switch (name) {
            case "Scores" -> EditionKind.COMPLETE_SCORE;
            case "Parts" -> EditionKind.PARTS;
            case "Arrangements and Transcriptions" -> EditionKind.ARRANGEMENT;
            default -> null;
        };
    }

    private List<ParsedEdition> parseBlock(Element block, EditionKind kind, String sectionLabel) {
        List<ParsedEdition> editions = new ArrayList<>();
        if (block.selectFirst("div.we_audio_top") != null || block.selectFirst("table.we_audio_info") != null) {
            return editions;
        }
        EditionInfo info = parseEditionInfo(block);
        boolean complete = sectionLabel == null || sectionLabel.equalsIgnoreCase("Complete");

        for (Element fileBlock : block.children()) {
            if (!fileBlock.id().startsWith("IMSLP")) {
                continue;
            }
            ParsedEdition edition = parseFile(fileBlock);
            if (edition == null) {
                continue;
            }
            edition.setKind(kind);
            edition.setScope(complete ? EditionScope.COMPLETE : EditionScope.MOVEMENT);
            edition.setSectionLabel(complete ? null : sectionLabel);
            edition.setMovementNumber(complete ? null : movementNumber(sectionLabel));
            edition.setEditor(info.editor);
            edition.setArranger(info.arranger);
            edition.setPublisher(info.publisher);
            edition.setPublishYear(info.publishYear);
            edition.setPlateNumber(info.plateNumber);
            edition.setImslpCopyrightText(info.copyrightText);
            edition.setImslpLicenseCode(LicenseCode.fromText(info.copyrightText));
            editions.add(edition);
        }
        return editions;
    }

    private ParsedEdition parseFile(Element fileBlock) {
        Matcher idMatcher = FILE_ID.matcher(fileBlock.id());
        if (!idMatcher.matches()) {
            return null;
        }
        String fileId = idMatcher.group(1);

        Element hidden = fileBlock.selectFirst("span.we_file_info2 span.hidden a");
        String originalFileName = hidden == null ? null : originalFileName(hidden);
        if (originalFileName == null || !originalFileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return null;
        }

        Element descriptionSpan = fileBlock.selectFirst("div.we_file_download a.external span[title]");
        String description = descriptionSpan == null ? null : clean(descriptionSpan.text());

        Element info2 = fileBlock.selectFirst("span.we_file_info2");
        Integer pageCount = null;
        if (info2 != null) {
            Matcher pages = PAGE_COUNT.matcher(info2.text());
            if (pages.find()) {
                pageCount = Integer.valueOf(pages.group(1));
            }
        }

        Integer downloadCount = null;
        Element counter = fileBlock.selectFirst("span[title^=Total number of downloads]");
        if (counter != null) {
            Matcher matcher = DOWNLOAD_COUNT.matcher(counter.attr("title"));
            if (matcher.find()) {
                downloadCount = Integer.valueOf(matcher.group(1));
            }
        }

        return ParsedEdition.builder()
                .imslpFileId(fileId)
                .imslpOriginalFileName(truncate(originalFileName, 300))
                .imslpFileUrl(ImslpUrlNormalizer.WIKI_PREFIX + "Special:ImagefromIndex/" + fileId)
                .imslpDescription(truncate(description, 300))
                .pageCount(pageCount)
                .imslpDownloadCount(downloadCount)
                .scanner(truncate(scanner(fileBlock), 200))
                .build();
    }

    /** 원본 파일명 — 링크의 title 속성이 가장 깨끗하다(퍼센트 인코딩·밑줄 없음). */
    private String originalFileName(Element link) {
        String title = link.attr("title").trim();
        if (!title.isEmpty()) {
            return title;
        }
        String href = link.attr("href");
        int slash = href.lastIndexOf('/');
        String name = slash >= 0 ? href.substring(slash + 1) : href;
        try {
            return URLDecoder.decode(name.replace("+", "%2B"), StandardCharsets.UTF_8).replace('_', ' ');
        } catch (IllegalArgumentException e) {
            return name.replace('_', ' ');
        }
    }

    /** {@code PDF scanned by Unknown} 첫 줄에서 제공자만. */
    private String scanner(Element fileBlock) {
        Element info = fileBlock.selectFirst("div.we_file_info span.mh555");
        if (info == null) {
            return null;
        }
        String firstLine = info.html().split("(?i)<br\\s*/?>")[0];
        String text = clean(Jsoup.parse(firstLine).text());
        Matcher matcher = SCANNED_BY.matcher(text);
        return matcher.find() ? clean(matcher.group(1)) : null;
    }

    private Integer movementNumber(String sectionLabel) {
        if (sectionLabel == null) {
            return null;
        }
        Matcher inParens = MOVEMENT_IN_PARENS.matcher(sectionLabel);
        if (inParens.find()) {
            return Integer.valueOf(inParens.group(1));
        }
        Matcher prefix = MOVEMENT_PREFIX.matcher(sectionLabel.trim());
        return prefix.find() ? Integer.valueOf(prefix.group(1)) : null;
    }

    private EditionInfo parseEditionInfo(Element block) {
        EditionInfo info = new EditionInfo();
        Element table = block.selectFirst("table.we_edition_info");
        if (table == null) {
            return info;
        }
        for (Element row : table.select("tr")) {
            Element th = row.selectFirst("th");
            Element td = row.selectFirst("td");
            if (th == null || td == null) {
                continue;
            }
            String label = clean(th.text());
            if (label.startsWith("Editor")) {
                info.editor = truncate(stripLifeYears(clean(td.text())), 200);
            } else if (label.startsWith("Arranger")) {
                info.arranger = truncate(stripLifeYears(clean(td.text())), 200);
            } else if (label.startsWith("Pub") && !label.startsWith("Purchase")) {
                String publisher = clean(td.text());
                info.publisher = truncate(publisher, 300);
                Matcher year = YEAR.matcher(publisher);
                if (year.find()) {
                    info.publishYear = Integer.valueOf(year.group(1));
                }
                Matcher plate = PLATE.matcher(publisher);
                if (plate.find()) {
                    info.plateNumber = truncate(clean(plate.group(1)), 100);
                }
            } else if (label.startsWith("Copyright")) {
                Element link = td.selectFirst("a[title^=IMSLP:]");
                String copyright = link != null
                        ? link.attr("title").substring("IMSLP:".length())
                        : clean(td.ownText());
                info.copyrightText = truncate(clean(copyright), 200);
            }
        }
        return info;
    }

    /** 판본 정보 표에서 읽은 값 (블록 안 모든 파일이 공유한다). */
    private static final class EditionInfo {
        private String editor;
        private String arranger;
        private String publisher;
        private Integer publishYear;
        private String plateNumber;
        private String copyrightText;
    }

    // ===== 문자열 유틸 =====

    private static List<String> splitSemicolon(String value) {
        List<String> values = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return values;
        }
        for (String part : value.split(";")) {
            String trimmed = clean(part);
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static String stripLifeYears(String value) {
        return clean(LIFE_YEARS.matcher(value).replaceAll(""));
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
