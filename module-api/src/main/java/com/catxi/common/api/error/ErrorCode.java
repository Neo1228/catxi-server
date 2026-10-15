package com.catxi.common.api.error;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

  HttpStatus getHttpStatus();

  String getCode();

  String getMessage();
}
