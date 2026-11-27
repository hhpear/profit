package com.yuexin.bmp.bi.business.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yuexin.mybatis.mysql.InsertBatch;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@MapperScan(basePackages = {"com.yuexin.bmp.bi.business.repo"})
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public GlobalConfig globalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setSqlInjector(insertBatchOnConflictInjector());
        return globalConfig;
    }

    @Bean
    public InsertBatchOnConflictInjector insertBatchOnConflictInjector() {
        return new InsertBatchOnConflictInjector();
    }

    public static class InsertBatchOnConflictInjector extends DefaultSqlInjector {

        @Override
        public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
            List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
            methodList.add(new InsertBatch(t -> t.getFieldFill() != FieldFill.UPDATE));
            return methodList;
        }
    }
}