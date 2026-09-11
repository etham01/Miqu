package com.miqu.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 私信发送接口测试。
 *
 * <p>种子事实：test001(id=2) 的私信未读总数为 2（都在与 test002 的会话里）。
 */
@DisplayName("私信模块 /api/messages")
class MessageControllerTest extends BaseControllerTest {

    private long send(String token, long receiverId, String content) throws Exception {
        JsonNode json = exec(post("/api/messages").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        java.util.Map.of("receiverId", receiverId, "content", content))));
        assertThat(json.path("code").asInt()).isEqualTo(200);
        return json.path("data").path("id").asLong();
    }

    private JsonNode conversationWith(String token, String conversationId) throws Exception {
        for (JsonNode item : exec(get("/api/conversations").header("Authorization", bearer(token)))
                .path("data").path("list")) {
            if (item.path("id").asText().equals(conversationId)) {
                return item;
            }
        }
        return objectMapper.createObjectNode();
    }

    /**
     * 私聊要求双方互相关注（2026-09-11 冻结规则）：让 test001(id=2) 与 user 9(test008) 互相 follow。
     *
     * <p>本类中"发送成功/自动建会话/长度边界"等用例验证的是**消息本身**的行为，
     * 因此先把前置关系补成互关——**只改前置数据，不改任何断言**。
     * 基类带 {@code @Transactional}，这些关注关系会在用例结束后自动回滚。
     */
    private void makeMutualWithUser9(String test001Token) throws Exception {
        exec(post("/api/users/9/follow").header("Authorization", bearer(test001Token)));
        String user9Token = login("test008", "123456");
        exec(post("/api/users/2/follow").header("Authorization", bearer(user9Token)));
    }

    // ==================== 发送 ====================

    @Test
    @DisplayName("发送私信成功：内容与双方 ID 正确返回")
    void send_success() throws Exception {
        String token = login("test001", "123456");

        makeMutualWithUser9(token);
        JsonNode json = exec(post("/api/messages").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":9,\"content\":\"你好，初次联系\"}"));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("id").isMissingNode()).isFalse();
        assertThat(json.path("data").path("conversationId").isMissingNode()).isFalse();
        assertThat(json.path("data").path("senderId").asText()).isEqualTo("2");
        assertThat(json.path("data").path("receiverId").asText()).isEqualTo("9");
        assertThat(json.path("data").path("content").asText()).isEqualTo("你好，初次联系");
        assertThat(json.path("data").path("mine").asBoolean()).isTrue();
        assertThat(json.path("data").path("isRead").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("发送私信会自动创建会话，无需客户端先建")
    void send_createsConversation() throws Exception {
        String token = login("test001", "123456");
        makeMutualWithUser9(token);
        long messageId = send(token, 9, "自动建会话");

        long conversationId = exec(get("/api/conversations").header("Authorization", bearer(token)))
                .path("data").path("list").get(0).path("id").asLong();

        JsonNode messages = exec(get("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", bearer(token))).path("data");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).path("id").asLong()).isEqualTo(messageId);
    }

    @Test
    @DisplayName("发到已存在的会话：复用会话，不新建")
    void send_reusesExistingConversation() throws Exception {
        String token = login("test001", "123456");
        // test001 与 test003(user 4) 已有会话 id=2
        exec(post("/api/messages").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":4,\"content\":\"复用会话\"}"));

        // 会话总数仍然是 2，没有多出来一个
        assertThat(exec(get("/api/conversations").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(2);

        assertThat(conversationWith(token, "2").path("lastMessagePreview").asText())
                .isEqualTo("复用会话");
    }

    @Test
    @DisplayName("发送后：会话的最后消息被更新，接收方未读数 +1")
    void send_updatesConversationAndUnread() throws Exception {
        // 会话 1 = (user1=2 test001, user2=3 test002)，未读 2 条属于 user1 即 test001
        String senderToken = login("test002", "123456");   // user 3
        String receiverToken = login("test001", "123456"); // user 2

        long before = exec(get("/api/messages/unread-count").header("Authorization", bearer(receiverToken)))
                .path("data").path("total").asLong();
        assertThat(before).isEqualTo(2);

        send(senderToken, 2, "这条会让未读变多");

        long after = exec(get("/api/messages/unread-count").header("Authorization", bearer(receiverToken)))
                .path("data").path("total").asLong();
        assertThat(after).isEqualTo(before + 1);

        // 接收方看到的会话预览同步更新
        assertThat(conversationWith(receiverToken, "1").path("lastMessagePreview").asText())
                .isEqualTo("这条会让未读变多");
    }

    @Test
    @DisplayName("发送者自己的未读数不增加")
    void send_doesNotIncreaseSenderUnread() throws Exception {
        String senderToken = login("test001", "123456");
        long before = exec(get("/api/messages/unread-count").header("Authorization", bearer(senderToken)))
                .path("data").path("total").asLong();
        assertThat(before).isEqualTo(2);   // test001 的未读来自会话 1

        send(senderToken, 3, "自己发的不算未读");

        long after = exec(get("/api/messages/unread-count").header("Authorization", bearer(senderToken)))
                .path("data").path("total").asLong();
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("长消息在会话列表里被截断为 100 字符预览")
    void send_truncatesPreview() throws Exception {
        String token = login("test001", "123456");
        send(token, 3, "很长的消息".repeat(100));   // 500 字符

        String preview = conversationWith(token, "1").path("lastMessagePreview").asText();
        assertThat(preview.length()).isEqualTo(100);
    }

    @Test
    @DisplayName("发送私信：不能给自己发 → 400")
    void send_toSelf() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/messages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":2,\"content\":\"自言自语\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能给自己发送私信"));
    }

    @Test
    @DisplayName("发送私信：接收者不存在 → 404")
    void send_receiverNotFound() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/messages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":999999,\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("发送私信：接收者被禁用 → 423")
    void send_receiverBanned() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/messages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":12,\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(423));
    }

    @Test
    @DisplayName("发送私信：接收者已注销 → 404")
    void send_receiverDeleted() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/messages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":13,\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("发送私信：内容为空 → 400")
    void send_blankContent() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/messages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":9,\"content\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("消息内容不能为空"));
    }

    @Test
    @DisplayName("发送私信：内容超过 1000 字符 → 400")
    void send_contentTooLong() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/messages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("receiverId", 9, "content", "字".repeat(1001)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("消息内容不能超过 1000 个字符"));
    }

    @Test
    @DisplayName("发送私信：恰好 1000 字符可以通过（边界值）")
    void send_contentExactlyMaxLength() throws Exception {
        String token = login("test001", "123456");
        makeMutualWithUser9(token);
        mockMvc.perform(post("/api/messages").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("receiverId", 9, "content", "字".repeat(1000)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("发送私信：未登录 → 401")
    void send_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":9,\"content\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 未读数 ====================

    @Test
    @DisplayName("私信未读数：test001 为 2")
    void unreadCount() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(get("/api/messages/unread-count").header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("私信未读数：没有任何私信往来的用户为 0")
    void unreadCount_zeroForUserWithoutConversations() throws Exception {
        // user 21(test018) 不在任何会话里
        String token = login("test018", "123456");
        JsonNode json = exec(get("/api/messages/unread-count").header("Authorization", bearer(token)));

        assertThat(json.path("data").path("total").asLong()).isZero();
    }

    @Test
    @DisplayName("私信未读数：未登录 → 401")
    void unreadCount_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/messages/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
