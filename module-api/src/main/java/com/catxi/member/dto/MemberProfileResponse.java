package com.catxi.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MemberProfileResponse(
    @JsonProperty("membername") String memberName, String studentNo, int matchCount) {}
