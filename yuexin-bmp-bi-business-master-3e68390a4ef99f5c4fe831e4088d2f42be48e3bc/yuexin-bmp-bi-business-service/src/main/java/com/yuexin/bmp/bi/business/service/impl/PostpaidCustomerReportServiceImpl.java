package com.yuexin.bmp.bi.business.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuexin.BusinessException;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.business.repo.PostpaidCustomerReportRepo;
import com.yuexin.bmp.bi.common.PostpaidCustomerReport;
import com.yuexin.bmp.bi.service.PostpaidCustomerReportService;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 后付费客户跟踪报表(PostpaidCustomerReport)表服务实现类
 *
 * @author lxf
 * @since 2024-06-05 14:21:21
 */
@Slf4j
@DubboService
public class PostpaidCustomerReportServiceImpl implements PostpaidCustomerReportService, NullHandler {
    @Resource
    private PostpaidCustomerReportRepo repo;
    @DubboReference
    private UidGenerator uidGenerator;

    @Override
    public PageResponse<PostpaidCustomerReport> queryTotal(QueryParams params) throws BusinessException {
        Page<Map<String, Object>> mapPage = this.repo.selectMapsPage(PageUtils.convert(params.getPage()), buildQuery(params)
                .isNull("parent_id")
                .groupBy("platform,ent_code,customer_id,business_body_id")
                .select("MAX(id) AS id,platform,ent_code,customer_id,business_body_id,SUM(amount) AS amount,MAX(time) AS time"));
        Page<PostpaidCustomerReport> page = new Page<>(mapPage.getCurrent(), mapPage.getSize(), mapPage.getTotal());
        page.setRecords(mapPage.getRecords().stream().map(map -> new JSONObject(nonNull(map)).toJavaObject(PostpaidCustomerReport.class)).collect(Collectors.toList()));
        return PageUtils.convert(page);
    }

    @Override
    public PageResponse<PostpaidCustomerReport> query(QueryParams params) throws BusinessException {
        if (params.getPage() != null) {
            Page<PostpaidCustomerReport> page = this.repo.selectPage(PageUtils.convert(params.getPage()), buildQuery(params));
            return PageUtils.convert(page);
        } else {
            List<PostpaidCustomerReport> list = this.repo.selectList(buildQuery(params));
            return new PageResponse<>((long) list.size(), list);
        }
    }

    @Override
    public void add(AddParams params) throws BusinessException {
        PostpaidCustomerReport postpaidCustomerReport = BeanUtils.copyProperties(params);
        postpaidCustomerReport.setId(uidGenerator.generate());
        this.repo.insert(postpaidCustomerReport);
    }

    @Override
    public void batchAdd(List<AddParams> params) throws BusinessException {
        List<Long> ids = uidGenerator.batchGenerate(params.size());
        List<PostpaidCustomerReport> list = new ArrayList<>();
        IntStream.range(0, params.size()).forEach(i -> {
            PostpaidCustomerReport report = BeanUtils.copyProperties(params.get(i));
            report.setId(ids.get(i));
            list.add(report);
        });
        this.repo.insertBatch(list);
    }

    @Override
    public void modify(ModifyParams params) throws BusinessException {
        LambdaUpdateWrapper<PostpaidCustomerReport> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(PostpaidCustomerReport::getSourceId, params.getSourceIds());
        updateWrapper.set(PostpaidCustomerReport::getState, params.getState());
        this.repo.update(null, updateWrapper);
    }

    @Override
    public void modifyTime(ModifyTimeParams params) throws BusinessException {
        this.repo.update(new PostpaidCustomerReport().setTime(new Date()),
                new LambdaQueryWrapper<PostpaidCustomerReport>().in(PostpaidCustomerReport::getSourceId, params.getSourceIds()));
    }

    @Override
    public List<PostpaidCustomerReport> queryByState() throws BusinessException {
        return this.repo.selectList(new LambdaQueryWrapper<PostpaidCustomerReport>().ne(PostpaidCustomerReport::getState, 5));
    }

    @Override
    public List<PostpaidCustomerReport> queryChildren(ChildParams params) throws BusinessException {
        return this.repo.selectList(new LambdaQueryWrapper<PostpaidCustomerReport>()
                .eq(StringUtils.isNotBlank(params.getPlatform()), PostpaidCustomerReport::getPlatform, params.getPlatform())
                .eq(StringUtils.isNotBlank(params.getEntCode()), PostpaidCustomerReport::getEntCode, params.getEntCode())
                .eq(params.getCustomerId() != null, PostpaidCustomerReport::getCustomerId, params.getCustomerId())
                .eq(params.getBusinessBodyId() != null, PostpaidCustomerReport::getBusinessBodyId, params.getBusinessBodyId())
                .eq(params.getMonth() != null, PostpaidCustomerReport::getMonth, params.getMonth())
                .eq(params.getParentId() != null, PostpaidCustomerReport::getParentId, params.getParentId())
                .isNotNull(PostpaidCustomerReport::getParentId));
    }

    private QueryWrapper<PostpaidCustomerReport> buildQuery(QueryParams params) {
        QueryWrapper<PostpaidCustomerReport> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(params.getCustomerId() != null, "customer_id", params.getCustomerId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getCustomerIds()), "customer_id", params.getCustomerIds());
        queryWrapper.eq(params.getBusinessBodyId() != null, "business_body_id", params.getBusinessBodyId());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getBusinessBodyIds()), "business_body_id", params.getBusinessBodyIds());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getEntCodes()), "ent_code", params.getEntCodes());
        queryWrapper.in(CollectionUtils.isNotEmpty(params.getEntCodesBill()), "ent_code", params.getEntCodesBill());
        queryWrapper.ge(params.getStartMonth() != null, "month", params.getStartMonth());
        queryWrapper.le(params.getEndMonth() != null, "month", params.getEndMonth());
        queryWrapper.eq(params.getState() != null, "state", params.getState());
        queryWrapper.ge(params.getStartOutTime() != null, "out_time", params.getStartOutTime());
        queryWrapper.le(params.getEndOutTime() != null, "out_time", params.getEndOutTime());
        queryWrapper.eq(params.getParentId() != null, "parent_id", params.getParentId());
        return queryWrapper;
    }
}
