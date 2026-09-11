package com.miqu.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 会话模块接口测试。
 *
 * <p>种子事实：
 * <ul>
 *   <li>会话 1 = (user 2 test001, user 3 test002)，10 条消息，test001 侧 2 条未读</li>
 *   <li>会话 2 = (user 2 test001, user 4 test003)，10 条消息，无未读</li>
 *   <li>会话 4 = (user 4, user 6)，test001 <b>不是</b>参与者</li>
 *   <li>会话表强制 {@code user1_id < user2_id}</li>
 * </ul>
 */
@DisplayName("会话模块 /api/conversations")
class ConversationControllerTest extends BaseControllerTest {

    private static final String CONV_1 = "1";   // test001 与 test002
    private static final String CONV_4 = "4";   // test001 不是参与者

    // ==================== 会话列表 ====================

    @Test
    @DisplayName("会话列表：test001 参与 2 个会话，按最后消息时间倒序")
    void list() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(get("/api/conversations").header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(2);

        JsonNode list = json.path("data").path("list");
        // 会话 1 的最后消息时间比会话 2 更近
        assertThat(list.get(0).path("id").asText()).isEqualTo("1");
        assertThat(list.get(0).path("partner").path("username").asText()).isEqualTo("test002");
        assertThat(list.get(1).path("partner").path("username").asText()).isEqualTo("test003");
    }

    @Test
    @DisplayName("会话列表：未读数取会话表的权威值，test001 在会话 1 里有 2 条未读")
    void list_unreadCount() throws Exception {
        String token = login("test001", "123456");
        JsonNode list = exec(get("/api/conversations").header("Authorization", bearer(token)))
                .path("data").path("list");

        assertThat(list.get(0).path("unreadCount").asInt()).isEqualTo(2);
        assertThat(list.get(1).path("unreadCount").asInt()).isZero();
    }

    @Test
    @DisplayName("会话列表：不返回没有消息的空会话")
    void list_excludesEmptyConversations() throws Exception {
        String token = login("test001", "123456");

        // 与 user 9 建一个会话，但一条消息都不发
        exec(post("/api/conversations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":9}"));

        JsonNode json = exec(get("/api/conversations").header("Authorization", bearer(token)));
        // 列表数量不变：空会话不会出现在列表里
        assertThat(json.path("data").path("total").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("会话列表：未登录 → 401")
    void list_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 打开会话 ====================

    @Test
    @DisplayName("打开已存在的会话：返回原会话，不重复创建")
    void open_existingConversation() throws Exception {
        String token = login("test001", "123456");

        JsonNode json = exec(post("/api/conversations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":4}"));

        // test001 与 user 4 的会话是 id=2
        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("id").asText()).isEqualTo("2");
        assertThat(json.path("data").path("partner").path("username").asText()).isEqualTo("test003");
    }

    @Test
    @DisplayName("打开会话：幂等，反复调用返回同一个会话 ID")
    void open_isIdempotent() throws Exception {
        String token = login("test001", "123456");
        String body = "{\"targetUserId\":9}";

        String first = exec(post("/api/conversations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body)).path("data").path("id").asText();
        String second = exec(post("/api/conversations").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body)).path("data").path("id").asText();

        assertThat(first).isEqualTo(second);
    }

    /**
     * 会话表有 CHECK (user1_id &lt; user2_id) 与 uk_users 唯一键，
     * 服务端必须把两个 ID 规整成 (较小, 较大)。
     * 否则 A 找 B 和 B 找 A 会各建一条会话，聊天记录被劈成两半。
     */
    @Test
    @DisplayName("会话规整：A 发起与 B 发起得到同一条会话")
    void open_isSymmetric() throws Exception {
        String token1 = login("test005", "123456");   // user 6
        String token2 = login("test017", "123456");   // user 20

        String fromSix = exec(post("/api/conversations").header("Authorization", bearer(token1))
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":20}"))
                .path("data").path("id").asText();
        String fromTwenty = exec(post("/api/conversations").header("Authorization", bearer(token2))
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":6}"))
                .path("data").path("id").asText();

        assertThat(fromSix)
                .as("(A,B) 与 (B,A) 必须命中同一条会话记录")
                .isEqualTo(fromTwenty);
    }

    @Test
    @DisplayName("打开会话：不能与自己发起会话 → 400")
    void open_self() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/conversations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不能给自己发送私信"));
    }

    @Test
    @DisplayName("打开会话：对方不存在 → 404")
    void open_targetNotFound() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/conversations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":999999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("打开会话：对方被禁用 → 423")
    void open_targetBanned() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(post("/api/conversations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(423));
    }

    // ==================== 聊天记录（游标分页） ====================

    @Test
    @DisplayName("聊天记录：会话 1 有 10 条，按时间正序返回")
    void messages_returnsAll() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(get("/api/conversations/" + CONV_1 + "/messages?size=50")
                .header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data")).hasSize(10);

        // 正序：id 递增
        long previous = -1;
        for (JsonNode item : json.path("data")) {
            long id = item.path("id").asLong();
            assertThat(id).isGreaterThan(previous);
            previous = id;
        }
    }

    @Test
    @DisplayName("聊天记录：mine 正确区分收发双方")
    void messages_mineFlag() throws Exception {
        String token = login("test001", "123456");
        JsonNode list = exec(get("/api/conversations/" + CONV_1 + "/messages?size=50")
                .header("Authorization", bearer(token))).path("data");

        boolean hasMine = false;
        boolean hasTheirs = false;
        for (JsonNode item : list) {
            if (item.path("mine").asBoolean()) {
                hasMine = true;
                assertThat(item.path("senderId").asText()).isEqualTo("2");
            } else {
                hasTheirs = true;
                assertThat(item.path("senderId").asText()).isEqualTo("3");
            }
        }
        assertThat(hasMine).isTrue();
        assertThat(hasTheirs).isTrue();
    }

    /**
     * 游标分页的意义：聊天记录会不断从头部新增，
     * 用 offset 翻历史时新消息会把旧消息往后挤，导致翻页重复或跳过。
     * 用 beforeId 则无论期间新增多少条，翻页结果都稳定。
     */
    @Test
    @DisplayName("聊天记录：游标分页翻页不重复不遗漏")
    void messages_cursorPagination() throws Exception {
        String token = login("test001", "123456");

        JsonNode firstPage = exec(get("/api/conversations/" + CONV_1 + "/messages?size=4")
                .header("Authorization", bearer(token))).path("data");
        assertThat(firstPage).hasSize(4);

        long earliest = firstPage.get(0).path("id").asLong();

        JsonNode secondPage = exec(get("/api/conversations/" + CONV_1 + "/messages?size=4&beforeId=" + earliest)
                .header("Authorization", bearer(token))).path("data");
        assertThat(secondPage).hasSize(4);

        // 第二页必须全部早于第一页，且无交集
        for (JsonNode item : secondPage) {
            assertThat(item.path("id").asLong()).isLessThan(earliest);
        }

        // 两页之间也不应有空隙：第二页最大 id + 1 == 第一页最小 id
        long secondMax = secondPage.get(secondPage.size() - 1).path("id").asLong();
        assertThat(secondMax + 1).isEqualTo(earliest);
    }

    @Test
    @DisplayName("聊天记录：翻到最早一页后返回空数组")
    void messages_cursorBeyondOldest() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(get("/api/conversations/" + CONV_1 + "/messages?beforeId=1")
                .header("Authorization", bearer(token)));

        assertThat(json.path("data")).isEmpty();
    }

    @Test
    @DisplayName("聊天记录：不是参与者 → 403")
    void messages_notMember() throws Exception {
        // test001 不在会话 4 里
        String token = login("test001", "123456");
        mockMvc.perform(get("/api/conversations/" + CONV_4 + "/messages")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("你不是该会话的参与者"));
    }

    @Test
    @DisplayName("聊天记录：会话不存在 → 404")
    void messages_conversationNotFound() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(get("/api/conversations/999999/messages")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("聊天记录：未登录 → 401")
    void messages_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/conversations/" + CONV_1 + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 标记已读 ====================

    @Test
    @DisplayName("标记已读：会话未读数清零，消息 is_read 一并更新")
    void markRead_clearsUnread() throws Exception {
        String token = login("test001", "123456");

        assertThat(exec(get("/api/conversations").header("Authorization", bearer(token)))
                .path("data").path("list").get(0).path("unreadCount").asInt()).isEqualTo(2);

        assertThat(exec(put("/api/conversations/" + CONV_1 + "/read")
                .header("Authorization", bearer(token))).path("code").asInt()).isEqualTo(200);

        // 会话角标归零
        assertThat(exec(get("/api/conversations").header("Authorization", bearer(token)))
                .path("data").path("list").get(0).path("unreadCount").asInt()).isZero();

        // 消息层面的 is_read 也同步了（两个表示必须一致）
        JsonNode messages = exec(get("/api/conversations/" + CONV_1 + "/messages?size=50")
                .header("Authorization", bearer(token))).path("data");
        for (JsonNode message : messages) {
            if (!message.path("mine").asBoolean()) {
                assertThat(message.path("isRead").asBoolean())
                        .as("我收到的消息在标记已读后应为已读")
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("标记已读：只影响我自己收到的消息，不影响对方")
    void markRead_doesNotAffectPartner() throws Exception {
        // 会话 4 中 user 4 有 1 条未读、user 6 有 2 条未读
        assertThat(exec(get("/api/conversations").header("Authorization",
                bearer(login("test003", "123456")))).path("code").asInt()).isEqualTo(200);

        String tokenUser4 = login("test003", "123456");   // user 4
        exec(put("/api/conversations/" + CONV_4 + "/read").header("Authorization", bearer(tokenUser4)));

        // user 6 的未读不受影响
        JsonNode fromUser6 = exec(get("/api/conversations")
                .header("Authorization", bearer(login("test005", "123456")))).path("data").path("list");
        boolean found = false;
        for (JsonNode item : fromUser6) {
            if (item.path("id").asText().equals(CONV_4)) {
                assertThat(item.path("unreadCount").asInt()).isEqualTo(2);
                found = true;
            }
        }
        assertThat(found).as("user 6 应该能看到会话 4").isTrue();
    }

    @Test
    @DisplayName("标记已读：不是参与者 → 403")
    void markRead_notMember() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(put("/api/conversations/" + CONV_4 + "/read")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("标记已读：会话不存在 → 404")
    void markRead_conversationNotFound() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(put("/api/conversations/999999/read").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("标记已读：未登录 → 401")
    void markRead_unauthenticated() throws Exception {
        mockMvc.perform(put("/api/conversations/" + CONV_1 + "/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
