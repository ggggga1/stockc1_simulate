package com.xc.pojo;

import java.io.Serializable;
import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 *  新闻资讯
 * @author lr 2020-08-05
 */
@Data
public class SiteNews implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 新闻主键id
     */
    private Integer id;

    /**
     * 新闻类型：1、财经要闻，2、经济数据，3、全球股市，4、7*24全球，5、商品资讯，6、上市公司，7、全球央行
     */
    private Integer type;

    /**
     * 新闻标题
     */
    private String title;

    /**
     * 来源id
     */
    private String sourceId;

    /**
     * 来源名称
     */
    private String sourceName;

    /**
     * 浏览量
     */
    private Integer views;

    /**
     * 状态：1、启用，0、停用
     */
    private Integer status;

    /**
     * 显示时间
     */
    private Date showTime;

    /**
     * 添加时间
     */
    private Date addTime;

    /**
     * 修改时间
     */
    private Date updateTime;

    /**
     * 图片地址
     */
    private String imgurl;

    /**
     * 描述
     */
    private String description;

    /**
     * 新闻内容
     */
    private String content;

    public SiteNews() {
    }

}
