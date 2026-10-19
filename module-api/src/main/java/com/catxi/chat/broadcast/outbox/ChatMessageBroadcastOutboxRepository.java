package com.catxi.chat.broadcast.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageBroadcastOutboxRepository
    extends JpaRepository<ChatMessageBroadcastOutbox, Long> {}
