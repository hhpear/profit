package com.yuexin.bmp.bi.business.service.impl;

import com.yuexin.bmp.bi.business.repo.RebateReportRepo;
import com.yuexin.bmp.bi.common.RebateReport;
import com.yuexin.bmp.bi.service.RebateReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RebateReportServiceImplTest {

    @Test
    void returnsAffectedRowsWhenUpdatingApprovalState() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), RebateReport.class);
        RebateReportRepo repo = Mockito.mock(RebateReportRepo.class);
        Mockito.when(repo.update(Mockito.any(RebateReport.class), Mockito.any()))
                .thenReturn(2);
        RebateReportServiceImpl service = new RebateReportServiceImpl();
        ReflectionTestUtils.setField(service, "repo", repo);

        int affected = service.approveAndCount(new RebateReportService.ApproveParams()
                .setInstanceCode("instance-1")
                .setState(RebateReport.State.Canceled));

        assertEquals(2, affected);
        org.mockito.ArgumentCaptor<LambdaQueryWrapper> wrapper = org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        Mockito.verify(repo).update(Mockito.any(RebateReport.class), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("instanceCode"), sql);
        assertTrue(sql.contains("deleted"), sql);
        assertTrue(sql.contains("state"), sql);
    }
}
