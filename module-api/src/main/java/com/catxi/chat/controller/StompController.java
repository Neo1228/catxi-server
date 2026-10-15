package com.catxi.chat.controller;

import com.catxi.chat.dto.ChatMessageSendReq;
import com.catxi.chat.service.ChatMessageDeliveryService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@RequiredArgsConstructor
@Controller
public class StompController {

  private final ChatMessageDeliveryService chatMessageDeliveryService;

  @MessageMapping("/{roomId}")
  public void sendMessage(
      @DestinationVariable Long roomId, @Valid ChatMessageSendReq request, Principal principal) {
    chatMessageDeliveryService.sendMessage(roomId, principal.getName(), request);
  }
}
