package com.pubgsite.bglog.exception;

public class MatchNotFoundException extends RuntimeException {
    public MatchNotFoundException(String id) {
        super("매치를 찾을 수 없습니다: " + id);
    }
}
