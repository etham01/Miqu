package com.miqu.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

/** 用户。对应 {@code user} 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /** 登录名，唯一。 */
    private String username;

    /**
     * BCrypt 哈希。
     * 三重防护：@JsonIgnore 防序列化、@ToString.Exclude 防日志打印、UserVO 与实体分离防误返回。
     */
    @JsonIgnore
    @ToString.Exclude
    private String password;

    private String nickname;

    /** 邮箱，唯一。 */
    private String email;

    /** 见 {@link com.miqu.common.enums.GenderEnum}，0未知 1男 2女。 */
    private Integer gender;

    private LocalDate birthday;

    private String bio;

    private String avatar;

    /** 见 {@link com.miqu.common.enums.RoleEnum}，1=USER 2=ADMIN。 */
    private Integer role;

    /** 见 {@link com.miqu.common.enums.UserStatusEnum}，1正常 0禁用。 */
    private Integer status;

    /** 关注数（冗余，由 follow 表重算保证一致）。 */
    private Integer followingCount;

    /** 粉丝数（冗余）。 */
    private Integer followerCount;

    /** 动态数（冗余）。 */
    private Integer postCount;
}
