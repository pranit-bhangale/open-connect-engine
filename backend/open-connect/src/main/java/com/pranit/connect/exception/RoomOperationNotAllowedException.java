package com.pranit.connect.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class RoomOperationNotAllowedException extends BaseException {
    public RoomOperationNotAllowedException(String message) {
        super(message);
    }
}
