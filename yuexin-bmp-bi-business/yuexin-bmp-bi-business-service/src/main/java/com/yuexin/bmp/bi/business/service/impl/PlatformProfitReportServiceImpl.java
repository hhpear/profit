package com.yuexin.bmp.bi.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.PlatformProfitReportRepo;
import com.yuexin.bmp.bi.common.PlatformProfitReport;
import com.yuexin.bmp.bi.common.StatSource;
import com.yuexin.bmp.bi.common.StatSourceResolver;
import com.yuexin.bmp.bi.service.PlatformProfitReportService;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 平台利润报表(PlatformProfitReport)表服务实现类
 *
 * @author lxf
 * @since 2024-01-23 10:26:59
 */
@Slf4j
@DubboService
public class PlatformProfitReportServiceImpl implements PlatformProfitReportService {
    @Resource
    private PlatformProfitReportRepo repo;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<PlatformProfitReport> query(QueryParams params) throws BusinessException {
        if (params.getPage() != null) {
            Page<PlatformProfitReport> page = this.repo.selectPage(PageUtils.convert(params.getPage()), buildQuery(params));
            return PageUtils.convert(page);
        } else {
            List<PlatformProfitReport> list = this.repo.selectList(buildQuery(params));
            return new PageResponse<>(Long.parseLong(String.valueOf(list.size())), list);
        }
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<PlatformProfitReport> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            PlatformProfitReport report = BeanUtils.copyProperties(params.get(i));
            report.setId(ids.get(i));
            list.add(report);
        });
        this.repo.insertBatch(list);
    }

    @Override
    public void batchDelete(DeleteParams params) throws BusinessException {
        repo.delete(new QueryWrapper<PlatformProfitReport>()
                .eq("cycle", params.getCycle())
                .eq("source", params.getSource())
                .ge("time", params.getStartTime())
                .le("time", params.getEndTime()));
    }

    private QueryWrapper<PlatformProfitReport> buildQuery(QueryParams params) {
        StatSource source = StatSourceResolver.resolve(params.getCycle(), params.getSource(), null, params.getStartTime());
        QueryWrapper<PlatformProfitReport> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(Objects.nonNull(params.getCycle()), "cycle", params.getCycle());
        queryWrapper.eq(source != null, "source", source);
        queryWrapper.ge(params.getStartTime() != null, "time", params.getStartTime());
        queryWrapper.lt(params.getEndTime() != null, "time", params.getEndTime());
        return queryWrapper;
    }
}
