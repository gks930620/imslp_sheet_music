package com.test.test.sheetmusic.edition.repository;

/** 곡별 판본 수 프로젝션 (02 §4-6 {@code editionCount}) — 판본 행을 읽지 않고 숫자만 받는다. */
public interface WorkEditionCount {

    Long getWorkId();

    long getEditionCount();
}
