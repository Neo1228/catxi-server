package com.catxi.common.api;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class CommonPageResponse<T> {
  private List<T> content; // 데이터 리스트
  private int totalPages; // 전체 페이지 수
  private long totalElements; // 전체 요소 수
  private int currentPage; // 현재 페이지 번호
  private int size; // 한 페이지당 크기
  private boolean hasNext; // 다음 페이지 존재 여부

  public static <T> CommonPageResponse<T> of(Page<T> page) {
    return of(
        page.getContent(),
        page.getTotalPages(),
        page.getTotalElements(),
        page.getNumber(),
        page.getSize(),
        page.hasNext());
  }

  public static <T> CommonPageResponse<T> of(
      List<T> content,
      int totalPages,
      long totalElements,
      int currentPage,
      int size,
      boolean hasNext) {
    return CommonPageResponse.<T>builder()
        .content(content)
        .totalPages(totalPages)
        .totalElements(totalElements)
        .currentPage(currentPage)
        .size(size)
        .hasNext(hasNext)
        .build();
  }
}
