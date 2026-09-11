package com.miqu.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.miqu.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件：必须显式指定 DbType，否则方言探测在部分场景下会失效
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);   // 兜底：即使上层漏了 size 校验也不会拖垮库
        pagination.setOverflow(false);  // 页码超出总页数时返回空列表，而不是回到第一页
        interceptor.addInnerInterceptor(pagination);

        // 防全表更新/删除：UPDATE / DELETE 未带 WHERE 条件时直接抛异常。
        // 后台管理里的批量操作一旦漏写条件就是全表事故，这里做最后一道防线。
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
