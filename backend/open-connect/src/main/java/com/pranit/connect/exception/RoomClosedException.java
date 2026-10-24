package com.pranit.connect.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class RoomClosedException extends BaseException {
    public RoomClosedException(String message) {
        super(message);
    }
}
