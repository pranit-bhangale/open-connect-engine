package com.pranit.connect.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class RoomAlreadyExistsException extends BaseException {

    public RoomAlreadyExistsException(final String message) {
        super(message);
    }
}
