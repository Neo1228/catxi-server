package com.catxi.chat.repository;

import com.catxi.chat.domain.ChatRoomSummary;
import com.catxi.chat.domain.QChatParticipant;
import com.catxi.chat.domain.QChatRoom;
import com.catxi.chat.domain.RoomStatus;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.common.domain.Location;
import com.catxi.member.domain.QMember;
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
  private static final Set<Location> STATIONS =
      Set.of(
          Location.SOSA_ST,
          Location.GURO_ST,
          Location.YEOKGOK_ST,
          Location.BUCHEON_ST,
          Location.SINDORIM_ST);

  @Override
  public Page<ChatRoomSummary> findByLocationAndDirection(
      Location location, String point, Pageable pageable) {
    QChatRoom chatRoom = QChatRoom.chatRoom;
    QChatParticipant participant = QChatParticipant.chatParticipant;
    QMember host = QMember.member;

    List<ChatRoomSummary> chatRooms =
        jpaQueryFactory
            .select(
                Projections.constructor(
                    ChatRoomSummary.class,
                    chatRoom.roomId,
                    host.id,
                    host.memberName,
                    host.nickname,
                    host.matchCount,
                    chatRoom.startPoint,
                    chatRoom.endPoint,
                    chatRoom.maxCapacity,
                    participant.id.countDistinct(),
                    chatRoom.status,
                    chatRoom.departAt,
                    chatRoom.createdTime))
            .from(chatRoom)
            .join(chatRoom.host, host)
            .leftJoin(chatRoom.participants, participant)
            .where(
                chatRoom.status.eq(RoomStatus.WAITING), filterByLocationAndPoint(location, point))
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
                chatRoom.status.eq(RoomStatus.WAITING), filterByLocationAndPoint(location, point));

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
            default -> throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
          };
      orderSpecifiers.add(orderSpecifier);
    }
    orderSpecifiers.add(chatRoom.roomId.desc());

    return orderSpecifiers.toArray(new OrderSpecifier[0]);
  }

  private BooleanExpression filterByLocationAndPoint(Location location, String point) {
    QChatRoom chatRoom = QChatRoom.chatRoom;
    // 전체 역을 조회할 때는 출발지나 도착지가 택시 승강장인 방만 찾습니다.
    if (point.equals("TO_SCHOOL")) {
      return location == null ? chatRoom.startPoint.in(STATIONS) : chatRoom.startPoint.eq(location);
    } else if (point.equals("FROM_SCHOOL")) {
      return location == null ? chatRoom.endPoint.in(STATIONS) : chatRoom.endPoint.eq(location);
    } else {
      throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
    }
  }
}
