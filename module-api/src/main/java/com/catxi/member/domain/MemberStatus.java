package com.catxi.member.domain;

public enum MemberStatus {
  ACTIVE("ACTIVE"),
  PENDING("PENDING"),
  INACTIVE("INACTIVE");

  private String code;

  private MemberStatus(String code) {
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public static MemberStatus fromCode(String code) {
    for (MemberStatus status : values()) {
      if (status.getCode().equals(code)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown MEMBERSTATUS: " + code);
  }
}
