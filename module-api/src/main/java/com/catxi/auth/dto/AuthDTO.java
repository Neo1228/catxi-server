package com.catxi.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDTO {
  private AuthDTO() {}

  public static record LoginRequest(
      @JsonProperty("email") @JsonAlias("membername") @NotBlank @Email @Size(max = 254)
          String email,
      @NotBlank @Size(max = 72) String password) {}

  public static record LoginResponse(String token) {}
}
