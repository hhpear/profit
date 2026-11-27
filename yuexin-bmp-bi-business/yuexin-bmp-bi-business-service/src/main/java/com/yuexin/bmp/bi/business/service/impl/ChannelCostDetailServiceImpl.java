package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.ChannelCostDetailRepo;
import com.yuexin.bmp.bi.common.ChannelCostDetail;
import com.yuexin.bmp.bi.service.ChannelCostDetailService;
import com.yuexin.mybatis.PageUtils;
import com.yuexin.sequence.service.UidGenerator;
import com.yuexin.util.NullHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 平台利润明细(ChannelCostDetail)表服务实现类
 *
 * @author lxf
 * @since 2024-02-03 18:51:15
 */
@Slf4j
@DubboService
public class ChannelCostDetailServiceImpl implements ChannelCostDetailService, NullHandler {
    @Resource
    private ChannelCostDetailRepo repo;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<ChannelCostDetail> query(QueryParams params) throws BusinessException {
        if (params.getPage() != null) {
            Page<ChannelCostDetail> page = this.repo.selectPage(PageUtils.convert(params.getPage()), buildQuery(params));
            return PageUtils.convert(page);
        } else {
            List<ChannelCostDetail> list = this.repo.selectList(buildQuery(params));
            return new PageResponse<>((long) list.size(), list);
        }
    }

    @Override
    public List<Map<String, Object>> queryStat(QueryParams params) throws BusinessException {
        // 判断是否使用含税金额，默认使用含税金额
        boolean useTax = params.getTax() == null || params.getTax();

        if (params.getMarketIndex() != null && params.getMarketIndex()) {
            // 销售指标：根据 tax 字段选择含税或不含税的销售金额
            String amountField = useTax ? "market_amount" : "market_amount_untax";
            return this.repo.selectMaps(buildQuery(params)
                    .select("SUM(" + amountField + ") as money"));
        } else {
            // 运营指标：根据 tax 字段选择含税或不含税的运营金额
            String amountField = useTax ? "operate_amount" : "operate_amount_untax";
            return this.repo.selectMaps(buildQuery(params)
                    .select("SUM(" + amountField + ") as money"));
        }
    }

    @Override
    public ChannelCostDetail sum(QueryParams params) throws BusinessException {
        List<Map<String, Object>> maps = this.repo.selectMaps(buildQuery(params)
                .select("SUM(submit_count) AS submit_count",
                        "SUM(success_count) AS success_count"));
        return maps.stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(ChannelCostDetail.class))
                .collect(Collectors.toList()).get(0);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<ChannelCostDetail> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            ChannelCostDetail report = BeanUtils.copyProperties(params.get(i));
            report.setId(ids.get(i));
            list.add(report);
        });
        this.repo.insertBatch(list);
    }

    @Override
    public void batchDelete(DeleteParams params) throws BusinessException {
        repo.delete(new QueryWrapper<ChannelCostDetail>()
                .eq("cycle", params.getCycle())
                .ge("time", params.getStartTime())
                .le("time", params.getEndTime()));
    }


    private QueryWrapper<ChannelCostDetail> buildQuery(QueryParams params) {
        QueryWrapper<ChannelCostDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(params.getCustomerId() != null, "customer_id", params.getCustomerId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getCustomerIds()), "customer_id", params.getCustomerIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, "business_body_id", params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), "business_body_id", params.getBusinessBodyIds());
        queryWrapper.eq(params.getProjectId() != null, "project_id", params.getProjectId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectIds()), "project_id", params.getProjectIds());
        queryWrapper.eq(params.getProjectLeaderId() != null, "project_leader_id", params.getProjectLeaderId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getProjectLeaderIds()), "project_leader_id", params.getProjectLeaderIds());
        queryWrapper.eq(StringUtils.isNotBlank(params.getPlatform()), "platform", params.getPlatform());
        queryWrapper.eq(StringUtils.isNotBlank(params.getEntCode()), "ent_code", params.getEntCode());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getEntCodes()), "ent_code", params.getEntCodes());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getPlatformEntCodes()), "CONCAT(platform, ent_code)", params.getPlatformEntCodes());
        queryWrapper.eq(params.getCycle() != null, "cycle", params.getCycle());
        queryWrapper.ge(params.getStartTime() != null, "time", params.getStartTime());
        queryWrapper.lt(params.getEndTime() != null, "time", params.getEndTime());
        return queryWrapper;
    }
}
