package com.catxi.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SignUpRequest {

  @NotBlank
  @Email
  @Size(max = 254)
  private String email;

  @NotBlank
  @Size(max = 30)
  @JsonProperty("membername")
  private String memberName;

  @NotBlank
  @Size(min = 8, max = 72)
  private String password;

  @NotBlank
  @Pattern(regexp = "\\d{9}", message = "학번은 9자리 숫자로 입력해주세요")
  private String studentNo;

  @NotBlank
  @Size(max = 9, message = "닉네임은 9자 이하로 입력해주세요")
  private String nickname;
}
