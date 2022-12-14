package com.xc.service.impl;

import com.github.pagehelper.StringUtil;
import com.xc.dao.*;
import com.xc.pojo.*;
import com.xc.service.*;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.xc.common.ServerResponse;
import com.xc.utils.DateTimeUtil;
import com.xc.utils.KeyUtils;
import com.xc.utils.stock.BuyAndSellUtils;
import com.xc.utils.stock.GeneratePosition;
import com.xc.utils.stock.GetStayDays;
import com.xc.utils.stock.qq.QqMiniData;
import com.xc.utils.stock.qq.QqStockApi;
import com.xc.utils.stock.sina.SinaStockApi;
import com.xc.vo.agent.AgentIncomeVO;
import com.xc.vo.position.AdminPositionVO;
import com.xc.vo.position.AgentPositionVO;
import com.xc.vo.position.PositionProfitVO;
import com.xc.vo.position.PositionVO;
import com.xc.vo.position.UserPositionVO;
import com.xc.vo.stock.StockListVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Calendar;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service("iUserPositionService")
public class UserPositionServiceImpl implements IUserPositionService {

    private static final Logger log = LoggerFactory.getLogger(UserPositionServiceImpl.class);

    @Autowired
    UserPositionMapper userPositionMapper;

    @Autowired
    IUserService iUserService;

    @Autowired
    ISiteSettingService iSiteSettingService;

    @Autowired
    ISiteSpreadService iSiteSpreadService;

    @Autowired
    IStockService iStockService;

    @Autowired
    UserMapper userMapper;

    @Autowired
    UserCashDetailMapper userCashDetailMapper;
    @Autowired
    IAgentUserService iAgentUserService;
    @Autowired
    AgentUserMapper agentUserMapper;
    @Autowired
    SiteTaskLogMapper siteTaskLogMapper;
    @Autowired
    StockMapper stockMapper;
    @Autowired
    AgentAgencyFeeMapper agentAgencyFeeMapper;
    @Autowired
    IAgentAgencyFeeService iAgentAgencyFeeService;
    @Autowired
    ISiteProductService iSiteProductService;
    @Autowired
    INewListService iNewListService;

    @Autowired
    FundsApplyMapper fundsApplyMapper;


    private boolean isTopBoard(Stock stock){
        //??????????????????????????????
        String stockPlate = stock.getStockPlate();
        boolean isTopBoard=false;
        if(!StringUtils.isEmpty(stockPlate)){
            if(stockPlate.equals("?????????")){
                isTopBoard=true;
            }
        }
        //return isTopBoard;
        return true;
    }


    @Transactional
    public ServerResponse buy(Integer stockId, Integer buyNum, Integer buyType, Integer lever, HttpServletRequest request) throws Exception {

        // ?????????????????????
        Date today = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(today);
        int weekday = c.get(Calendar.DAY_OF_WEEK);
        if (weekday == 1) {
            return ServerResponse.createByErrorMsg("?????????????????????");
        }
        if (weekday == 7) {
            return ServerResponse.createByErrorMsg("?????????????????????");
        }

        /*????????????????????????*/
        SiteProduct siteProduct = iSiteProductService.getProductSetting();
        User user = this.iUserService.getCurrentRefreshUser(request);
        if (siteProduct.getRealNameDisplay() && (StringUtils.isBlank(user.getRealName()) || StringUtils.isBlank(user.getIdCard()))) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }
        BigDecimal user_enable_amt = user.getEnableAmt();
        log.info("?????? {} ???????????????id = {} ????????? = {} , ?????? = {} , ?????? = {}", new Object[]{user
                .getId(), stockId, buyNum, buyType, lever});
        if (siteProduct.getRealNameDisplay() && user.getIsLock().intValue() == 1) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }
        if(siteProduct.getHolidayDisplay()){
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }

        SiteSetting siteSetting = this.iSiteSettingService.getSiteSetting();
        if (siteSetting == null) {
            log.error("???????????????????????????????????????");
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }

        String am_begin = siteSetting.getTransAmBegin();
        String am_end = siteSetting.getTransAmEnd();
        String pm_begin = siteSetting.getTransPmBegin();
        String pm_end = siteSetting.getTransPmEnd();
        boolean am_flag = BuyAndSellUtils.isTransTime(am_begin, am_end);
        boolean pm_flag = BuyAndSellUtils.isTransTime(pm_begin, pm_end);
        log.info("??????????????????????????? = {} ??????????????????????????? = {}", Boolean.valueOf(am_flag), Boolean.valueOf(pm_flag));

        if (!am_flag && !pm_flag) {
            return ServerResponse.createByErrorMsg("????????????????????????????????????");
        }

        Stock stock = null;
        ServerResponse stock_res = this.iStockService.findStockById(stockId);
        if (!stock_res.isSuccess()) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }
        stock = (Stock) stock_res.getData();

        if (stock.getIsLock().intValue() != 0) {
            return ServerResponse.createByErrorMsg("???????????????????????????????????????");
        }

        List dbPosition = findPositionByStockCodeAndTimes(siteSetting.getBuySameTimes().intValue(), stock
                .getStockCode(), user.getId());
        if (dbPosition.size() >= siteSetting.getBuySameNums().intValue()) {
            return ServerResponse.createByErrorMsg("????????????," + siteSetting.getBuySameTimes() + "???????????????????????????????????????" + siteSetting
                    .getBuySameNums() + "???");
        }

        Integer transNum = findPositionNumByTimes(siteSetting.getBuyNumTimes().intValue(), user.getId());
        if (transNum.intValue() / 100 >= siteSetting.getBuyNumLots().intValue()) {
            return ServerResponse.createByErrorMsg("????????????," + siteSetting
                    .getBuyNumTimes() + "?????????????????????" + siteSetting.getBuyNumLots() + "???");
        }

        if (buyNum.intValue() < siteSetting.getBuyMinNum().intValue()) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????" + siteSetting
                    .getBuyMinNum() + "???");
        }
        if (buyNum.intValue() > siteSetting.getBuyMaxNum().intValue()) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????" + siteSetting
                    .getBuyMaxNum() + "???");
        }


        StockListVO stockListVO = SinaStockApi.assembleStockListVO(SinaStockApi.getSinaStock(stock.getStockGid()));
        BigDecimal now_price = new BigDecimal(stockListVO.getNowPrice());

        if (now_price.compareTo(new BigDecimal("0")) == 0) {
            return ServerResponse.createByErrorMsg("??????0??????????????????");
        }


        double stock_crease = stockListVO.getHcrate().doubleValue();


        BigDecimal maxRisePercent = new BigDecimal("0");
        if (stock.getStockPlate() != null||stock.getStockCode().startsWith("688")) {
            maxRisePercent = new BigDecimal("0.2");
            log.info("??????????????????");
        } else {
            maxRisePercent = new BigDecimal("0.1");
            log.info("?????????A??????");
        }

        if(stockListVO.getName().startsWith("ST") || stockListVO.getName().endsWith("???")){
            return ServerResponse.createByErrorMsg("ST?????????????????????????????????");
        }

        BigDecimal zsPrice = new BigDecimal(stockListVO.getPreclose_px());

        BigDecimal ztPrice = zsPrice.multiply(maxRisePercent).add(zsPrice);
        ztPrice = ztPrice.setScale(2, 4);
        BigDecimal chaPrice = ztPrice.subtract(zsPrice);

        BigDecimal ztRate = chaPrice.multiply(new BigDecimal("100")).divide(zsPrice, 2, 4);

        log.info("??????????????? = {} % , ???????????? = {} %", Double.valueOf(stock_crease), ztRate);
        if ((new BigDecimal(String.valueOf(stock_crease))).compareTo(ztRate) == 0 && buyType
                .intValue() == 0&&!isTopBoard(stock)) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }


        if (stock.getStockPlate() == null || StringUtils.isEmpty(stock.getStockPlate())) {

            int maxcrease = siteSetting.getCreaseMaxPercent().intValue();
            if (stock_crease > 0.0D &&
                    stock_crease >= maxcrease) {
                return ServerResponse.createByErrorMsg("?????????????????????????????????:" + stock_crease + ",??????????????????:" + maxcrease);
            }


            if (stock_crease < 0.0D &&
                    -stock_crease > maxcrease) {
                return ServerResponse.createByErrorMsg("?????????????????????????????????:" + stock_crease + ",??????????????????:" + maxcrease);

            }

        } else if("??????".equals(stock.getStockPlate())) {

            int maxcrease = siteSetting.getCyCreaseMaxPercent().intValue();
            if (stock_crease > 0.0D &&
                    stock_crease >= maxcrease) {
                return ServerResponse.createByErrorMsg("????????????????????????????????????:" + stock_crease + ",??????????????????:" + maxcrease);
            }


            if (stock_crease < 0.0D &&
                    -stock_crease > maxcrease) {
                return ServerResponse.createByErrorMsg("????????????????????????????????????:" + stock_crease + ",??????????????????:" + maxcrease);
            }
        } else {

            int maxcrease = siteSetting.getKcCreaseMaxPercent().intValue();
            if (stock_crease > 0.0D &&
                    stock_crease >= maxcrease) {
                return ServerResponse.createByErrorMsg("????????????????????????????????????:" + stock_crease + ",??????????????????:" + maxcrease);
            }


            if (stock_crease < 0.0D &&
                    -stock_crease > maxcrease) {
                return ServerResponse.createByErrorMsg("????????????????????????????????????:" + stock_crease + ",??????????????????:" + maxcrease);
            }
        }


        ServerResponse serverResponse = this.iStockService.selectRateByDaysAndStockCode(stock
                .getStockCode(), siteSetting.getStockDays().intValue());
        if (!serverResponse.isSuccess()) {
            return serverResponse;
        }
        BigDecimal daysRate = (BigDecimal) serverResponse.getData();
        log.info("?????? {} ??? {} ?????? ?????? {} , ??????????????? = {}", new Object[]{stock.getStockCode(), siteSetting
                .getStockDays(), daysRate, siteSetting.getStockRate()});

        if (daysRate != null &&
                siteSetting.getStockRate().compareTo(daysRate) == -1) {
            return serverResponse.createByErrorMsg(siteSetting.getStockDays() + "?????????????????? " + siteSetting
                    .getStockRate() + "????????????");
        }


        //BigDecimal buy_amt = now_price.multiply(new BigDecimal(buyNum.intValue())).divide(new BigDecimal(lever.intValue())).setScale(2, 4);
        //????????????=????????????*????????????
        BigDecimal buy_amt = now_price.multiply(new BigDecimal(buyNum.intValue()));


        //BigDecimal buy_amt_autual = now_price.multiply(new BigDecimal(buyNum.intValue())).divide(new BigDecimal(lever.intValue()), 2, 4);
        //??????????????????
        BigDecimal buy_amt_autual = buy_amt.divide(new BigDecimal(lever.intValue()), 2, 4);


        int compareInt = buy_amt_autual.compareTo(new BigDecimal(siteSetting.getBuyMinAmt().intValue()));
        if (compareInt == -1) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????" + siteSetting
                    .getBuyMinAmt() + "???");
        }


        BigDecimal max_buy_amt = user_enable_amt.multiply(siteSetting.getBuyMaxAmtPercent());
        int compareCwInt = buy_amt_autual.compareTo(max_buy_amt);
        if (compareCwInt == 1) {
            return ServerResponse.createByErrorMsg("??????????????????????????????????????????" + siteSetting
                    .getBuyMaxAmtPercent().multiply(new BigDecimal("100")) + "%");
        }


        int compareUserAmtInt = user_enable_amt.compareTo(buy_amt_autual);
        log.info("?????????????????? = {}  ?????????????????? =  {}", user_enable_amt, buy_amt_autual);
        log.info("?????? ???????????? ??? ?????? ???????????? =  {}", Integer.valueOf(compareUserAmtInt));
        if (compareUserAmtInt == -1) {
            return ServerResponse.createByErrorMsg("???????????????????????????????????????" + buy_amt_autual + "???");
        }

//        if (user.getUserIndexAmt().compareTo(new BigDecimal("0")) == -1) {
//            return ServerResponse.createByErrorMsg("??????????????????????????????0");
//        }
//        if (user.getUserFutAmt().compareTo(new BigDecimal("0")) == -1) {
//            return ServerResponse.createByErrorMsg("??????????????????????????????0");
//        }

        UserPosition userPosition = new UserPosition();
        userPosition.setPositionType(user.getAccountType());
        userPosition.setPositionSn(KeyUtils.getUniqueKey());
        userPosition.setUserId(user.getId());
        userPosition.setNickName(user.getRealName());
        userPosition.setAgentId(user.getAgentId());
        userPosition.setStockCode(stock.getStockCode());
        userPosition.setStockName(stock.getStockName());
        userPosition.setStockGid(stock.getStockGid());
        userPosition.setStockSpell(stock.getStockSpell());
        userPosition.setBuyOrderId(GeneratePosition.getPositionId());
        userPosition.setBuyOrderTime(new Date());
        userPosition.setBuyOrderPrice(now_price);
        userPosition.setOrderDirection((buyType.intValue() == 0) ? "??????" : "??????");

        userPosition.setOrderNum(buyNum);



        if (stock.getStockPlate() != null) {
            userPosition.setStockPlate(stock.getStockPlate());
        }


        userPosition.setIsLock(Integer.valueOf(0));


        userPosition.setOrderLever(lever);


        userPosition.setOrderTotalPrice(buy_amt);

        //?????????????????????
        BigDecimal stayFee = userPosition.getOrderTotalPrice().multiply(siteSetting.getStayFee());
        BigDecimal allStayFee = stayFee.multiply(new BigDecimal(1));
        userPosition.setOrderStayFee(allStayFee);
        userPosition.setOrderStayDays(1);


        BigDecimal buy_fee_amt = buy_amt.multiply(siteSetting.getBuyFee()).setScale(2, 4);
        log.info("?????????????????????????????????????????? * ???????????? = {}", buy_fee_amt);
        userPosition.setOrderFee(buy_fee_amt);


        BigDecimal buy_yhs_amt = buy_amt.multiply(siteSetting.getDutyFee()).setScale(2, 4);
        log.info("?????????????????????????????????????????? * ???????????? = {}", buy_yhs_amt);
        userPosition.setOrderSpread(buy_yhs_amt);

//        SiteSpread siteSpread = iSiteSpreadService.findSpreadRateOne(new BigDecimal(stock_crease), buy_amt, stock.getStockCode(), now_price);
          BigDecimal spread_rate_amt = new BigDecimal("0");
//        if(siteSpread != null){
//            spread_rate_amt = buy_amt.multiply(siteSpread.getSpreadRate()).setScale(2, 4);
//            log.info("?????????????????????????????????????????? * ?????????{}??? = {}", siteSpread.getSpreadRate(), spread_rate_amt);
//        } else{
//            log.info("?????????????????????????????????????????? * ?????????{}??? = {}", "????????????", spread_rate_amt);
//        }
        //?????????
        userPosition.setSpreadRatePrice(spread_rate_amt);
        BigDecimal profit_and_lose = new BigDecimal("0");
        userPosition.setProfitAndLose(profit_and_lose);


        BigDecimal all_profit_and_lose = profit_and_lose.subtract(buy_fee_amt).subtract(buy_yhs_amt).subtract(spread_rate_amt);
        userPosition.setAllProfitAndLose(all_profit_and_lose);


        userPosition.setOrderStayDays(Integer.valueOf(0));
        userPosition.setOrderStayFee(new BigDecimal("0"));

        int insertPositionCount = 0;
        this.userPositionMapper.insert(userPosition);
        insertPositionCount = userPosition.getId();
        if (insertPositionCount > 0) {
            //????????????????????????= ????????????-????????????-???????????????-?????????-?????????
//            BigDecimal reckon_enable = user_enable_amt.subtract(buy_amt_autual).subtract(buy_fee_amt).subtract(buy_yhs_amt).subtract(spread_rate_amt);
//            if(reckon_enable.compareTo(BigDecimal.ZERO)<0){
//                log.error("????????????????????????????????????????????????");
//                throw new Exception("????????????????????????????????????????????????");
//            }
            //????????????????????????= ????????????-???????????????
            BigDecimal reckon_enable = user_enable_amt.subtract(buy_amt_autual);
            user.setEnableAmt(reckon_enable);
            int updateUserCount = this.userMapper.updateByPrimaryKeySelective(user);
            if (updateUserCount > 0) {
                log.info("????????????????????????????????????????????????");
            } else {
                log.error("?????????????????????????????????????????????");
                throw new Exception("?????????????????????????????????????????????");
            }
            //??????????????????-???????????????
            iAgentAgencyFeeService.AgencyFeeIncome(1,userPosition.getPositionSn());
            log.info("????????????????????????????????????????????????");
        } else {
            log.error("?????????????????????????????????????????????");
            throw new Exception("?????????????????????????????????????????????");
        }

        return ServerResponse.createBySuccess("????????????");
    }

    public ServerResponse sell(String positionSn, int doType) throws Exception {
        log.info("???????????????????????? positionSn = {} ??? dotype = {}", positionSn, Integer.valueOf(doType));

        SiteSetting siteSetting = this.iSiteSettingService.getSiteSetting();
        if (siteSetting == null) {
            log.error("???????????????????????????????????????");
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }
        SiteProduct siteProduct = iSiteProductService.getProductSetting();

        if (doType != 0) {
            String am_begin = siteSetting.getTransAmBegin();
            String am_end = siteSetting.getTransAmEnd();
            String pm_begin = siteSetting.getTransPmBegin();
            String pm_end = siteSetting.getTransPmEnd();
            boolean am_flag = BuyAndSellUtils.isTransTime(am_begin, am_end);
            boolean pm_flag = BuyAndSellUtils.isTransTime(pm_begin, pm_end);
            log.info("??????????????????????????? = {} ??????????????????????????? = {}", Boolean.valueOf(am_flag), Boolean.valueOf(pm_flag));
            if (!am_flag && !pm_flag) {
                return ServerResponse.createByErrorMsg("????????????????????????????????????");
            }

            if(siteProduct.getHolidayDisplay()){
                return ServerResponse.createByErrorMsg("?????????????????????????????????");
            }

        }

        UserPosition userPosition = this.userPositionMapper.findPositionBySn(positionSn);
        if (userPosition == null) {
            return ServerResponse.createByErrorMsg("??????????????????????????????");
        }

        User user = this.userMapper.selectByPrimaryKey(userPosition.getUserId());
        /*????????????????????????*/

        if (siteProduct.getRealNameDisplay() && user.getIsLock().intValue() == 1) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }



        if (userPosition.getSellOrderId() != null) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }

        Stock stock = stockMapper.findStockByCode(userPosition.getStockCode());
        if(stock.getIsLock()!=0){
            if (1 == userPosition.getIsLock().intValue()) {
                return ServerResponse.createByErrorMsg("???????????? " + userPosition.getLockMsg());
            }
        }


        if (!DateTimeUtil.isCanSell(userPosition.getBuyOrderTime(), siteSetting.getCantSellTimes().intValue())) {
            return ServerResponse.createByErrorMsg(siteSetting.getCantSellTimes() + "?????????????????????");
        }

//        if (DateTimeUtil.sameDate(DateTimeUtil.getCurrentDate(),userPosition.getBuyOrderTime())) {
//            return ServerResponse.createByErrorMsg("?????????????????????????????????????????????");
//        }


        StockListVO stockListVO = SinaStockApi.assembleStockListVO(SinaStockApi.getSinaStock(userPosition.getStockGid()));

        BigDecimal now_price = new BigDecimal(stockListVO.getNowPrice());
        if (now_price.compareTo(new BigDecimal("0")) != 1) {
            log.error("?????? = {} ???????????? = {}", userPosition.getStockName(), now_price);
            return ServerResponse.createByErrorMsg("??????0?????????????????????????????????");
        }

        double stock_crease = stockListVO.getHcrate().doubleValue();

        BigDecimal zsPrice = new BigDecimal(stockListVO.getPreclose_px());

        BigDecimal ztPrice = zsPrice.multiply(new BigDecimal("0.1")).add(zsPrice);
        ztPrice = ztPrice.setScale(2, 4);
        BigDecimal chaPrice = ztPrice.subtract(zsPrice);

        BigDecimal ztRate = chaPrice.multiply(new BigDecimal("100")).divide(zsPrice, 2, 4);

        ztRate = ztRate.negate();
        log.info("????????????????????? = {} ???????????? = {}", Double.valueOf(stock_crease), ztRate);
        if ((new BigDecimal(String.valueOf(stock_crease))).compareTo(ztRate) == 0 && "??????"
                .equals(userPosition.getOrderDirection())) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }

        Integer buy_num = userPosition.getOrderNum();

        BigDecimal all_buy_amt = userPosition.getOrderTotalPrice();
        //BigDecimal all_sell_amt = now_price.multiply(new BigDecimal(buy_num.intValue())).divide(new BigDecimal(userPosition.getOrderLever())).setScale(2,4);
        BigDecimal all_sell_amt = now_price.multiply(new BigDecimal(buy_num.intValue()));

        BigDecimal profitLoss = new BigDecimal("0");
        if ("??????".equals(userPosition.getOrderDirection())) {
            log.info("???????????????{}", "???");

            profitLoss = all_sell_amt.subtract(all_buy_amt);
        } else {
            log.info("???????????????{}", "???");
            profitLoss = all_buy_amt.subtract(all_sell_amt);
        }
        log.info("??????????????? = {} , ??????????????? = {} , ?????? = {}", new Object[]{all_buy_amt, all_sell_amt, profitLoss});

        BigDecimal user_all_amt = user.getUserAmt();
        BigDecimal user_enable_amt = user.getEnableAmt();
        log.info("????????????????????? = {} , ?????? = {}", user_all_amt, user_enable_amt);

        BigDecimal buy_fee_amt = userPosition.getOrderFee();
        log.info("??????????????? = {}", buy_fee_amt);

        BigDecimal orderSpread = userPosition.getOrderSpread();
        log.info("????????? = {}", orderSpread);

        BigDecimal orderStayFee = userPosition.getOrderStayFee();
        log.info("????????? = {}", orderStayFee);

        BigDecimal spreadRatePrice = userPosition.getSpreadRatePrice();
        log.info("????????? = {}", spreadRatePrice);

        BigDecimal sell_fee_amt = all_sell_amt.multiply(siteSetting.getSellFee()).setScale(2, 4);
        log.info("??????????????? = {}", sell_fee_amt);

        //????????????= ???????????????+???????????????+?????????+?????????+?????????
        BigDecimal all_fee_amt = buy_fee_amt.add(sell_fee_amt).add(orderSpread).add(orderStayFee).add(spreadRatePrice);
        log.info("????????????????????? = {}", all_fee_amt);

        userPosition.setSellOrderId(GeneratePosition.getPositionId());
        userPosition.setSellOrderPrice(now_price);
        userPosition.setSellOrderTime(new Date());

        BigDecimal order_fee_all = buy_fee_amt.add(sell_fee_amt);
        userPosition.setOrderFee(order_fee_all);

        userPosition.setProfitAndLose(profitLoss);

        BigDecimal all_profit = profitLoss.subtract(all_fee_amt);
        userPosition.setAllProfitAndLose(all_profit);

        //??????????????????
        userPosition.setOrderStayDays(GetStayDays.getDays(userPosition.getBuyOrderTime()));
        int updatePositionCount = this.userPositionMapper.updateByPrimaryKeySelective(userPosition);
        if (updatePositionCount > 0) {
            log.info("????????????????????????????????????????????????");
        } else {
            log.error("?????????????????????????????????????????????");
            throw new Exception("?????????????????????????????????????????????");
        }

        BigDecimal freez_amt = all_buy_amt.divide(new BigDecimal(userPosition.getOrderLever().intValue()), 2, 4);
        //BigDecimal freez_amt = all_buy_amt;

        BigDecimal reckon_all = user_all_amt.add(all_profit);
        //????????????????????????=??????????????????+?????????+???????????????+???????????????
        BigDecimal reckon_enable = user_enable_amt.add(all_profit).add(freez_amt).add(userPosition.getMarginAdd());

        log.info("???????????????????????????  = {} , ???????????? = {}", reckon_all, reckon_enable);
        user.setUserAmt(reckon_all);
        user.setEnableAmt(reckon_enable);
        int updateUserCount = this.userMapper.updateByPrimaryKeySelective(user);
        if (updateUserCount > 0) {
            log.info("??????????????????????????????????????????");
        } else {
            log.error("???????????????????????????????????????");
            throw new Exception("???????????????????????????????????????");
        }

        UserCashDetail ucd = new UserCashDetail();
        ucd.setPositionId(userPosition.getId());
        ucd.setAgentId(user.getAgentId());
        ucd.setAgentName(user.getAgentName());
        ucd.setUserId(user.getId());
        ucd.setUserName(user.getRealName());
        ucd.setDeType("?????????");
        ucd.setDeAmt(all_profit);
        ucd.setDeSummary("???????????????" + userPosition.getStockCode() + "/" + userPosition.getStockName() + ",???????????????" + freez_amt + ",???????????????" + all_fee_amt + ",????????????" + orderStayFee+ ",????????????" + orderSpread + ",?????????" + profitLoss + "???????????????" + all_profit);

        ucd.setAddTime(new Date());
        ucd.setIsRead(Integer.valueOf(0));

        int insertSxfCount = this.userCashDetailMapper.insert(ucd);
        if (insertSxfCount > 0) {
            //??????????????????-???????????????
            iAgentAgencyFeeService.AgencyFeeIncome(2,userPosition.getPositionSn());
            //??????????????????-??????
            iAgentAgencyFeeService.AgencyFeeIncome(4,userPosition.getPositionSn());
            log.info("??????????????????????????????????????????");
        } else {
            log.error("???????????????????????????????????????");
            throw new Exception("???????????????????????????????????????");
        }

        return ServerResponse.createBySuccessMsg("???????????????");
    }

    //???????????????????????????
    public ServerResponse addmargin(String positionSn, int doType, BigDecimal marginAdd) throws Exception {
        log.info("??????????????????????????? positionSn = {} ??? dotype = {}", positionSn, Integer.valueOf(doType));

        SiteSetting siteSetting = this.iSiteSettingService.getSiteSetting();
        if (siteSetting == null) {
            log.error("????????????????????????????????????????????????");
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }

        if (doType != 0) {
            /*String am_begin = siteSetting.getTransAmBegin();
            String am_end = siteSetting.getTransAmEnd();
            String pm_begin = siteSetting.getTransPmBegin();
            String pm_end = siteSetting.getTransPmEnd();
            boolean am_flag = BuyAndSellUtils.isTransTime(am_begin, am_end);
            boolean pm_flag = BuyAndSellUtils.isTransTime(pm_begin, pm_end);
            log.info("??????????????????????????? = {} ??????????????????????????? = {}", Boolean.valueOf(am_flag), Boolean.valueOf(pm_flag));
            if (!am_flag && !pm_flag) {
                return ServerResponse.createByErrorMsg("????????????????????????????????????");
            }*/
        }

        UserPosition userPosition = this.userPositionMapper.findPositionBySn(positionSn);
        if (userPosition == null) {
            return ServerResponse.createByErrorMsg("??????????????????????????????");
        }

        User user = this.userMapper.selectByPrimaryKey(userPosition.getUserId());
        /*????????????????????????*/
        SiteProduct siteProduct = iSiteProductService.getProductSetting();
        if (!siteProduct.getStockMarginDisplay()) {
            return ServerResponse.createByErrorMsg("????????????????????????????????????");
        }

        if(siteProduct.getHolidayDisplay()){
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }

        if (siteProduct.getRealNameDisplay() && user.getIsLock().intValue() == 1) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }

        if (1 == userPosition.getIsLock().intValue()) {
            return ServerResponse.createByErrorMsg("???????????? " + userPosition.getLockMsg());
        }

        BigDecimal user_all_amt = user.getUserAmt();
        BigDecimal user_enable_amt = user.getEnableAmt();
        int compareUserAmtInt = user_enable_amt.compareTo(marginAdd);
        log.info("?????????????????? = {}  ???????????? =  {}", user_enable_amt, marginAdd);
        log.info("?????? ???????????? ??? ?????? ???????????? =  {}", Integer.valueOf(compareUserAmtInt));
        if (compareUserAmtInt == -1) {
            return ServerResponse.createByErrorMsg("???????????????????????????????????????" + marginAdd + "???");
        }


        userPosition.setMarginAdd(userPosition.getMarginAdd().add(marginAdd));

        int updatePositionCount = this.userPositionMapper.updateByPrimaryKeySelective(userPosition);
        if (updatePositionCount > 0) {
            log.info("????????????????????????????????????????????????");
        } else {
            log.error("?????????????????????????????????????????????");
            throw new Exception("?????????????????????????????????????????????");
        }

        //????????????????????????=??????????????????-????????????
        BigDecimal reckon_enable = user_enable_amt.subtract(marginAdd);

        log.info("????????????????????????????????????  = {} , ???????????? = {}", user_all_amt, reckon_enable);
        user.setEnableAmt(reckon_enable);
        int updateUserCount = this.userMapper.updateByPrimaryKeySelective(user);
        if (updateUserCount > 0) {
            log.info("??????????????????????????????????????????");
        } else {
            log.error("???????????????????????????????????????");
            throw new Exception("???????????????????????????????????????");
        }

        UserCashDetail ucd = new UserCashDetail();
        ucd.setPositionId(userPosition.getId());
        ucd.setAgentId(user.getAgentId());
        ucd.setAgentName(user.getAgentName());
        ucd.setUserId(user.getId());
        ucd.setUserName(user.getRealName());
        ucd.setDeType("???????????????");
        ucd.setDeAmt(marginAdd.multiply(new BigDecimal("-1")));
        ucd.setDeSummary("???????????????" + userPosition.getStockCode() + "/" + userPosition.getStockName() + ",???????????????" + marginAdd );

        ucd.setAddTime(new Date());
        ucd.setIsRead(Integer.valueOf(0));

        int insertSxfCount = this.userCashDetailMapper.insert(ucd);
        if (insertSxfCount > 0) {
            log.info("??????????????????????????????????????????");
        } else {
            log.error("???????????????????????????????????????");
            throw new Exception("???????????????????????????????????????");
        }

        return ServerResponse.createBySuccessMsg("???????????????");
    }


    public ServerResponse lock(Integer positionId, Integer state, String lockMsg) {
        if (positionId == null || state == null) {
            return ServerResponse.createByErrorMsg("??????????????????");
        }

        UserPosition position = this.userPositionMapper.selectByPrimaryKey(positionId);
        if (position == null) {
            return ServerResponse.createByErrorMsg("???????????????");
        }

        if (position.getSellOrderId() != null) {
            return ServerResponse.createByErrorMsg("?????????????????????");
        }

        if (state.intValue() == 1 &&
                StringUtils.isBlank(lockMsg)) {
            return ServerResponse.createByErrorMsg("????????????????????????");
        }


        if (state.intValue() == 1) {
            position.setIsLock(Integer.valueOf(1));
            position.setLockMsg(lockMsg);
        } else {
            position.setIsLock(Integer.valueOf(0));
        }

        int updateCount = this.userPositionMapper.updateByPrimaryKeySelective(position);
        if (updateCount > 0) {
            return ServerResponse.createBySuccessMsg("????????????");
        }
        return ServerResponse.createByErrorMsg("????????????");
    }

    public ServerResponse del(Integer positionId) {
        if (positionId == null) {
            return ServerResponse.createByErrorMsg("id????????????");
        }
        UserPosition position = this.userPositionMapper.selectByPrimaryKey(positionId);
        if (position == null) {
            ServerResponse.createByErrorMsg("??????????????????");
        }
        /*if (position.getSellOrderId() == null) {
            return ServerResponse.createByErrorMsg("????????????????????????");
        }*/
        int updateCount = this.userPositionMapper.deleteByPrimaryKey(positionId);
        if (updateCount > 0) {
            return ServerResponse.createBySuccessMsg("????????????");
        }
        return ServerResponse.createByErrorMsg("????????????");
    }

    public ServerResponse findMyPositionByCodeAndSpell(String stockCode, String stockSpell, Integer state, HttpServletRequest request, int pageNum, int pageSize) {
        User user = this.iUserService.getCurrentUser(request);

        PageHelper.startPage(pageNum, pageSize);


        List<UserPosition> userPositions = this.userPositionMapper.findMyPositionByCodeAndSpell(user.getId(), stockCode, stockSpell, state);

        List<UserPositionVO> userPositionVOS = Lists.newArrayList();
        if (userPositions.size() > 0) {
            for (UserPosition position : userPositions) {

                UserPositionVO userPositionVO = assembleUserPositionVO(position);
                userPositionVOS.add(userPositionVO);
            }
        }

        PageInfo pageInfo = new PageInfo(userPositions);
        pageInfo.setList(userPositionVOS);

        return ServerResponse.createBySuccess(pageInfo);
    }

    public PositionVO findUserPositionAllProfitAndLose(Integer userId) {
        List<UserPosition> userPositions = this.userPositionMapper.findPositionByUserIdAndSellIdIsNull(userId);

        BigDecimal allProfitAndLose = new BigDecimal("0");
        BigDecimal allFreezAmt = new BigDecimal("0");
        BigDecimal allPositionMoney = new BigDecimal("0");
        for (UserPosition position : userPositions) {

            StockListVO stockListVO = SinaStockApi.assembleStockListVO(
                    SinaStockApi.getSinaStock(position.getStockGid()));
            String nowPriceStr = stockListVO.getNowPrice();
            BigDecimal nowPrice = new BigDecimal(StringUtils.isEmpty(nowPriceStr)?position.getBuyOrderPrice().toPlainString():nowPriceStr);
            if (nowPrice.compareTo(new BigDecimal("0")) != 0) {

                BigDecimal buyPrice = position.getBuyOrderPrice();
                BigDecimal subPrice = nowPrice.subtract(buyPrice);

                BigDecimal profit_and_lose = subPrice.multiply(new BigDecimal(position.getOrderNum().intValue()));
                //BigDecimal profit_and_lose = subPrice.multiply(new BigDecimal(position.getOrderNum().intValue())).divide(new BigDecimal(position.getOrderLever())).setScale(2,4);
                if ("??????".equals(position.getOrderDirection())) {
                    profit_and_lose = profit_and_lose.negate();
                }


                BigDecimal total_fee = position.getOrderFee().add(position.getOrderSpread()).add(position.getOrderStayFee());


                BigDecimal position_profit = profit_and_lose.subtract(total_fee);
                allPositionMoney=allPositionMoney.add(position.getOrderTotalPrice());

                allProfitAndLose = allProfitAndLose.add(position_profit);

                BigDecimal position_freez = position.getOrderTotalPrice().divide(new BigDecimal(position.getOrderLever().intValue()), 2, 4);
                //BigDecimal position_freez = position.getOrderTotalPrice();
//                allFreezAmt = allFreezAmt.add(position_freez).add(position.getMarginAdd());
                continue;
            }

            log.info("????????????????????????????????????????????????0????????????????????????");
        }

        //???????????????????????????
//        List<FundsApply> fundsApplyList = fundsApplyMapper.getUserMarginList(userId);
//        for (FundsApply fundsApply : fundsApplyList){
//            allFreezAmt = allFreezAmt.add(fundsApply.getMargin());
//        }


        PositionVO positionVO = new PositionVO();
        positionVO.setAllProfitAndLose(allProfitAndLose);
        positionVO.setAllFreezAmt(allFreezAmt);
        positionVO.setAllPositionMoney(allPositionMoney);

        return positionVO;
    }

    public List<UserPosition> findPositionByUserIdAndSellIdIsNull(Integer userId) {
        return this.userPositionMapper.findPositionByUserIdAndSellIdIsNull(userId);
    }



    /**
     * ??????????????????????????????
     * @param userId
     * @param pageNo
     * @param pageSize
     * @return
     */
    @Override
    public List<UserPositionVO> findPositionByUserIdAndSellIdIsNull(Integer userId, Integer pageNo, Integer pageSize) {
        PageHelper.startPage(pageNo, pageSize);
        List<UserPosition> list = this.userPositionMapper.findPositionByUserIdAndSellIdIsNull(userId);
        List<UserPositionVO> userPositionVOList=new ArrayList<>();
        if(!CollectionUtils.isEmpty(list)){
            for (UserPosition userPosition:list){
                UserPositionVO userPositionVO = assembleUserPositionVO(userPosition);
                userPositionVOList.add(userPositionVO);
            }
        }
        return userPositionVOList;
    }

    /**
     * ??????????????????????????????????????????
     * @param userId
     * @return
     */
    @Override
    public List<UserPosition> findPositionByUserIdAndSellIdNotNull(Integer userId) {
        return this.userPositionMapper.findPositionByUserIdAndSellIdNotNull(userId);
    }

    @Override
    public PositionVO findUserPositionAllProfitAndLose(List<UserPositionVO> userPositionVos) {
        if(!CollectionUtils.isEmpty(userPositionVos)){
            BigDecimal allProfitAndLose = new BigDecimal("0");
            BigDecimal allFreezAmt = new BigDecimal("0");
            BigDecimal allPositionMoney = new BigDecimal("0");
            for (UserPositionVO  position : userPositionVos) {
                String nowPriceStr = position.getNow_price();
                BigDecimal nowPrice = new BigDecimal(StringUtils.isEmpty(nowPriceStr) ? position.getBuyOrderPrice().toPlainString() : nowPriceStr);
                if (nowPrice.compareTo(new BigDecimal("0")) != 0) {

                    BigDecimal buyPrice = position.getBuyOrderPrice();
                    BigDecimal subPrice = nowPrice.subtract(buyPrice);

                    BigDecimal profit_and_lose = subPrice.multiply(new BigDecimal(position.getOrderNum().intValue()));
                    //BigDecimal profit_and_lose = subPrice.multiply(new BigDecimal(position.getOrderNum().intValue())).divide(new BigDecimal(position.getOrderLever())).setScale(2,4);
                    if ("??????".equals(position.getOrderDirection())) {
                        profit_and_lose = profit_and_lose.negate();
                    }

                    BigDecimal total_fee = position.getOrderFee().add(position.getOrderSpread()).add(position.getOrderStayFee());

                    BigDecimal position_profit = profit_and_lose.subtract(total_fee);
                    allPositionMoney = allPositionMoney.add(position.getOrderTotalPrice());

                    allProfitAndLose = allProfitAndLose.add(position_profit);

                    BigDecimal position_freez = position.getOrderTotalPrice().divide(new BigDecimal(position.getOrderLever().intValue()), 2, 4);
                    //BigDecimal position_freez = position.getOrderTotalPrice();
                    allFreezAmt = allFreezAmt.add(position_freez).add(position.getMarginAdd());
                    continue;
                }

                log.info("????????????????????????????????????????????????0????????????????????????");
            }
            PositionVO positionVO = new PositionVO();
            positionVO.setAllProfitAndLose(allProfitAndLose);
            positionVO.setAllFreezAmt(allFreezAmt);
            positionVO.setAllPositionMoney(allPositionMoney);

            return positionVO;
        }
        return null;
    }

    @Override
    public List<UserPosition> findPositionByUserIdAndSellIdNotNull(Integer userId, Integer pageNo, Integer pageSize) {
        PageHelper.startPage(pageNo, pageSize);
        List<UserPosition> positionList = this.userPositionMapper.findPositionByUserIdAndSellIdNotNull(userId);
        return positionList;
    }

    public List<UserPosition> findPositionByStockCodeAndTimes(int minuteTimes, String stockCode, Integer userId) {
        Date paramTimes = null;
        paramTimes = DateTimeUtil.parseToDateByMinute(minuteTimes);

        return this.userPositionMapper.findPositionByStockCodeAndTimes(paramTimes, stockCode, userId);
    }

    public Integer findPositionNumByTimes(int minuteTimes, Integer userId) {
        Date beginDate = DateTimeUtil.parseToDateByMinute(minuteTimes);
        Integer transNum = this.userPositionMapper.findPositionNumByTimes(beginDate, userId);
        log.info("?????? {} ??? {} ???????????? ???????????? {}", new Object[]{userId, Integer.valueOf(minuteTimes), transNum});
        return transNum;
    }

    public ServerResponse listByAgent(Integer positionType, Integer state, Integer userId, Integer agentId, String positionSn, String beginTime, String endTime, HttpServletRequest request, int pageNum, int pageSize) {
        AgentUser currentAgent = this.iAgentUserService.getCurrentAgent(request);


        if (agentId != null) {
            AgentUser agentUser = this.agentUserMapper.selectByPrimaryKey(agentId);
            if (agentUser!=null && agentUser.getParentId() != currentAgent.getId()) {
                return ServerResponse.createByErrorMsg("???????????????????????????????????????");
            }
        }

        Integer searchId = null;
        if (agentId == null) {
            searchId = currentAgent.getId();
        } else {
            searchId = agentId;
        }


        Timestamp begin_time = null;
        if (StringUtils.isNotBlank(beginTime)) {
            begin_time = DateTimeUtil.searchStrToTimestamp(beginTime);
        }
        Timestamp end_time = null;
        if (StringUtils.isNotBlank(endTime)) {
            end_time = DateTimeUtil.searchStrToTimestamp(endTime);
        }

        PageHelper.startPage(pageNum, pageSize);


        List<UserPosition> userPositions = this.userPositionMapper.listByAgent(positionType, state, userId, searchId, positionSn, begin_time, end_time);

        List<AgentPositionVO> agentPositionVOS = Lists.newArrayList();
        for (UserPosition position : userPositions) {
            AgentPositionVO agentPositionVO = assembleAgentPositionVO(position);
            agentPositionVOS.add(agentPositionVO);
        }

        PageInfo pageInfo = new PageInfo(userPositions);
        pageInfo.setList(agentPositionVOS);

        return ServerResponse.createBySuccess(pageInfo);
    }

    public ServerResponse getIncome(Integer agentId, Integer positionType, String beginTime, String endTime) {
        if (StringUtils.isBlank(beginTime) || StringUtils.isBlank(endTime)) {
            return ServerResponse.createByErrorMsg("??????????????????");
        }

        Timestamp begin_time = null;
        if (StringUtils.isNotBlank(beginTime)) {
            begin_time = DateTimeUtil.searchStrToTimestamp(beginTime);
        }
        Timestamp end_time = null;
        if (StringUtils.isNotBlank(endTime)) {
            end_time = DateTimeUtil.searchStrToTimestamp(endTime);
        }


        List<UserPosition> userPositions = this.userPositionMapper.listByAgent(positionType, Integer.valueOf(1), null, agentId, null, begin_time, end_time);


        BigDecimal order_fee_amt = new BigDecimal("0");
        BigDecimal order_profit_and_lose = new BigDecimal("0");
        BigDecimal order_profit_and_lose_all = new BigDecimal("0");

        for (UserPosition position : userPositions) {

            order_fee_amt = order_fee_amt.add(position.getOrderFee()).add(position.getOrderSpread()).add(position.getOrderStayFee());

            order_profit_and_lose = order_profit_and_lose.add(position.getProfitAndLose());

            order_profit_and_lose_all = order_profit_and_lose_all.add(position.getAllProfitAndLose());
        }

        AgentIncomeVO agentIncomeVO = new AgentIncomeVO();
        agentIncomeVO.setOrderSize(Integer.valueOf(userPositions.size()));
        agentIncomeVO.setOrderFeeAmt(order_fee_amt);
        agentIncomeVO.setOrderProfitAndLose(order_profit_and_lose);
        agentIncomeVO.setOrderAllAmt(order_profit_and_lose_all);

        return ServerResponse.createBySuccess(agentIncomeVO);
    }

    public ServerResponse listByAdmin(Integer agentId, Integer positionType, Integer state, Integer userId, String positionSn, String beginTime, String endTime, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);


        Timestamp begin_time = null;
        if (StringUtils.isNotBlank(beginTime)) {
            begin_time = DateTimeUtil.searchStrToTimestamp(beginTime);
        }
        Timestamp end_time = null;
        if (StringUtils.isNotBlank(endTime)) {
            end_time = DateTimeUtil.searchStrToTimestamp(endTime);
        }


        List<UserPosition> userPositions = this.userPositionMapper.listByAgent(positionType, state, userId, agentId, positionSn, begin_time, end_time);

        List<AdminPositionVO> adminPositionVOS = Lists.newArrayList();
        for (UserPosition position : userPositions) {
            AdminPositionVO adminPositionVO = assembleAdminPositionVO(position);
            adminPositionVOS.add(adminPositionVO);
        }

        PageInfo pageInfo = new PageInfo(userPositions);
        pageInfo.setList(adminPositionVOS);

        return ServerResponse.createBySuccess(pageInfo);
    }

    public int CountPositionNum(Integer state, Integer accountType) {
        return this.userPositionMapper.CountPositionNum(state, accountType);
    }

    public BigDecimal CountPositionProfitAndLose() {
        return this.userPositionMapper.CountPositionProfitAndLose();
    }

    public BigDecimal CountPositionAllProfitAndLose() {
        return this.userPositionMapper.CountPositionAllProfitAndLose();
    }

    public ServerResponse create(Integer userId, String stockCode, String buyPrice, String buyTime, Integer buyNum, Integer buyType, Integer lever) {
        if (userId == null || StringUtils.isBlank(buyPrice) || StringUtils.isBlank(stockCode) ||
                StringUtils.isBlank(buyTime) || buyNum == null || buyType == null || lever == null) {

            return ServerResponse.createByErrorMsg("??????????????????");
        }

        User user = this.userMapper.selectByPrimaryKey(userId);
        if (user == null) {
            return ServerResponse.createByErrorMsg("???????????????");
        }
        if (user.getAccountType().intValue() != 1) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }

        Stock stock = (Stock) this.iStockService.findStockByCode(stockCode).getData();
        if (stock == null) {
            return ServerResponse.createByErrorMsg("???????????????");
        }


        SiteSetting siteSetting = this.iSiteSettingService.getSiteSetting();
        if (siteSetting == null) {
            log.error("???????????????????????????????????????");
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }


        BigDecimal user_enable_amt = user.getEnableAmt();

        BigDecimal buy_amt = (new BigDecimal(buyPrice)).multiply(new BigDecimal(buyNum.intValue()));
        BigDecimal buy_amt_autual = buy_amt.divide(new BigDecimal(lever.intValue()), 2, 4);


        int compareUserAmtInt = user_enable_amt.compareTo(buy_amt_autual);
        log.info("?????????????????? = {}  ?????????????????? =  {}", user_enable_amt, buy_amt_autual);
        log.info("?????? ???????????? ??? ?????? ???????????? =  {}", Integer.valueOf(compareUserAmtInt));
        if (compareUserAmtInt == -1) {
            return ServerResponse.createByErrorMsg("???????????????????????????????????????" + buy_amt_autual + "???");
        }


        BigDecimal reckon_enable = user_enable_amt.subtract(buy_amt_autual);
        user.setEnableAmt(reckon_enable);
        int updateUserCount = this.userMapper.updateByPrimaryKeySelective(user);
        if (updateUserCount > 0) {
            log.info("????????????????????????????????????????????????");
        } else {
            log.error("?????????????????????????????????????????????");
        }


        UserPosition userPosition = new UserPosition();
        userPosition.setPositionType(Integer.valueOf(1));
        userPosition.setPositionSn(KeyUtils.getUniqueKey());
        userPosition.setUserId(user.getId());
        userPosition.setNickName(user.getRealName());
        userPosition.setAgentId(user.getAgentId());
        userPosition.setStockCode(stock.getStockCode());
        userPosition.setStockName(stock.getStockName());
        userPosition.setStockGid(stock.getStockGid());
        userPosition.setStockSpell(stock.getStockSpell());
        userPosition.setBuyOrderId(GeneratePosition.getPositionId());
        userPosition.setBuyOrderTime(DateTimeUtil.strToDate(buyTime));
        userPosition.setBuyOrderPrice(new BigDecimal(buyPrice));
        userPosition.setOrderDirection((buyType.intValue() == 0) ? "??????" : "??????");

        userPosition.setOrderNum(buyNum);


        userPosition.setIsLock(Integer.valueOf(0));


        userPosition.setOrderLever(lever);


        userPosition.setOrderTotalPrice(buy_amt);


        BigDecimal buy_fee_amt = buy_amt.multiply(siteSetting.getBuyFee()).setScale(2, 4);
        log.info("?????????????????? ?????????????????????????????? * ???????????? = {}", buy_fee_amt);
        userPosition.setOrderFee(buy_fee_amt);


        BigDecimal buy_yhs_amt = buy_amt.multiply(siteSetting.getDutyFee()).setScale(2, 4);
        log.info("?????????????????? ?????????????????????????????? * ???????????? = {}", buy_yhs_amt);
        userPosition.setOrderSpread(buy_yhs_amt);


        BigDecimal profit_and_lose = new BigDecimal("0");
        userPosition.setProfitAndLose(profit_and_lose);


        BigDecimal all_profit_and_lose = profit_and_lose.subtract(buy_fee_amt).subtract(buy_yhs_amt);
        userPosition.setAllProfitAndLose(all_profit_and_lose);


        userPosition.setOrderStayDays(Integer.valueOf(0));
        userPosition.setOrderStayFee(new BigDecimal("0"));
        userPosition.setSpreadRatePrice(new BigDecimal("0"));

        int insertPositionCount = this.userPositionMapper.insert(userPosition);
        if (insertPositionCount > 0) {
            log.info("??????????????????????????????????????????");
        } else {
            log.error("??????????????????????????????????????????");
        }

        return ServerResponse.createBySuccess("????????????????????????");
    }

    public int deleteByUserId(Integer userId) {
        return this.userPositionMapper.deleteByUserId(userId);
    }

    public void doClosingStayTask() {
        List<UserPosition> userPositions = this.userPositionMapper.findAllStayPosition();


        if (userPositions.size() > 0) {
            log.info("???????????????????????????????????? = {}", Integer.valueOf(userPositions.size()));

            SiteProduct siteProduct = iSiteProductService.getProductSetting();
            if(!siteProduct.getHolidayDisplay()) {
                for (UserPosition position : userPositions) {
                    int stayDays = GetStayDays.getDays(GetStayDays.getBeginDate(position.getBuyOrderTime()));
                    //?????????????????????
                    stayDays = stayDays + 1;

                    log.info("");
                    log.info("???????????? ????????????id = {} ????????? = {} ??????id = {} realName = {} ???????????? = {}", new Object[]{position
                            .getId(), position.getPositionSn(), position.getUserId(), position
                            .getNickName(), Integer.valueOf(stayDays)});

                    if (stayDays != 0) {
                        log.info(" ???????????? {} ??? ?????????", Integer.valueOf(stayDays));
                        try {
                            closingStayTask(position, Integer.valueOf(stayDays));
                        } catch (Exception e) {
                            log.error("doClosingStayTask = ", e);


                        }


                    } else {


                        log.info("???????????? = {} ,????????????0???,???????????????...", position.getId());
                    }

                    log.info("??????????????? ???????????????");
                    log.info("");
                }

                SiteTaskLog stl = new SiteTaskLog();
                stl.setTaskType("???????????????");
                stl.setAddTime(new Date());
                stl.setIsSuccess(Integer.valueOf(0));
                stl.setTaskTarget("?????????????????????????????????" + userPositions.size());
                this.siteTaskLogMapper.insert(stl);
            }
        } else {
            log.info("doClosingStayTask???????????????????????????");
        }
    }

    /*?????????????????????????????????15?????????*/
    public void expireStayUnwindTask() {
        List<UserPosition> userPositions = this.userPositionMapper.findAllStayPosition();


        if (userPositions.size() > 0) {
            log.info("???????????????????????????????????? = {}", Integer.valueOf(userPositions.size()));

            SiteSetting siteSetting = this.iSiteSettingService.getSiteSetting();
            for (UserPosition position : userPositions) {
                int stayDays = GetStayDays.getDays(GetStayDays.getBeginDate(position.getBuyOrderTime()));

                log.info("");
                log.info("???????????? ????????????id = {} ????????? = {} ??????id = {} realName = {} ???????????? = {}", new Object[]{position
                        .getId(), position.getPositionSn(), position.getUserId(), position
                        .getNickName(), Integer.valueOf(stayDays)});

                //????????????????????????
                if (stayDays >= siteSetting.getStayMaxDays().intValue()) {
                    log.info(" ???????????? {} ???", Integer.valueOf(stayDays));
                    try {
                        this.sell(position.getPositionSn(),0);
                    } catch (Exception e) {
                        log.error("expireStayUnwindTask = ", e);
                    }
                } else {
                    log.info("???????????? = {} ,????????????0???,???????????????...", position.getId());
                }
            }
        } else {
            log.info("doClosingStayTask???????????????????????????");
        }
    }

    @Transactional
    public ServerResponse closingStayTask(UserPosition position, Integer stayDays) throws Exception {
        log.info("=================closingStayTask====================");
        log.info("????????????????????????id={},????????????={}", position.getId(), stayDays);

        SiteSetting siteSetting = this.iSiteSettingService.getSiteSetting();
        if (siteSetting == null) {
            log.error("????????????????????????????????????????????????");
            return ServerResponse.createByErrorMsg("????????????????????????????????????????????????");
        }


        BigDecimal stayFee = position.getOrderTotalPrice().multiply(siteSetting.getStayFee());

        BigDecimal allStayFee = stayFee.multiply(new BigDecimal(stayDays.intValue()));

        log.info("???????????? = {}", allStayFee);


        position.setOrderStayFee(allStayFee);
        position.setOrderStayDays(stayDays);

        BigDecimal all_profit = position.getAllProfitAndLose().subtract(allStayFee);
        position.setAllProfitAndLose(all_profit);

        int updateCount = this.userPositionMapper.updateByPrimaryKeySelective(position);
        if (updateCount > 0) {
            //??????????????????-?????????
            iAgentAgencyFeeService.AgencyFeeIncome(3,position.getPositionSn());
            log.info("???closingStayTask???????????????????????????????????????");
        } else {
            log.error("???closingStayTask???????????????????????????????????????");
            throw new Exception("???closingStayTask???????????????????????????????????????");
        }


        log.info("=======================================================");
        return ServerResponse.createBySuccess();
    }

    public List<Integer> findDistinctUserIdList() {
        return this.userPositionMapper.findDistinctUserIdList();
    }

    private AdminPositionVO assembleAdminPositionVO(UserPosition position) {
        AdminPositionVO adminPositionVO = new AdminPositionVO();

        adminPositionVO.setId(position.getId());
        adminPositionVO.setPositionSn(position.getPositionSn());
        adminPositionVO.setPositionType(position.getPositionType());
        adminPositionVO.setUserId(position.getUserId());
        adminPositionVO.setNickName(position.getNickName());
        adminPositionVO.setAgentId(position.getAgentId());
        adminPositionVO.setStockName(position.getStockName());
        adminPositionVO.setStockCode(position.getStockCode());
        adminPositionVO.setStockGid(position.getStockGid());
        adminPositionVO.setStockSpell(position.getStockSpell());
        adminPositionVO.setBuyOrderId(position.getBuyOrderId());
        adminPositionVO.setBuyOrderTime(position.getBuyOrderTime());
        adminPositionVO.setBuyOrderPrice(position.getBuyOrderPrice());
        adminPositionVO.setSellOrderId(position.getSellOrderId());
        adminPositionVO.setSellOrderTime(position.getSellOrderTime());
        adminPositionVO.setSellOrderPrice(position.getSellOrderPrice());
        adminPositionVO.setOrderDirection(position.getOrderDirection());
        adminPositionVO.setOrderNum(position.getOrderNum());
        adminPositionVO.setOrderLever(position.getOrderLever());
        adminPositionVO.setOrderTotalPrice(position.getOrderTotalPrice());
        adminPositionVO.setOrderFee(position.getOrderFee());
        adminPositionVO.setOrderSpread(position.getOrderSpread());
        adminPositionVO.setOrderStayFee(position.getOrderStayFee());
        adminPositionVO.setOrderStayDays(position.getOrderStayDays());

        adminPositionVO.setIsLock(position.getIsLock());
        adminPositionVO.setLockMsg(position.getLockMsg());

        adminPositionVO.setStockPlate(position.getStockPlate());

        PositionProfitVO positionProfitVO = getPositionProfitVO(position);
        adminPositionVO.setProfitAndLose(positionProfitVO.getProfitAndLose());
        adminPositionVO.setAllProfitAndLose(positionProfitVO.getAllProfitAndLose());
        adminPositionVO.setNow_price(positionProfitVO.getNowPrice());


        return adminPositionVO;
    }

    private AgentPositionVO assembleAgentPositionVO(UserPosition position) {
        AgentPositionVO agentPositionVO = new AgentPositionVO();

        agentPositionVO.setId(position.getId());
        agentPositionVO.setPositionSn(position.getPositionSn());
        agentPositionVO.setPositionType(position.getPositionType());
        agentPositionVO.setUserId(position.getUserId());
        agentPositionVO.setNickName(position.getNickName());
        agentPositionVO.setAgentId(position.getAgentId());
        agentPositionVO.setStockName(position.getStockName());
        agentPositionVO.setStockCode(position.getStockCode());
        agentPositionVO.setStockGid(position.getStockGid());
        agentPositionVO.setStockSpell(position.getStockSpell());
        agentPositionVO.setBuyOrderId(position.getBuyOrderId());
        agentPositionVO.setBuyOrderTime(position.getBuyOrderTime());
        agentPositionVO.setBuyOrderPrice(position.getBuyOrderPrice());
        agentPositionVO.setSellOrderId(position.getSellOrderId());
        agentPositionVO.setSellOrderTime(position.getSellOrderTime());
        agentPositionVO.setSellOrderPrice(position.getSellOrderPrice());
        agentPositionVO.setOrderDirection(position.getOrderDirection());
        agentPositionVO.setOrderNum(position.getOrderNum());
        agentPositionVO.setOrderLever(position.getOrderLever());
        agentPositionVO.setOrderTotalPrice(position.getOrderTotalPrice());
        agentPositionVO.setOrderFee(position.getOrderFee());
        agentPositionVO.setOrderSpread(position.getOrderSpread());
        agentPositionVO.setOrderStayFee(position.getOrderStayFee());
        agentPositionVO.setOrderStayDays(position.getOrderStayDays());

        agentPositionVO.setIsLock(position.getIsLock());
        agentPositionVO.setLockMsg(position.getLockMsg());

        agentPositionVO.setStockPlate(position.getStockPlate());

        PositionProfitVO positionProfitVO = getPositionProfitVO(position);
        agentPositionVO.setProfitAndLose(positionProfitVO.getProfitAndLose());
        agentPositionVO.setAllProfitAndLose(positionProfitVO.getAllProfitAndLose());
        agentPositionVO.setNow_price(positionProfitVO.getNowPrice());


        return agentPositionVO;
    }

    private UserPositionVO assembleUserPositionVO(UserPosition position) {
        UserPositionVO userPositionVO = new UserPositionVO();

        userPositionVO.setId(position.getId());
        userPositionVO.setPositionType(position.getPositionType());
        userPositionVO.setPositionSn(position.getPositionSn());
        userPositionVO.setUserId(position.getUserId());
        userPositionVO.setNickName(position.getNickName());
        userPositionVO.setAgentId(position.getAgentId());
        userPositionVO.setStockName(position.getStockName());
        userPositionVO.setStockCode(position.getStockCode());
        userPositionVO.setStockGid(position.getStockGid());
        userPositionVO.setStockSpell(position.getStockSpell());
        userPositionVO.setBuyOrderId(position.getBuyOrderId());
        userPositionVO.setBuyOrderTime(position.getBuyOrderTime());
        userPositionVO.setBuyOrderPrice(position.getBuyOrderPrice());
        userPositionVO.setSellOrderId(position.getSellOrderId());
        userPositionVO.setSellOrderTime(position.getSellOrderTime());
        userPositionVO.setSellOrderPrice(position.getSellOrderPrice());
        userPositionVO.setProfitTargetPrice(position.getProfitTargetPrice());
        userPositionVO.setStopTargetPrice(position.getStopTargetPrice());
        userPositionVO.setOrderDirection(position.getOrderDirection());
        userPositionVO.setOrderNum(position.getOrderNum());
        userPositionVO.setOrderLever(position.getOrderLever());
        userPositionVO.setOrderTotalPrice(position.getOrderTotalPrice());
        userPositionVO.setOrderFee(position.getOrderFee());
        userPositionVO.setOrderSpread(position.getOrderSpread());
        userPositionVO.setOrderStayFee(position.getOrderStayFee());
        userPositionVO.setOrderStayDays(position.getOrderStayDays());
        userPositionVO.setMarginAdd(position.getMarginAdd());

        userPositionVO.setStockPlate(position.getStockPlate());
        userPositionVO.setSpreadRatePrice(position.getSpreadRatePrice());

        PositionProfitVO positionProfitVO = getPositionProfitVO(position);
        userPositionVO.setProfitAndLose(positionProfitVO.getProfitAndLose());
        userPositionVO.setAllProfitAndLose(positionProfitVO.getAllProfitAndLose());
        userPositionVO.setNow_price(positionProfitVO.getNowPrice());

        BigDecimal buyOrderPrice = userPositionVO.getBuyOrderPrice();
        String now_price = userPositionVO.getNow_price();
        if(!StringUtil.isEmpty(now_price)){
           // BigDecimal nowPrice = new BigDecimal(now_price);
            //BigDecimal prb = nowPrice.subtract(buyOrderPrice).divide(buyOrderPrice, 2, RoundingMode.DOWN).multiply(new BigDecimal(100));
            BigDecimal prb=positionProfitVO.getAllProfitAndLose().divide(position.getOrderTotalPrice(),4,RoundingMode.DOWN).multiply(new BigDecimal(100));
            //???????????????
            userPositionVO.setPlr(prb.toPlainString());
        }else {
            //???????????????
            userPositionVO.setPlr("0");
        }

        return userPositionVO;
    }

    public PositionProfitVO getPositionProfitVO(UserPosition position) {
        BigDecimal profitAndLose = new BigDecimal("0");
        BigDecimal allProfitAndLose = new BigDecimal("0");
        String nowPrice = "";

        if (position.getSellOrderId() != null) {

            BigDecimal subPrice = position.getSellOrderPrice().subtract(position.getBuyOrderPrice());
            //profitAndLose = subPrice.multiply(new BigDecimal(position.getOrderNum().intValue())).multiply(new BigDecimal(position.getOrderLever())).setScale(2,4);
            profitAndLose = subPrice.multiply(new BigDecimal(position.getOrderNum().intValue()));
            if ("??????".equals(position.getOrderDirection())) {
                profitAndLose = profitAndLose.negate();
            }


            allProfitAndLose = profitAndLose.subtract(position.getOrderFee()).subtract(position.getOrderSpread()).subtract(position.getOrderStayFee()).subtract(position.getSpreadRatePrice());
        } else {

//            StockListVO stockListVO = SinaStockApi.assembleStockListVO(
//                    SinaStockApi.getSinaStock(position.getStockGid()));

            //??????????????????????????????
            QqMiniData qqMiniData= QqStockApi.getSimpleMarket(position.getStockGid());
            nowPrice = StringUtils.isEmpty(qqMiniData.getNowPrice())?position.getBuyOrderPrice().toPlainString():qqMiniData.getNowPrice();

            BigDecimal subPrice = (new BigDecimal(nowPrice)).subtract(position.getBuyOrderPrice());
            //profitAndLose = subPrice.multiply(new BigDecimal(position.getOrderNum().intValue())).multiply(new BigDecimal(position.getOrderLever())).setScale(2,4);
            profitAndLose = subPrice.multiply(new BigDecimal(position.getOrderNum().intValue()));
            if ("??????".equals(position.getOrderDirection())) {
                profitAndLose = profitAndLose.negate();
            }


            //?????????= ???????????? ??? ????????? ??? ????????? ??? ????????? ??? ?????????
            allProfitAndLose = profitAndLose.subtract(position.getOrderFee()).subtract(position.getOrderSpread()).subtract(position.getOrderStayFee()).subtract(position.getSpreadRatePrice());
        }
        PositionProfitVO positionProfitVO = new PositionProfitVO();
        positionProfitVO.setProfitAndLose(profitAndLose);
        positionProfitVO.setAllProfitAndLose(allProfitAndLose);
        positionProfitVO.setNowPrice(nowPrice);

        return positionProfitVO;
    }

    /*??????????????????top??????*/
    public ServerResponse findPositionTopList(Integer pageSize) {
        List<UserPosition> userPositions = this.userPositionMapper.findPositionTopList(pageSize);

        List<UserPositionVO> userPositionVOS = Lists.newArrayList();
        if (userPositions.size() > 0) {
            for (UserPosition position : userPositions) {

                UserPositionVO userPositionVO = assembleUserPositionVO(position);
                userPositionVOS.add(userPositionVO);
            }
        }

        PageInfo pageInfo = new PageInfo(userPositions);
        pageInfo.setList(userPositionVOS);

        return ServerResponse.createBySuccess(pageInfo);
    }

    /*????????????????????????????????????????????????*/
    public ServerResponse findUserPositionByCode(HttpServletRequest request, String stockCode) {
        User user = this.iUserService.getCurrentRefreshUser(request);
        UserPosition position = this.userPositionMapper.findUserPositionByCode(user.getId(), stockCode);

        List<UserPositionVO> userPositionVOS = Lists.newArrayList();
        UserPositionVO userPositionVO = null;
        if(position != null){
            userPositionVO = assembleUserPositionVO(position);
        }
        userPositionVOS.add(userPositionVO);

        PageInfo pageInfo = new PageInfo();
        pageInfo.setList(userPositionVOS);

        return ServerResponse.createBySuccess(pageInfo);
    }

}

