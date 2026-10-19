package com.catxi.api.config;

import com.catxi.chat.realtime.RedisChatBroadcastListener;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class RedisConfiguration {

  // 리스너 컨테이너들을 추적하기 위한 리스트
  private final List<RedisMessageListenerContainer> listenerContainers = new ArrayList<>();

  @Value("${spring.data.redis.host}")
  private String host;

  @Value("${spring.data.redis.port}")
  private int port;

  @Value("${spring.data.redis.username}")
  private String username;

  @Value("${spring.data.redis.password}")
  private String password;

  @Value("${spring.data.redis.ssl.enabled}")
  private boolean sslEnabled;

  @Value("${spring.data.redis.timeout}")
  private Duration commandTimeout;

  @Value("${spring.data.redis.connect-timeout}")
  private Duration connectTimeout;

  private LettuceConnectionFactory createConnectionFactory(
      RedisStandaloneConfiguration configuration) {
    if (StringUtils.hasText(username)) {
      configuration.setUsername(username);
    }
    if (StringUtils.hasText(password)) {
      configuration.setPassword(RedisPassword.of(password));
    }

    LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfiguration =
        LettuceClientConfiguration.builder()
            .commandTimeout(commandTimeout)
            .clientOptions(
                ClientOptions.builder()
                    .socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())
                    .build());
    if (sslEnabled) {
      clientConfiguration.useSsl().and();
    }
    return new LettuceConnectionFactory(configuration, clientConfiguration.build());
  }

  // 연결 기본 객체
  @Bean
  @Qualifier("chatRedisConnectionFactory")
  public RedisConnectionFactory chatPubSubFactory() {
    RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
    configuration.setHostName(host);
    configuration.setPort(port);
    return createConnectionFactory(configuration);
  }

  @Bean("chatPubSubTemplate")
  @Qualifier("chatPubSub")
  public StringRedisTemplate chatPubSubTemplate(
      @Qualifier("chatRedisConnectionFactory") RedisConnectionFactory cf) {
    StringRedisTemplate tpl = new StringRedisTemplate(cf);
    return tpl;
  }

  @Bean("redisPubSubExecutor")
  public ThreadPoolTaskExecutor redisPubSubExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(512);
    executor.setThreadNamePrefix("redis-pubsub-");
    // 과부하 때 무제한 적재하거나 메시지를 버리는 대신 수신 측에 역압을 전달한다.
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(5);
    return executor;
  }

  @Bean({"readyTimeoutScheduler", "taskScheduler"})
  public ThreadPoolTaskScheduler readyTimeoutScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(4);
    scheduler.setThreadNamePrefix("ready-timeout-");
    return scheduler;
  }

  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      @Qualifier("chatRedisConnectionFactory") RedisConnectionFactory cf,
      RedisChatBroadcastListener listener,
      @Qualifier("redisPubSubExecutor") ThreadPoolTaskExecutor redisPubSubExecutor) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(cf);
    container.setTaskExecutor(redisPubSubExecutor);

    // 명시적 구독: 채널 + 패턴
    container.addMessageListener(listener, new ChannelTopic("chat"));
    container.addMessageListener(listener, new ChannelTopic("map"));
    container.addMessageListener(listener, new PatternTopic("ready:*"));
    container.addMessageListener(listener, new PatternTopic("participants:*"));
    container.addMessageListener(listener, new PatternTopic("kick:*"));
    container.addMessageListener(listener, new PatternTopic("roomaccessrevoked:*"));
    container.addMessageListener(listener, new PatternTopic("roomdeleted:*"));
    container.addMessageListener(listener, new PatternTopic("readyresult:*"));

    // 종료 시 정리를 위해 리스트에 추가
    listenerContainers.add(container);
    return container;
  }

  /** Redis 리스너 컨테이너들의 정리 작업 */
  @PreDestroy
  public void cleanup() {
    log.info("Redis 리스너 컨테이너 정리 시작 - 총 {} 개", listenerContainers.size());

    for (RedisMessageListenerContainer container : listenerContainers) {
      try {
        if (container.isRunning()) {
          container.stop();
          log.debug("Redis 리스너 컨테이너 정지 완료");
        }

        // 정리 작업
        container.destroy();
        log.debug("Redis 리스너 컨테이너 정리 완료");

      } catch (Exception e) {
        log.warn("Redis 리스너 컨테이너 정리 중 오류 발생", e);
      }
    }

    listenerContainers.clear();
    log.info("Redis 리스너 컨테이너 정리 완료");
  }

  // JWT 토큰 저장용 Redis 연결
  @Bean
  @Qualifier("tokenRedisConnectionFactory")
  public RedisConnectionFactory tokenRedisConnectionFactory() {
    RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
    configuration.setHostName(host);
    configuration.setPort(port);

    // 리프레시 토큰용 DB 분리
    configuration.setDatabase(1);
    return createConnectionFactory(configuration);
  }

  // JWT 토큰 저장용 RedisTemplate
  @Bean("tokenRedisTemplate")
  @Qualifier("tokenRedisTemplate")
  public RedisTemplate<String, String> tokenRedisTemplate(
      @Qualifier("tokenRedisConnectionFactory") RedisConnectionFactory redisConnectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory);
    template.setDefaultSerializer(new StringRedisSerializer());
    return template;
  }

  @Bean
  @Primary // RedisTemplate 중에서는 이것이 기본
  public RedisTemplate<String, String> redisTemplate(
      @Qualifier("chatRedisConnectionFactory") RedisConnectionFactory redisConnectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(redisConnectionFactory);
    template.setDefaultSerializer(new StringRedisSerializer());
    return template;
  }
}
