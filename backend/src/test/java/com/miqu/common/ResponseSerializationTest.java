package com.miqu.common;

import com.miqu.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 响应序列化约定。
 *
 * <p>本项目有一条贯穿所有接口的约定，前端与接口自动化测试都依赖它：
 * <ul>
 *   <li><b>id 字段序列化为字符串</b>——JS 的 Number 安全整数上限是 2^53，
 *       自增主键暂时够用，但一旦引入雪花 ID 就会静默丢精度。
 *       现在配好成本为零，将来再改是全局破坏性变更。</li>
 *   <li><b>计数类字段保持数字</b>——它们要参与前端运算（角标、分页、排序），
 *       变成字符串会让 {@code total > 0} 这类判断出现意外结果。</li>
 * </ul>
 *
 * <p>这条边界很容易在新增接口时被无意破坏——例如用
 * {@code Map<String, Long>} 返回一个计数，自动装箱后的 {@code Long}
 * 会被当成 id 一样转成字符串。这个测试类专门守住它。
 */
@DisplayName("响应序列化约定")
class ResponseSerializationTest extends BaseControllerTest {

    @Test
    @DisplayName("id 字段是字符串：post.id / author.id / user.id")
    void idsAreSerializedAsString() throws Exception {
        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.author.id").isString())
                .andExpect(jsonPath("$.data.likeCount").isNumber());

        mockMvc.perform(get("/api/users/2"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.followerCount").isNumber());
    }

    @Test
    @DisplayName("分页的 total/page/size 是数字，能直接参与前端运算")
    void paginationFieldsAreNumbers() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").isNumber())
                .andExpect(jsonPath("$.data.size").isNumber())
                // hasNext 是布尔，不是字符串
                .andExpect(jsonPath("$.data.hasNext").isBoolean());
    }

    /**
     * 这条曾经真的错过：{@code Map.of("total", someLong)} 里的计数会被自动装箱成
     * 包装类型 Long，从而被 JacksonConfig 当成 id 一样转成字符串，
     * 导致私信未读数是 {@code "3"} 而通知未读数是 {@code 3}，两个角标行为不一致。
     */
    @Test
    @DisplayName("未读数保持数字：私信与通知两个角标行为必须一致")
    void unreadCountsAreNumbers() throws Exception {
        String token = login("test001", "123456");

        mockMvc.perform(get("/api/messages/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.follow").isNumber())
                .andExpect(jsonPath("$.data.like").isNumber())
                .andExpect(jsonPath("$.data.comment").isNumber());
    }

    @Test
    @DisplayName("关注/点赞的结果计数是数字")
    void operationResultCountsAreNumbers() throws Exception {
        String token = login("test001", "123456");

        // user 9 是 test001 尚未关注的
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/users/9/follow").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.following").isBoolean())
                .andExpect(jsonPath("$.data.followerCount").isNumber());

        // 动态自己建一条再点赞：种子里 post 2 已经被 test001 点过赞了，
        // 直接拿它点赞会得到 409，data 为 null，断言路径取不到值
        long postId = exec(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/posts").header("Authorization", bearer(token))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"content\":\"序列化约定测试用动态\"}"))
                .path("data").path("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/posts/" + postId + "/like").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.liked").isBoolean())
                .andExpect(jsonPath("$.data.likeCount").isNumber());
    }

    @Test
    @DisplayName("统一响应体固定为 code / message / data 三个字段")
    void resultEnvelopeShape() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(jsonPath("$.code").isNumber())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("时间字段格式为 yyyy-MM-dd HH:mm:ss，不是时间戳数组")
    void timeFieldsAreFormattedStrings() throws Exception {
        mockMvc.perform(get("/api/posts/1"))
                // 若未注册 JavaTimeModule 的序列化器，这里会是 [2026,9,11,10,0,0] 这样的数组
                .andExpect(jsonPath("$.data.createTime").isString())
                .andExpect(jsonPath("$.data.createTime").value(
                        org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")));
    }
}
