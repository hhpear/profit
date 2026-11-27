package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.ChannelCostDetail;
import com.yuexin.bmp.bi.common.Cycle;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 通道成本明细(ChannelCostDetail)表服务接口
 *
 * @author lxf
 * @since 2024-02-03 18:51:15
 */
@Validated
public interface ChannelCostDetailService {

    @ApiOperation(value = "查询")
    PageResponse<ChannelCostDetail> query(QueryParams params) throws BusinessException;

    List<Map<String, Object>> queryStat(QueryParams params) throws BusinessException;

    ChannelCostDetail sum(QueryParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelCostDetailService.QueryParams")
    class QueryParams implements Serializable {
        @ApiModelProperty(value = "分页参数")
        private PageParams page;
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("客户ID")
        private List<Long> customerIds;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("业务主体ID")
        private List<Long> businessBodyIds;
        @ApiModelProperty("销售项目ID")
        private Long projectId;
        @ApiModelProperty("销售项目ID列表")
        private List<Long> projectIds;
        @ApiModelProperty("项目负责人ID")
        private Long projectLeaderId;
        @ApiModelProperty("项目负责人ID")
        private List<Long> projectLeaderIds;
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业编码")
        private String entCode;
        @ApiModelProperty("企业编码")
        private List<String> entCodes;
        @ApiModelProperty("平台和企业编码")
        private List<String> platformEntCodes;
        @ApiModelProperty("周期")
        private Cycle cycle;
        @ApiModelProperty("开始时间")
        private Date startTime;
        @ApiModelProperty("结束时间")
        private Date endTime;
        @ApiModelProperty("销售指标")
        private Boolean marketIndex;
        @ApiModelProperty("是否含税(true-含税, false-不含税)")
        private Boolean tax;
    }

    @ApiOperation(value = "新增")
    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "ChannelCostDetailService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("销售项目ID")
        private Long projectId;
        @ApiModelProperty("项目负责人ID")
        private Long projectLeaderId;
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业编码")
        private String entCode;
        @ApiModelProperty("通道ID")
        private Long channelId;
        @ApiModelProperty("周期")
        private Cycle cycle;
        @ApiModelProperty("时间")
        private Date time;
        @ApiModelProperty("提交数")
        private Long submitCount;
        @ApiModelProperty("成功数")
        private Long successCount;
        @ApiModelProperty("金额(含税)")
        private BigDecimal amount;
        @ApiModelProperty("销售金额")
        private BigDecimal marketAmount;
        @ApiModelProperty("销售金额(不含税)")
        private BigDecimal marketAmountUntax;
        @ApiModelProperty("运营金额")
        private BigDecimal operateAmount;
        @ApiModelProperty("运营金额(不含税)")
        private BigDecimal operateAmountUntax;
        @ApiModelProperty("出账状态")
        private String state;
    }

    void batchDelete(DeleteParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    class DeleteParams implements Serializable {
        @ApiModelProperty("周期")
        private Cycle cycle;
        @ApiModelProperty("开始时间")
        private Date startTime;
        @ApiModelProperty("结束时间")
        private Date endTime;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        ChannelCostDetail copyProperties(AddParams params);

    }
}
