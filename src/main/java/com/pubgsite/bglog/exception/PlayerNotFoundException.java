package com.pubgsite.bglog.exception;

public class PlayerNotFoundException extends RuntimeException {

    public PlayerNotFoundException(String name) {
        super("플레이어를 찾을 수 없습니다: " + name);
    }
}
