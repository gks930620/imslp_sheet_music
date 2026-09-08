package com.test.test.integration.support;

import com.test.test.sheetmusic.crawl.ImslpGate;

/**
 * 실제 {@link ImslpGate} 를 그대로 쓰되(테스트 설정은 0ms 라 자지 않는다) 호출 사실만 {@link ImslpCallLog} 에 남긴다.
 * {@link FakeImslpClientConfig} 가 {@code @Primary} 로 등록해 워커·판본 받아오기가 이 게이트를 쓰게 한다.
 */
public class RecordingImslpGate extends ImslpGate {

    private final ImslpCallLog callLog;

    public RecordingImslpGate(ImslpCallLog callLog) {
        this.callLog = callLog;
    }

    @Override
    public synchronized void awaitRequestSlot() {
        super.awaitRequestSlot();
        callLog.record(ImslpCallLog.REQUEST_SLOT);
    }

    @Override
    public void awaitFileWait() {
        super.awaitFileWait();
        callLog.record(ImslpCallLog.FILE_WAIT);
    }
}
