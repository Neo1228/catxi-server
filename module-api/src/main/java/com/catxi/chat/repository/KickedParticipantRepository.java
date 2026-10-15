package com.catxi.chat.repository;

import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.domain.KickedParticipant;
import com.catxi.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KickedParticipantRepository extends JpaRepository<KickedParticipant, Long> {
  boolean existsByChatRoomAndMember(ChatRoom room, Member member);
}
