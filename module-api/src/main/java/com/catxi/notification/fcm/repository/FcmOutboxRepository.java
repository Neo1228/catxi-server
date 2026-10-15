package com.catxi.notification.fcm.repository;

import com.catxi.notification.fcm.domain.FcmOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FcmOutboxRepository extends JpaRepository<FcmOutbox, Long> {}
