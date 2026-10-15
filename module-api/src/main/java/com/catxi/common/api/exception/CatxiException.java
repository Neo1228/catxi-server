package com.catxi.common.api.exception;

import com.catxi.common.api.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CatxiException extends RuntimeException {

  private final ErrorCode errorCode;
}
