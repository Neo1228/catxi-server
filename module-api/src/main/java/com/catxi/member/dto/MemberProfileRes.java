package com.catxi.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MemberProfileRes(
    @JsonProperty("membername") String memberName, String studentNo, int matchCount) {}
