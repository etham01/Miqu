package com.miqu.service.impl;

import com.miqu.entity.AdminOperationLog;
import com.miqu.mapper.AdminOperationLogMapper;
import com.miqu.service.AdminLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLogServiceImpl implements AdminLogService {

    private static final int DETAIL_MAX = 500;

    private final AdminOperationLogMapper adminOperationLogMapper;

    @Override
    public void record(Long adminId, String operationType, Integer targetType, Long targetId, String detail) {
        if (adminId == null || !StringUtils.hasText(operationType)) {
            // 记日志失败不应影响主流程，但也不能静默吞掉配置错误
            log.warn("跳过无效的管理员操作日志: adminId={}, operationType={}", adminId, operationType);
            return;
        }

        AdminOperationLog entity = new AdminOperationLog();
        entity.setAdminId(adminId);
        entity.setOperationType(operationType);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setDetail(truncate(detail));
        entity.setIp(resolveClientIp());

        adminOperationLogMapper.insert(entity);
        log.info("管理员操作: adminId={}, type={}, target=({}, {}), ip={}",
                adminId, operationType, targetType, targetId, entity.getIp());
    }

    private String truncate(String detail) {
        if (!StringUtils.hasText(detail)) {
            return "";
        }
        String trimmed = detail.trim();
        return trimmed.length() <= DETAIL_MAX ? trimmed : trimmed.substring(0, DETAIL_MAX);
    }

    /**
     * 取客户端 IP。
     *
     * <p>优先取 {@code X-Forwarded-For} 的第一段（部署在反向代理后面时
     * {@code getRemoteAddr()} 拿到的会是代理的地址）。
     * 该头部可被伪造，因此只用于审计参考，不作为鉴权依据。
     */
    private String resolveClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }
}
