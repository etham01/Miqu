package com.miqu.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.miqu.entity.Notification;
import com.miqu.mapper.NotificationMapper;
import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知模块接口测试。
 *
 * <p>种子事实（user 2 = test001）：
 * <ul>
 *   <li>可见通知共 23 条</li>
 *   <li>未读 15 条：关注 5、点赞 5、评论 5</li>
 *   <li>已读 8 条：关注 3、点赞 5、评论 0</li>
 * </ul>
 */
@DisplayName("通知模块 /api/notifications")
class NotificationControllerTest extends BaseControllerTest {

    private static final long TEST001_ID = 2L;

    @Autowired
    private NotificationMapper notificationMapper;

    private static final String LIST = "/api/notifications";

    // ==================== 列表 ====================

    @Test
    @DisplayName("通知列表：test001 共 23 条，按时间倒序")
    void list() throws Exception {
        String token = login("test001", "123456");
        JsonNode json = exec(get(LIST + "?size=50").header("Authorization", bearer(token)));

        assertThat(json.path("code").asInt()).isEqualTo(200);
        assertThat(json.path("data").path("total").asLong()).isEqualTo(23);
        assertThat(json.path("data").path("list")).hasSize(23);
    }

    @Test
    @DisplayName("通知列表：每条都带触发者信息与内容快照字段")
    void list_containsActor() throws Exception {
        String token = login("test001", "123456");
        JsonNode first = exec(get(LIST).header("Authorization", bearer(token)))
                .path("data").path("list").get(0);

        assertThat(first.path("actor").path("id").isMissingNode()).isFalse();
        assertThat(first.path("actor").path("nickname").asText()).isNotBlank();
        assertThat(first.path("type").asInt()).isBetween(1, 3);
        // 触发者信息不应包含邮箱
        assertThat(first.path("actor").has("email")).isFalse();
    }

    @Test
    @DisplayName("通知列表：按类型过滤（点赞类共 10 条）")
    void list_filterByType() throws Exception {
        String token = login("test001", "123456");

        assertThat(exec(get(LIST + "?type=1&size=50").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(8);    // 关注
        assertThat(exec(get(LIST + "?type=2&size=50").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(10);   // 点赞
        assertThat(exec(get(LIST + "?type=3&size=50").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(5);    // 评论
    }

    @Test
    @DisplayName("通知列表：按已读状态过滤")
    void list_filterByReadState() throws Exception {
        String token = login("test001", "123456");

        assertThat(exec(get(LIST + "?isRead=0&size=50").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(15);
        assertThat(exec(get(LIST + "?isRead=1&size=50").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(8);
    }

    @Test
    @DisplayName("通知列表：类型取值非法 → 400")
    void list_invalidType() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(get(LIST + "?type=9").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("通知类型只能是 1、2 或 3"));
    }

    /**
     * 种子数据里 23 条通知全部属于 test001，因此这里先制造一条属于别人的通知，
     * 才能真正验证"数据按接收者隔离"，而不是仅靠数量不同来间接推断。
     *
     * <p>注意要挑一个 test001 **尚未关注**的对象：seed 里 test001 已关注 3~8，
     * 拿这些去关注会得到 409，也就不会产生通知。
     */
    @Test
    @DisplayName("通知列表：只能看到自己的通知")
    void list_onlyOwnNotifications() throws Exception {
        String test001Token = login("test001", "123456");
        String user9Token = login("test008", "123456");   // user 9，test001 尚未关注

        // user 9 原本没有任何通知
        assertThat(exec(get(LIST + "?size=50").header("Authorization", bearer(user9Token)))
                .path("data").path("total").asLong()).isZero();

        // test001 关注 user 9 → 给 user 9 造出一条通知
        assertThat(exec(post("/api/users/9/follow").header("Authorization", bearer(test001Token)))
                .path("code").asInt()).isEqualTo(200);

        JsonNode user9List = exec(get(LIST + "?size=50").header("Authorization", bearer(user9Token)))
                .path("data");
        assertThat(user9List.path("total").asLong()).isEqualTo(1);
        assertThat(user9List.path("list").get(0).path("actor").path("username").asText())
                .isEqualTo("test001");

        // test001 自己的列表里不应该出现这条（接收者是 user 9）
        assertThat(exec(get(LIST + "?size=50").header("Authorization", bearer(test001Token)))
                .path("data").path("total").asLong()).isEqualTo(23);
    }

    @Test
    @DisplayName("通知列表：未登录 → 401")
    void list_unauthenticated() throws Exception {
        mockMvc.perform(get(LIST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 未读数 ====================

    @Test
    @DisplayName("未读数：test001 未读 15 条，拆开为关注 5 / 点赞 5 / 评论 5")
    void unreadCount() throws Exception {
        String token = login("test001", "123456");
        JsonNode data = exec(get(LIST + "/unread-count").header("Authorization", bearer(token))).path("data");

        assertThat(data.path("total").asLong()).isEqualTo(15);
        assertThat(data.path("follow").asLong()).isEqualTo(5);
        assertThat(data.path("like").asLong()).isEqualTo(5);
        assertThat(data.path("comment").asLong()).isEqualTo(5);
    }

    @Test
    @DisplayName("未读数：未读合计等于三项之和")
    void unreadCount_totalIsSumOfParts() throws Exception {
        String token = login("test001", "123456");
        JsonNode data = exec(get(LIST + "/unread-count").header("Authorization", bearer(token))).path("data");

        assertThat(data.path("total").asLong())
                .isEqualTo(data.path("follow").asLong()
                        + data.path("like").asLong()
                        + data.path("comment").asLong());
    }

    @Test
    @DisplayName("未读数：未登录 → 401")
    void unreadCount_unauthenticated() throws Exception {
        mockMvc.perform(get(LIST + "/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 标记已读 ====================

    @Test
    @DisplayName("标记单条已读：未读总数 -1")
    void markRead() throws Exception {
        String token = login("test001", "123456");

        long notificationId = exec(get(LIST + "?isRead=0").header("Authorization", bearer(token)))
                .path("data").path("list").get(0).path("id").asLong();

        assertThat(exec(put(LIST + "/" + notificationId + "/read")
                .header("Authorization", bearer(token))).path("code").asInt()).isEqualTo(200);

        assertThat(exec(get(LIST + "/unread-count").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(14);
    }

    @Test
    @DisplayName("标记已读：重复标记是幂等的")
    void markRead_isIdempotent() throws Exception {
        String token = login("test001", "123456");

        long notificationId = exec(get(LIST + "?isRead=0").header("Authorization", bearer(token)))
                .path("data").path("list").get(0).path("id").asLong();

        exec(put(LIST + "/" + notificationId + "/read").header("Authorization", bearer(token)));
        JsonNode second = exec(put(LIST + "/" + notificationId + "/read")
                .header("Authorization", bearer(token)));

        assertThat(second.path("code").asInt()).isEqualTo(200);
        assertThat(exec(get(LIST + "/unread-count").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isEqualTo(14);
    }

    @Test
    @DisplayName("标记已读：操作他人的通知 → 403（而不是 404，两者语义不同）")
    void markRead_othersNotification() throws Exception {
        // 种子数据里所有通知都属于 test001，先造一条属于 user 9 的：
        // test001 关注 user 9 → 给 user 9 生成一条关注通知
        String token = login("test001", "123456");
        exec(post("/api/users/9/follow").header("Authorization", bearer(token)));

        Notification others = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, 9L)
                .orderByDesc(Notification::getId)
                .last("LIMIT 1"));
        assertThat(others).as("关注后 user 9 应收到一条通知").isNotNull();

        mockMvc.perform(put(LIST + "/" + others.getId() + "/read").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限执行该操作"));

        // 确认没有被误改
        assertThat(notificationMapper.selectById(others.getId()).getIsRead()).isZero();
    }

    @Test
    @DisplayName("标记已读：通知不存在 → 404")
    void markRead_notFound() throws Exception {
        String token = login("test001", "123456");
        mockMvc.perform(put(LIST + "/999999/read").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("标记已读：未登录 → 401")
    void markRead_unauthenticated() throws Exception {
        mockMvc.perform(put(LIST + "/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("全部标记已读：未读数归零，未读列表变空")
    void markAllRead() throws Exception {
        String token = login("test001", "123456");

        assertThat(exec(put(LIST + "/read-all").header("Authorization", bearer(token)))
                .path("code").asInt()).isEqualTo(200);

        JsonNode unread = exec(get(LIST + "/unread-count").header("Authorization", bearer(token))).path("data");
        assertThat(unread.path("total").asLong()).isZero();
        assertThat(unread.path("follow").asLong()).isZero();
        assertThat(unread.path("like").asLong()).isZero();
        assertThat(unread.path("comment").asLong()).isZero();

        assertThat(exec(get(LIST + "?isRead=0").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isZero();
    }

    @Test
    @DisplayName("全部标记已读：只影响自己的通知，不影响他人")
    void markAllRead_doesNotAffectOthers() throws Exception {
        String otherToken = login("test002", "123456");
        long before = exec(get(LIST + "/unread-count").header("Authorization", bearer(otherToken)))
                .path("data").path("total").asLong();

        String token = login("test001", "123456");
        exec(put(LIST + "/read-all").header("Authorization", bearer(token)));

        assertThat(exec(get(LIST + "/unread-count").header("Authorization", bearer(otherToken)))
                .path("data").path("total").asLong()).isEqualTo(before);
    }

    @Test
    @DisplayName("全部标记已读：对没有未读的用户是安全的空操作")
    void markAllRead_whenNothingUnread() throws Exception {
        String token = login("test018", "123456");   // 没有任何通知

        assertThat(exec(put(LIST + "/read-all").header("Authorization", bearer(token)))
                .path("code").asInt()).isEqualTo(200);
        assertThat(exec(get(LIST + "/unread-count").header("Authorization", bearer(token)))
                .path("data").path("total").asLong()).isZero();
    }

    @Test
    @DisplayName("全部标记已读：未登录 → 401")
    void markAllRead_unauthenticated() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put(LIST + "/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
}
