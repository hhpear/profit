package com.yuexin.platform.notice.param;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.annotation.JSONField;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class ElementTable extends Element implements Serializable {

    private final static String TABLE = "table";

    @ApiModelProperty("每页最大展示的数据行数。支持[1,10]整数。默认值 5")
    @JSONField(name = "page_size")
    private Integer pageSize = 10;

    @ApiModelProperty("表格的行高")
    @JSONField(name = "row_height")
    private String rowHeight = "low";

    @ApiModelProperty("表格的行高")
    @JSONField(name = "header_style")
    private HeaderStyle headerStyle = new HeaderStyle();

    @ApiModelProperty("列对象数组")
    private List<TableColumn> columns;

    @ApiModelProperty("行对象数组")
    private JSONArray rows;

    public ElementTable(List<TableColumn> columns, JSONArray rows) {
        super(TABLE);
        this.columns = columns;
        this.rows = rows;
    }
}
