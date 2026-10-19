package com.catxi.chat.websocket;

import com.catxi.api.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompChannelInterceptor stompChannelInterceptor;
  private final ChatRoomOutboundGuard chatRoomOutboundGuard;
  private final CorsProperties corsProperties;

  public StompWebSocketConfig(
      StompChannelInterceptor stompChannelInterceptor,
      ChatRoomOutboundGuard chatRoomOutboundGuard,
      CorsProperties corsProperties) {
    this.stompChannelInterceptor = stompChannelInterceptor;
    this.chatRoomOutboundGuard = chatRoomOutboundGuard;
    this.corsProperties = corsProperties;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/connect")
        .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
        .withSockJS()
        .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js")
        .setStreamBytesLimit(512 * 1024)
        .setHttpMessageCacheSize(1000)
        .setDisconnectDelay(30 * 1000)
        .setSuppressCors(true);
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    //  /publish/1 형태로 메시지 발행해야 함을 설정
    // /publish 로 시작하는 url 패턴으로 메시지가 발행되면 @Controller 객체의 @MessageMapping 메서드로 라우팅
    registry.setApplicationDestinationPrefixes("/publish");

    //  /topic/1 형태로 메시지 수신해야 함을 설정
    // /topic 로 시작하는 url 패턴으로 메시지가 발행되면 @Controller 객체의 @MessageMapping 메서드로 라우팅
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    registration
        .setMessageSizeLimit(64 * 1024)
        .setSendTimeLimit(15 * 1000)
        .setSendBufferSizeLimit(512 * 1024);
  }

  // 연결과 구독 요청에도 인증을 적용하도록 inbound interceptor를 등록합니다.
  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(stompChannelInterceptor);
  }

  @Override
  public void configureClientOutboundChannel(ChannelRegistration registration) {
    registration.interceptors(chatRoomOutboundGuard);
  }
}
