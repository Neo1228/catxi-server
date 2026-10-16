package com.catxi.worker.notification.delivery;

import java.util.List;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class FcmRecipientRepository {

  private final DatabaseClient databaseClient;

  public FcmRecipientRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  Flux<Recipient> findActive(List<Long> memberIds) {
    if (memberIds.isEmpty()) {
      return Flux.empty();
    }

    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, fcm_token
            FROM member
            WHERE status = 'ACTIVE'
              AND fcm_token IS NOT NULL
              AND TRIM(fcm_token) <> ''
              AND id IN (
            """);
    for (int index = 0; index < memberIds.size(); index++) {
      if (index > 0) {
        sql.append(',');
      }
      sql.append(":member").append(index);
    }
    sql.append(')');

    DatabaseClient.GenericExecuteSpec query = databaseClient.sql(sql.toString());
    for (int index = 0; index < memberIds.size(); index++) {
      query = query.bind("member" + index, memberIds.get(index));
    }

    return query
        .map(
            (row, metadata) ->
                new Recipient(row.get("id", Long.class), row.get("fcm_token", String.class)))
        .all();
  }

  // FCM 토큰은 대소문자를 구분하므로 DB collation과 무관하게 비교합니다.
  Mono<Boolean> clearExpiredToken(long memberId, String token) {
    return databaseClient
        .sql(
            """
            UPDATE member
            SET fcm_token = NULL, fcm_token_updated_at = NULL
            WHERE id = :memberId
              AND CAST(fcm_token AS BINARY) = CAST(:token AS BINARY)
            """)
        .bind("memberId", memberId)
        .bind("token", token)
        .fetch()
        .rowsUpdated()
        .map(updatedRows -> updatedRows > 0);
  }

  public record Recipient(long memberId, String token) {}
}
