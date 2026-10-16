package com.catxi.common.persistence;

import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@MappedSuperclass
public class BaseTimeEntity {
  @CreationTimestamp private LocalDateTime createdTime;
  @UpdateTimestamp private LocalDateTime updatedTime;
}
