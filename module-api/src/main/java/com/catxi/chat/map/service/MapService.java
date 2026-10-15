package com.catxi.chat.map.service;

import com.catxi.chat.domain.ChatRoom;
import com.catxi.chat.dto.ChatCoordinateBroadcast;
import com.catxi.chat.error.ChatParticipantErrorCode;
import com.catxi.chat.error.ChatRoomErrorCode;
import com.catxi.chat.map.dto.CoordinateRes;
import com.catxi.chat.map.dto.MapInfoRes;
import com.catxi.chat.map.error.MapErrorCode;
import com.catxi.chat.map.realtime.MapCoordinatePublisher;
import com.catxi.chat.repository.ChatParticipantRepository;
import com.catxi.chat.repository.ChatRoomRepository;
import com.catxi.common.api.exception.CatxiException;
import com.catxi.member.domain.Member;
import com.catxi.member.error.MemberErrorCode;
import com.catxi.member.repository.MemberRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MapService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatParticipantRepository chatParticipantRepository;
  private final MemberRepository memberRepository;
  private final CoordinateProvider coordinateProvider;
  private final MapCoordinatePublisher mapCoordinatePublisher;

  public MapInfoRes getCoordinates(Long roomId, String requesterEmail) {
    ChatRoom chatRoom = requireRoomMember(roomId, requesterEmail).room();
    List<CoordinateRes> coordinates =
        chatParticipantRepository.findSummariesByChatRoom(chatRoom).stream()
            .map(recipient -> toCoordinate(roomId, recipient))
            .filter(Objects::nonNull)
            .toList();
    return new MapInfoRes(chatRoom.getStartPoint(), coordinates);
  }

  public void saveDepartureCoordinate(
      Double latitude, Double longitude, Long roomId, String requesterEmail) {
    validateCoordinates(latitude, longitude);
    requireRoomMember(roomId, requesterEmail);
    coordinateProvider.saveDeparture(roomId, latitude, longitude);
  }

  public void shareCoordinate(Long roomId, String senderEmail, Double latitude, Double longitude) {
    validateCoordinates(latitude, longitude);
    RoomMember roomMember = requireRoomMember(roomId, senderEmail);
    Map<String, Double> departure = coordinateProvider.getDeparture(roomId);
    Double distance = null;
    if (departure != null
        && departure.get("latitude") != null
        && departure.get("longitude") != null) {
      distance =
          calculateDistance(
              latitude, longitude, departure.get("latitude"), departure.get("longitude"));
    }

    coordinateProvider.saveCoordinateWithDistance(
        roomId, roomMember.member().getEmail(), latitude, longitude, distance);
    Member member = roomMember.member();
    mapCoordinatePublisher.publish(
        new ChatCoordinateBroadcast(
            roomId,
            member.getEmail(),
            member.getMemberName(),
            member.getNickname(),
            latitude,
            longitude,
            distance));
  }

  private CoordinateRes toCoordinate(
      Long roomId, ChatParticipantRepository.ChatParticipantSummary recipient) {
    Map<String, Double> coordinate = coordinateProvider.getCoordinate(roomId, recipient.getEmail());
    if (coordinate == null
        || coordinate.get("latitude") == null
        || coordinate.get("longitude") == null) {
      return null;
    }
    return new CoordinateRes(
        roomId,
        recipient.getEmail(),
        recipient.getMemberName(),
        recipient.getNickname(),
        coordinate.get("latitude"),
        coordinate.get("longitude"),
        coordinate.get("distance"));
  }

  private RoomMember requireRoomMember(Long roomId, String email) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
    Member member =
        memberRepository
            .findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
    if (!chatParticipantRepository.existsByChatRoomAndMember(room, member)) {
      throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
    }
    return new RoomMember(room, member);
  }

  private void validateCoordinates(Double latitude, Double longitude) {
    if (latitude == null
        || longitude == null
        || !Double.isFinite(latitude)
        || !Double.isFinite(longitude)
        || latitude < -90
        || latitude > 90
        || longitude < -180
        || longitude > 180) {
      throw new CatxiException(MapErrorCode.COORDINATE_PARSE_FAILED);
    }
  }

  private double calculateDistance(
      double latitude1, double longitude1, double latitude2, double longitude2) {
    final double earthRadiusMeters = 6_371_000;
    double latitudeDistance = Math.toRadians(latitude2 - latitude1);
    double longitudeDistance = Math.toRadians(longitude2 - longitude1);
    double a =
        Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
            + Math.cos(Math.toRadians(latitude1))
                * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDistance / 2)
                * Math.sin(longitudeDistance / 2);
    double centralAngle = 2 * Math.atan2(Math.sqrt(Math.min(1, a)), Math.sqrt(Math.max(0, 1 - a)));
    double distanceKilometers = earthRadiusMeters * centralAngle / 1_000;
    return Math.round(distanceKilometers * 100) / 100.0;
  }

  private record RoomMember(ChatRoom room, Member member) {}
}
