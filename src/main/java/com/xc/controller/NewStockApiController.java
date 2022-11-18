package com.xc.controller;

import com.alibaba.fastjson.JSON;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.xc.common.ServerResponse;
import com.xc.pojo.NewList;
import com.xc.service.INewListService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 新股接口
 */
@Controller
@RequestMapping({"/api/newstock/"})
@Api("新股数据")
public class NewStockApiController {


    @Autowired
    INewListService iNewListService;

    /*新股日历数据初始化*/
    @PostMapping({"init.do"})
    @ResponseBody
    public ServerResponse newStockInit() {
        try {
            iNewListService.getNewStockListTask();
        } catch (Exception e) {
            e.printStackTrace();
            return ServerResponse.createByErrorMsg(e.getMessage());
        }
        return ServerResponse.createBySuccess();
    }


    @ApiOperation("今日申购")
    @PostMapping({"currentApplyList.do"})
    @ResponseBody
    public ServerResponse currentApply() {
        return  ServerResponse.createBySuccess(setType(iNewListService.getCurrentDayStockList()));
    }

    @ApiOperation("根据代码获取新股详情")
    @PostMapping({"currentApplyDetail.do"})
    @ResponseBody
    public ServerResponse currentApplyDetail(String code) {
        return  ServerResponse.createBySuccess(iNewListService.getByCode(code));
    }

    @ApiOperation("今日上市")
    @PostMapping({"currentShowList.do"})
    @ResponseBody
    public ServerResponse currentShow() {
        return  ServerResponse.createBySuccess(setType(iNewListService.getNewShowStockList()));
    }

    @ApiOperation("即将发布")
    @PostMapping({"upcommingList.do"})
    @ResponseBody
    public ServerResponse upcomming() {
        return  ServerResponse.createBySuccess(setType(iNewListService.getUpcomingSubscription()));
    }

    @ApiOperation("今日公布中签的新股")
    @PostMapping({"winShowList.do"})
    @ResponseBody
    public ServerResponse winShow() {
        return  ServerResponse.createBySuccess(setType(iNewListService.getWinStockList()));
    }


    private List<Object> setType(List<NewList> newLists){
        ArrayList<Object> objects = new ArrayList<>();
        if(!CollectionUtils.isEmpty(newLists)){
               for(NewList newList:newLists){
                   Map<String, Object> innerMap = JSON.parseObject(JSON.toJSONString(newList)).getInnerMap();
                   String  code =String.valueOf(innerMap.get("code")) ;
                   if(code.startsWith("00")){
                       innerMap.put("type","深");
                   }else if(code.startsWith("60")){
                       innerMap.put("type","沪");
                   }else if(code.startsWith("30")){
                       innerMap.put("type","创");
                   }else if(code.startsWith("68")){
                       innerMap.put("type","科");
                   }else {
                       innerMap.put("type","北");
                   }
                   objects.add(innerMap);
               }
               return objects;
           }
        return objects;
    }

}
