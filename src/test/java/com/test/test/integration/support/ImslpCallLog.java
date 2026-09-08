package com.test.test.integration.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * IMSLP 게이트·다운로드 호출을 <b>일어난 순서대로</b> 적는 테스트용 기록장.
 *
 * <p>기획 §9-1 / 03 §3 의 "IMSLP 의 15초 대기를 우회하지 않고 그대로 기다린다" 는 계약은 응답 JSON 으로 드러나지 않는다.
 * 그래서 {@link RecordingImslpGate} 와 {@link FakeImslpClient} 가 같은 기록장에 이벤트를 남기고,
 * 테스트는 파일 1개당 이벤트가 <b>이 순서</b>로 나오는지 검증한다:
 *
 * <pre>
 *   RESOLVE:{id}   대기 페이지 GET (ImslpClient.resolveFileUrl) — 여기서 카운트다운이 시작된다
 *   FILE_WAIT      ImslpGate.awaitFileWait() — 카운트다운을 그대로 기다린다
 *   DOWNLOAD:{id}  파일 호스트 GET (ImslpClient.downloadResolvedFile)
 * </pre>
 *
 * <p>순서가 이래야 하는 이유(2026-09-07 정정): 이전에는 {@code FILE_WAIT} 가 대기 페이지를 열기도 <b>전</b>에 있었다.
 * 그러면 15초를 다 쓴 뒤 카운트다운 페이지를 받고 0.3초 만에 파일을 치게 되어, IMSLP 서버에는
 * "카운트다운을 안 기다린 클라이언트"로 보인다. 브라우저는 페이지 → 15초 → 파일 순이다.
 * {@code RESOLVE} 와 {@code DOWNLOAD} 에 파일 ID 를 붙이는 것은 <b>같은 파일</b>의 세 이벤트가 붙어 있는지
 * (다른 파일 것이 사이에 끼지 않는지)까지 보기 위해서다.
 *
 * <p>{@link #fileEvents()} 는 요청 간격({@link #REQUEST_SLOT})을 걸러낸다 —
 * 2초 간격 게이트를 어디에 몇 번 두든 그건 구현 자유고, 계약은 "파일마다 대기 페이지 → 15초 → 파일" 뿐이다.
 */
public class ImslpCallLog {

    public static final String REQUEST_SLOT = "REQUEST_SLOT";
    public static final String FILE_WAIT = "FILE_WAIT";
    public static final String RESOLVE_PREFIX = "RESOLVE:";
    public static final String DOWNLOAD_PREFIX = "DOWNLOAD:";

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());

    public void record(String event) {
        events.add(event);
    }

    /** 대기 페이지에서 파일 주소를 읽었다 (파일 GET 아님). */
    public void recordResolve(String imslpFileId) {
        record(resolveEvent(imslpFileId));
    }

    public void recordDownload(String imslpFileId) {
        record(downloadEvent(imslpFileId));
    }

    public void reset() {
        events.clear();
    }

    /** 기록된 전체 이벤트(요청 간격 포함). */
    public List<String> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    /**
     * 파일 관련 이벤트만 남긴 순서 —
     * {@code ["RESOLVE:32718", "FILE_WAIT", "DOWNLOAD:32718", "RESOLVE:00014", "FILE_WAIT", "DOWNLOAD:00014"]} 형태여야 한다.
     */
    public List<String> fileEvents() {
        List<String> filtered = new ArrayList<>();
        for (String event : events()) {
            if (FILE_WAIT.equals(event) || event.startsWith(RESOLVE_PREFIX) || event.startsWith(DOWNLOAD_PREFIX)) {
                filtered.add(event);
            }
        }
        return List.copyOf(filtered);
    }

    public int fileWaitCount() {
        return (int) events().stream().filter(FILE_WAIT::equals).count();
    }

    public int requestSlotCount() {
        return (int) events().stream().filter(REQUEST_SLOT::equals).count();
    }

    public static String resolveEvent(String imslpFileId) {
        return RESOLVE_PREFIX + imslpFileId;
    }

    public static String downloadEvent(String imslpFileId) {
        return DOWNLOAD_PREFIX + imslpFileId;
    }

    /** 파일 N개를 <b>끝까지 정상으로</b> 받을 때 기대되는 이벤트 순서. */
    public static List<String> expectedFileEvents(String... imslpFileIds) {
        List<String> expected = new ArrayList<>();
        for (String id : imslpFileIds) {
            expected.add(resolveEvent(id));
            expected.add(FILE_WAIT);
            expected.add(downloadEvent(id));
        }
        return List.copyOf(expected);
    }
}
