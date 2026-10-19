package com.catxi.chat.broadcast.outbox;

import com.catxi.chat.broadcast.message.ChatMessageBroadcast;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageBroadcastOutboxService {

  private final ChatMessageBroadcastOutboxRepository outboxRepository;

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(ChatMessageBroadcast message) {
    outboxRepository.save(new ChatMessageBroadcastOutbox(message));
  }
}
