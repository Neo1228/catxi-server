package com.catxi.common.api.success;

import org.springframework.http.HttpStatus;

public interface SuccessCode {

  HttpStatus getHttpStatus();

  String getCode();

  String getMessage();
}
