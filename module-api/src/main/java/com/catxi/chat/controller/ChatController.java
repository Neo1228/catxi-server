package com.catxi.chat.controller;

import com.catxi.auth.security.CustomUserDetails;
import com.catxi.chat.dto.ChatMessagePageRes;
import com.catxi.chat.dto.ChatMessageRes;
import com.catxi.chat.dto.ChatRoomInfoRes;
import com.catxi.chat.dto.ChatRoomPageRes;
import com.catxi.chat.dto.KickRequest;
import com.catxi.chat.dto.RoomCreateReq;
import com.catxi.chat.dto.RoomCreateRes;
import com.catxi.chat.service.ChatMessageService;
import com.catxi.chat.service.ChatRoomService;
import com.catxi.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

  private final ChatMessageService chatMessageService;
  private final ChatRoomService chatRoomService;

  public ChatController(ChatMessageService chatMessageService, ChatRoomService chatRoomService) {
    this.chatMessageService = chatMessageService;
    this.chatRoomService = chatRoomService;
  }

  @Operation(summary = "채팅방 생성", description = "로그인한 사용자가 새로운 채팅방을 생성합니다.")
  @PostMapping("/room/create")
  public ResponseEntity<ApiResponse<RoomCreateRes>> createRoom(
      @Valid @RequestBody RoomCreateReq roomCreateReq,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    String email = userDetails.getUsername();
    RoomCreateRes res = chatRoomService.createRoom(roomCreateReq, email);
    return ResponseEntity.ok(ApiResponse.success(res));
  }

  @Operation(
      summary = "채팅방 메시지 조회",
      description = "채팅 이력이 100건 이하인 방에서만 사용할 수 있습니다. 그 이상은 페이지 조회 API를 사용합니다.",
      deprecated = true)
  @GetMapping("/{roomId}/messages")
  public ResponseEntity<ApiResponse<List<ChatMessageRes>>> getHistory(
      @PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails userDetails) {

    String email = userDetails.getUsername();
    List<ChatMessageRes> history = chatMessageService.getChatHistory(roomId, email);

    return ResponseEntity.ok(ApiResponse.success(history));
  }

  @Operation(
      summary = "채팅 이력 페이지 조회",
      description =
          "기본 조회는 최신 메시지부터 이전 이력을 반환합니다. beforeCreatedAt과 beforeMessageId를 함께 전달해 이전 페이지를 조회하거나, 두 값 대신 afterMessageId를 전달해 해당 메시지 이후의 새 메시지를 ID 오름차순으로 복구합니다.")
  @GetMapping("/{roomId}/messages/page")
  public ResponseEntity<ApiResponse<ChatMessagePageRes>> getChatHistoryPage(
      @PathVariable Long roomId,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(required = false) LocalDateTime beforeCreatedAt,
      @RequestParam(required = false) Long beforeMessageId,
      @RequestParam(required = false) Long afterMessageId,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    ChatMessagePageRes page =
        chatMessageService.getChatHistoryPage(
            roomId,
            userDetails.getUsername(),
            beforeCreatedAt,
            beforeMessageId,
            afterMessageId,
            limit);
    return ResponseEntity.ok(ApiResponse.success(page));
  }

  @Operation(
      summary = "채팅방 목록 조회",
      description =
          "역 방향, 정류장, 정렬 기준, 페이지 정보를 기반으로 채팅방 목록을 조회합니다."
              + """
			한 페이지에 최대 10개의 채팅방이 포함됩니다.
			- direction: 'FROM_SCHOOL' 또는 'TO_SCHOOL' 중 하나를 선택합니다.
			- station: 'SOSA_ST' 또는 'YEOKGOK_ST' 또는 'ALL' 중 하나를 선택합니다.
			- sort: 'departAt' 또는 'createdTime' 중 하나를 선택합니다.
			- page: 페이지 번호 (기본값은 0입니다).
			""")
  @GetMapping("/rooms")
  public ResponseEntity<ApiResponse<ChatRoomPageRes>> getRoomList(
      @RequestParam("direction") String direction,
      @RequestParam("station") String station,
      @RequestParam("sort") String sort,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    ChatRoomPageRes roomPage =
        chatRoomService.getChatRoomPage(direction, station, sort, page, userDetails.getUsername());
    return ResponseEntity.ok(ApiResponse.success(roomPage));
  }

  @Operation(summary = "채팅방 나가기", description = "로그인한 사용자가 해당 채팅방에서 나갑니다.")
  @DeleteMapping("/{roomId}/leave")
  public ResponseEntity<ApiResponse<Void>> leaveRoom(
      @PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails userDetails) {

    String email = userDetails.getUsername();
    chatRoomService.leaveChatRoom(roomId, email);
    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }

  @Operation(summary = "채팅방 참여", description = "로그인한 사용자가 지정한 채팅방에 참여합니다.")
  @PostMapping("/rooms/{roomId}/join")
  public ResponseEntity<ApiResponse<Void>> joinChatRoom(
      @PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails userDetails) {

    String email = userDetails.getUsername();
    chatRoomService.joinChatRoom(roomId, email);
    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }

  @PostMapping("/rooms/{roomId}/kick")
  @Operation(summary = "채팅방 유저 강퇴 (방장만 가능)")
  public ResponseEntity<ApiResponse<Void>> kickUser(
      @PathVariable Long roomId,
      @Valid @RequestBody KickRequest request,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    String requesterEmail = userDetails.getUsername();
    chatRoomService.kickUser(roomId, requesterEmail, request.targetEmail());
    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }

  @DeleteMapping("/{roomId}/remove")
  @Operation(
      summary = "채팅방 삭제 (방장만 가능)",
      description =
          """
          방장이 채팅방을 삭제합니다.
          - 채팅방이 MATCHED가 아닐 경우, 매칭 기록을 저장하지 않고 채팅방을 삭제합니다.
          - 채팅방 인원 수가 1명 이하일 경우, 매칭 기록을 저장하지 않고 채팅방을 삭제합니다.
          - 방장이 아닐 경우 예외가 발생합니다.
          """)
  public ResponseEntity<ApiResponse<Void>> removeChatRoom(
      @PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails userDetails) {

    String email = userDetails.getUsername();
    chatRoomService.removeChatRoom(roomId, email);
    return ResponseEntity.ok(ApiResponse.successWithNoData());
  }

  @Operation(summary = "나의 채팅방 ID 조회", description = "현재 참여 중인 채팅방의 ID를 반환합니다.")
  @GetMapping("/rooms/myid")
  public ResponseEntity<ApiResponse<Long>> getMyRoomId(
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    String email = userDetails.getUsername();
    Long roomId = chatRoomService.getMyChatRoomId(email);
    return ResponseEntity.ok(ApiResponse.success(roomId));
  }

  @Operation(summary = "채팅방 정보 조회", description = "채팅방 ID를 통해 채팅방의 정보를 조회합니다.")
  @GetMapping("/rooms/{roomId}")
  public ResponseEntity<ApiResponse<ChatRoomInfoRes>> getChatRoomInfo(
      @PathVariable Long roomId, @AuthenticationPrincipal CustomUserDetails userDetails) {

    String email = userDetails.getUsername();
    ChatRoomInfoRes chatRoomRes = chatRoomService.getChatRoomInfo(roomId, email);
    return ResponseEntity.ok(ApiResponse.success(chatRoomRes));
  }
}
