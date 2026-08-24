package com.ssa.lms.storage.privatefile;

/** 비공개 파일 저장·검증 실패. 메시지는 화면에 그대로 보여줄 수 있는 안내문이다. */
public class PrivateFileException extends RuntimeException {
    public PrivateFileException(String message) {
        super(message);
    }
}
