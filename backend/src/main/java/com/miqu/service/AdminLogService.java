package com.miqu.service;

/**
 * 管理员操作日志。
 *
 * <p>采用**显式调用**而不是 AOP 切面：后台管理只有寥寥几个写操作，
 * 显式调用一行 `adminLogService.record(...)` 比"注解 + 切面 + 参数解析"更好读，
 * 也更容易调试——切面里一旦参数取错，排查成本远高于这一行。
 *
 * <p>记录内容严格限制：只写操作类型、目标与摘要，**绝不写入密码、Token 等敏感信息**。
 */
public interface AdminLogService {

    String OP_DISABLE_USER = "DISABLE_USER";
    String OP_ENABLE_USER = "ENABLE_USER";
    String OP_DELETE_POST = "DELETE_POST";
    String OP_DELETE_COMMENT = "DELETE_COMMENT";
    String OP_HANDLE_REPORT = "HANDLE_REPORT";

    /**
     * 记录一次管理员操作。
     *
     * @param adminId       操作人
     * @param operationType 操作类型，取值见本接口常量
     * @param targetType    目标类型，见 {@link com.miqu.common.enums.TargetTypeEnum}
     * @param targetId      目标 ID，可为 null
     * @param detail        操作摘要
     */
    void record(Long adminId, String operationType, Integer targetType, Long targetId, String detail);
}
