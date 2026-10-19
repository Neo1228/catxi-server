package com.catxi.chat.repository;

import com.catxi.chat.domain.QChatParticipant;
import com.catxi.chat.domain.QChatRoom;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.common.domain.MeetingPoint;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatRoomRepositoryCustomImpl implements ChatRoomRepositoryCustom {
  private final JPAQueryFactory jpaQueryFactory;
  private static final Set<MeetingPoint> STATIONS =
      Set.of(
          MeetingPoint.SOSA_ST,
          MeetingPoint.GURO_ST,
          MeetingPoint.YEOKGOK_ST,
          MeetingPoint.BUCHEON_ST,
          MeetingPoint.SINDORIM_ST);

  @Override
  public Page<ChatRoomSummary> findByMeetingPointAndDirection(
      MeetingPoint meetingPoint, String direction, Pageable pageable) {
    QChatRoom chatRoom = QChatRoom.chatRoom;
    QChatParticipant participant = QChatParticipant.chatParticipant;
    List<ChatRoomSummary> chatRooms =
        jpaQueryFactory
            .select(
                Projections.constructor(
                    ChatRoomSummary.class,
                    chatRoom.roomId,
                    chatRoom.hostId,
                    chatRoom.startPoint,
                    chatRoom.endPoint,
                    chatRoom.maxCapacity,
                    participant.id.countDistinct(),
                    chatRoom.status,
                    chatRoom.departAt,
                    chatRoom.createdTime))
            .from(chatRoom)
            .leftJoin(chatRoom.participants, participant)
            .where(
                chatRoom.status.eq(RoomStatus.WAITING),
                filterByMeetingPointAndDirection(meetingPoint, direction))
            .groupBy(chatRoom.roomId)
            .orderBy(getOrderSpecifier(pageable.getSort()))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

    JPAQuery<Long> total =
        jpaQueryFactory
            .select(chatRoom.count())
            .from(chatRoom)
            .where(
                chatRoom.status.eq(RoomStatus.WAITING),
                filterByMeetingPointAndDirection(meetingPoint, direction));

    return PageableExecutionUtils.getPage(chatRooms, pageable, total::fetchOne);
  }

  private OrderSpecifier<?>[] getOrderSpecifier(Sort sort) {
    List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
    QChatRoom chatRoom = QChatRoom.chatRoom;

    for (Sort.Order order : sort) {
      OrderSpecifier<?> orderSpecifier =
          switch (order.getProperty()) {
            case "departAt" ->
                order.isAscending() ? chatRoom.departAt.asc() : chatRoom.departAt.desc();
            case "createdTime" ->
                order.isAscending() ? chatRoom.createdTime.asc() : chatRoom.createdTime.desc();
            default -> throw new IllegalStateException("서비스 검증을 거치지 않은 채팅방 정렬 기준입니다.");
          };
      orderSpecifiers.add(orderSpecifier);
    }
    orderSpecifiers.add(chatRoom.roomId.desc());

    return orderSpecifiers.toArray(new OrderSpecifier[0]);
  }

  private BooleanExpression filterByMeetingPointAndDirection(
      MeetingPoint meetingPoint, String direction) {
    QChatRoom chatRoom = QChatRoom.chatRoom;
    // 전체 역을 조회할 때는 출발지나 도착지가 등록된 역인 방만 찾습니다.
    if (direction.equals("TO_SCHOOL")) {
      return meetingPoint == null
          ? chatRoom.startPoint.in(STATIONS)
          : chatRoom.startPoint.eq(meetingPoint);
    } else if (direction.equals("FROM_SCHOOL")) {
      return meetingPoint == null
          ? chatRoom.endPoint.in(STATIONS)
          : chatRoom.endPoint.eq(meetingPoint);
    } else {
      throw new IllegalStateException("서비스 검증을 거치지 않은 채팅방 방향입니다.");
    }
  }
}
