package com.yuexin.bmp.bi.service;

import com.yuexin.BusinessException;
import com.yuexin.PageParams;
import com.yuexin.PageResponse;
import com.yuexin.bmp.bi.common.PostpaidCustomerReport;
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
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 后付费客户跟踪报表(PostpaidCustomerReport)表服务接口
 *
 * @author lxf
 * @since 2024-06-05 14:21:19
 */
@Validated
public interface PostpaidCustomerReportService {

    @ApiOperation(value = "查询")
    PageResponse<PostpaidCustomerReport> queryTotal(QueryParams params) throws BusinessException;

    @ApiOperation(value = "查询")
    PageResponse<PostpaidCustomerReport> query(QueryParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "PostpaidCustomerReportService.QueryParams")
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
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业编码")
        private List<String> entCodes;
        @ApiModelProperty("企业编码")
        private List<String> entCodesBill;
        @ApiModelProperty("开始月份")
        private Date startMonth;
        @ApiModelProperty("结束月份")
        private Date endMonth;
        @ApiModelProperty("状态(1=已出账未确认,2=已确认未开票未回款,3=已确认已开票未回款,4=已确认未开票已回款,5=已确认已开票已汇款)")
        private Integer state;
        @ApiModelProperty("开始出账时间")
        private Date startOutTime;
        @ApiModelProperty("结束出账时间")
        private Date endOutTime;
        @ApiModelProperty("数据源ID 拆分后的父ID")
        private Long parentId;
    }

    @ApiOperation(value = "新增")
    void add(@Valid AddParams params) throws BusinessException;

    void batchAdd(@Valid List<AddParams> params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "PostpaidCustomerReportService.AddParams")
    class AddParams implements Serializable {
        @ApiModelProperty("ID")
        private Long id;
        @ApiModelProperty("客户ID")
        private Long customerId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业编码")
        private String entCode;
        @ApiModelProperty("金额")
        private BigDecimal amount;
        @ApiModelProperty("月份")
        private Date month;
        @ApiModelProperty("出账时间")
        private Date outTime;
        @ApiModelProperty("状态(1=已出账未确认,2=已确认未开票未回款,3=已确认已开票未回款,4=已确认未开票已回款,5=已确认已开票已汇款)")
        private Integer state;
        @ApiModelProperty("时间")
        private Date time;
        @ApiModelProperty("数据源ID,CustomerStatement的ID")
        private Long sourceId;
        @ApiModelProperty("数据源ID 拆分后的父ID")
        private Long parentId;
    }

    @ApiOperation(value = "修改")
    void modify(@Valid ModifyParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "PostpaidCustomerReportService.ModifyParams")
    class ModifyParams implements Serializable {
        @ApiModelProperty("数据源ID")
        @NotEmpty
        private List<Long> sourceIds;
        @ApiModelProperty("状态(1=已出账未确认,2=已确认未开票未回款,3=已确认已开票未回款,4=已确认未开票已回款,5=已确认已开票已回款)")
        private Integer state;
    }

    @ApiOperation(value = "修改")
    void modifyTime(@Valid ModifyTimeParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "PostpaidCustomerReportService.ModifyTimeParams")
    class ModifyTimeParams implements Serializable {
        @ApiModelProperty("数据源ID")
        @NotEmpty
        private List<Long> sourceIds;
    }

    @ApiOperation(value = "查询")
    List<PostpaidCustomerReport> queryByState() throws BusinessException;

    @ApiOperation(value = "查询")
    List<PostpaidCustomerReport> queryChildren(ChildParams params) throws BusinessException;

    @Getter
    @Setter
    @Accessors(chain = true)
    @ApiModel(value = "PostpaidCustomerReportService.ChildParams")
    class ChildParams implements Serializable {
        @ApiModelProperty("平台")
        private String platform;
        @ApiModelProperty("企业")
        private String entCode;
        @ApiModelProperty("客户主数据")
        private Long customerId;
        @ApiModelProperty("业务主体ID")
        private Long businessBodyId;
        @ApiModelProperty("月份")
        private Date month;
        @ApiModelProperty("数据源ID 拆分后的父ID")
        private Long parentId;
    }

    BeanConverter BeanUtils = Mappers.getMapper(BeanConverter.class);

    @Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    interface BeanConverter {
        PostpaidCustomerReport copyProperties(AddParams params);
    }
}
