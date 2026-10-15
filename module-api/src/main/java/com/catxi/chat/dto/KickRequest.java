package com.catxi.chat.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record KickRequest(@NotBlank @Email String targetEmail) {}
