package com.xc.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.xc.common.ServerResponse;
import com.xc.config.StockPoll;
import com.xc.dao.*;
import com.xc.pojo.*;
import com.xc.service.*;
import com.xc.utils.DateTimeUtil;
import com.xc.utils.PropertiesUtil;
import com.xc.utils.ip.IpUtils;
import com.xc.utils.ip.JuheIpApi;
import com.xc.utils.redis.CookieUtils;
import com.xc.utils.redis.JsonUtil;
import com.xc.utils.redis.RedisShardedPoolUtils;
import com.xc.vo.agent.AgentUserListVO;
import com.xc.vo.futuresposition.FuturesPositionVO;
import com.xc.vo.indexposition.IndexPositionVO;
import com.xc.vo.position.PositionProfitVO;
import com.xc.vo.position.PositionVO;
import com.xc.vo.user.UserInfoVO;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

@Service("iUserService")
public class UserServiceImpl implements IUserService {
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    UserMapper userMapper;

    @Autowired
    IAgentUserService iAgentUserService;

    @Autowired
    ISiteLoginLogService iSiteLoginLogService;

    @Autowired
    StockOptionMapper stockOptionMapper;

    @Autowired
    StockMapper stockMapper;
    @Autowired
    IUserPositionService iUserPositionService;
    @Autowired
    IUserBankService iUserBankService;
    @Autowired
    AgentUserMapper agentUserMapper;
    @Autowired
    SiteTaskLogMapper siteTaskLogMapper;
    @Autowired
    IStockOptionService iStockOptionService;
    @Autowired
    ISiteSettingService iSiteSettingService;
    @Autowired
    IUserCashDetailService iUserCashDetailService;
    @Autowired
    IUserRechargeService iUserRechargeService;
    @Autowired
    IUserWithdrawService iUserWithdrawService;
    @Autowired
    IUserIndexPositionService iUserIndexPositionService;
    @Autowired
    ISiteIndexSettingService iSiteIndexSettingService;
    @Autowired
    StockPoll stockPoll;
    @Autowired
    SiteAmtTransLogMapper siteAmtTransLogMapper;
    @Autowired
    IUserFuturesPositionService iUserFuturesPositionService;
    @Autowired
    ISiteFuturesSettingService iSiteFuturesSettingService;
    @Autowired
    IStockFuturesService iStockFuturesService;
    @Autowired
    StockFuturesMapper stockFuturesMapper;
    @Autowired
    StockIndexMapper stockIndexMapper;
    @Autowired
    ISiteMessageService iSiteMessageService;

    public ServerResponse reg(String yzmCode, String agentCode, String phone, String userPwd, HttpServletRequest request) {
        if (StringUtils.isBlank(agentCode) || StringUtils.isBlank(phone) ||
                StringUtils.isBlank(userPwd))
        {
            return ServerResponse.createByErrorMsg("????????????, ??????????????????");
        }


//        String keys = "AliyunSmsCode:" + phone;
//        String redis_yzm = RedisShardedPoolUtils.get(keys);
//
//        log.info("redis_yzm = {},yzmCode = {}", redis_yzm, yzmCode);
//        if (!yzmCode.equals(redis_yzm) && !"6666".equals(yzmCode)) {
//            return ServerResponse.createByErrorMsg("????????????, ???????????????");
//        }


        AgentUser agentUser = this.iAgentUserService.findByCode(agentCode);
        if (agentUser == null) {
            return ServerResponse.createByErrorMsg("????????????, ???????????????");
        }
        if (agentUser.getIsLock().intValue() == 1) {
            return ServerResponse.createByErrorMsg("????????????, ??????????????????");
        }


        User dbuser = this.userMapper.findByPhone(phone);
        if (dbuser != null) {
            return ServerResponse.createByErrorMsg("????????????, ??????????????????");
        }


        User user = new User();
        user.setAgentId(agentUser.getId());
        user.setAgentName(agentUser.getAgentName());
        user.setPhone(phone);
        user.setUserPwd(userPwd);


        user.setAccountType(Integer.valueOf(0));
        user.setIsLock(Integer.valueOf(1));
        user.setIsActive(Integer.valueOf(0));

        user.setRegTime(new Date());
        String uip = IpUtils.getIp(request);
        user.setRegIp(uip);
        String uadd = JuheIpApi.ip2Add(uip);
        user.setRegAddress(uadd);

        user.setIsLogin(Integer.valueOf(0));

        user.setUserAmt(new BigDecimal("0"));
        user.setEnableAmt(new BigDecimal("0"));

        user.setUserIndexAmt(new BigDecimal("0"));
        user.setEnableIndexAmt(new BigDecimal("0"));

        user.setUserFutAmt(new BigDecimal("0"));
        user.setEnableFutAmt(new BigDecimal("0"));

        user.setSumBuyAmt(new BigDecimal("0"));
        user.setSumChargeAmt(new BigDecimal("0"));


        int insertCount = this.userMapper.insert(user);

        if (insertCount > 0) {
            log.info("?????????????????? ?????? {} , ip = {} ?????? = {}", new Object[] { phone, uip, uadd });
            return ServerResponse.createBySuccessMsg("????????????.?????????");
        }
        return ServerResponse.createBySuccessMsg("????????????, ?????????");
    }



    public ServerResponse login(String phone, String userPwd, HttpServletRequest request) {
        if (StringUtils.isBlank(phone) || StringUtils.isBlank(userPwd)) {
            return ServerResponse.createByErrorMsg("??????????????????????????????");
        }

        User user = this.userMapper.login(phone, userPwd);
        if (user != null) {
            if (user.getIsLogin().intValue() == 1) {
                return ServerResponse.createByErrorMsg("????????????, ???????????????");
            }

            log.info("??????{}????????????, ????????????{} ,????????????{}", new Object[] { user.getId(), user.getIsLogin(), user.getIsLock() });

            this.iSiteLoginLogService.saveLog(user, request);
            return ServerResponse.createBySuccess(user);
        }
        return ServerResponse.createByErrorMsg("????????????????????????????????????");
    }




    public User getCurrentUser(HttpServletRequest request) {
        String loginToken = CookieUtils.readLoginToken(request, PropertiesUtil.getProperty("user.cookie.name"));
        String userJson = RedisShardedPoolUtils.get(loginToken);
        return (User)JsonUtil.string2Obj(userJson, User.class);
    }



    public User getCurrentRefreshUser(HttpServletRequest request) {
        String loginToken = CookieUtils.readLoginToken(request, PropertiesUtil.getProperty("user.cookie.name"));
        String userJson = RedisShardedPoolUtils.get(loginToken);
        User user = (User)JsonUtil.string2Obj(userJson, User.class);
        return this.userMapper.selectByPrimaryKey(user.getId());
    }

    public ServerResponse addOption(String code, HttpServletRequest request) {
        User user = getCurrentUser(request);
        String stockcode = code;
        if(code.contains("hf")){
            stockcode = code.split("_")[1];
        }
        stockcode = stockcode.replace("sh","").replace("sz","");
        StockOption dboption = this.stockOptionMapper.findMyOptionIsExistByCode(user.getId(), stockcode);

        if (dboption != null) {
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }


        Stock stock = new Stock();
        //????????????
        if(code.contains("hf")){
            StockFutures stockFutures = this.stockFuturesMapper.selectFuturesByCode(stockcode);
            if(stockFutures != null){
                stock.setId(stockFutures.getId());
                stock.setStockCode(stockFutures.getFuturesCode());
                stock.setStockGid(stockFutures.getFuturesGid());
                stock.setStockName(stockFutures.getFuturesName());
                stock.setIsLock(0);
            }
        } else if(code.contains("sh") || code.contains("sz")){
            StockIndex stockIndex = this.stockIndexMapper.selectIndexByCode(stockcode);
            if(stockIndex != null){
                stock.setId(stockIndex.getId());
                stock.setStockCode(stockIndex.getIndexCode());
                stock.setStockGid(stockIndex.getIndexGid());
                stock.setStockName(stockIndex.getIndexName());
                stock.setIsLock(0);
            }
        } else {
            stock = this.stockMapper.findStockByCode(code);
        }
        if (stock == null) {
            return ServerResponse.createByErrorMsg("??????????????????????????????");
        }
        StockOption stockOption = new StockOption();
        stockOption.setUserId(user.getId());
        stockOption.setStockId(stock.getId());
        stockOption.setAddTime(new Date());

        stockOption.setStockCode(stock.getStockCode());
        stockOption.setStockName(stock.getStockName());
        stockOption.setStockGid(stock.getStockGid());
        stockOption.setIsLock(stock.getIsLock());

        int insertCount = this.stockOptionMapper.insert(stockOption);
        if (insertCount > 0) {
            return ServerResponse.createBySuccessMsg("?????????????????????");
        }
        return ServerResponse.createByErrorMsg("????????????, ?????????");
    }




    public ServerResponse delOption(String code, HttpServletRequest request) {
        User user = getCurrentUser(request);
        String stockcode = code;
        if(code.contains("hf")){
            stockcode = code.split("_")[1].toString();
        }
        stockcode = stockcode.replace("sh","").replace("sz","");
        StockOption dboption = this.stockOptionMapper.findMyOptionIsExistByCode(user.getId(), stockcode);

        if (dboption == null) {
            return ServerResponse.createByErrorMsg("????????????, ??????????????????");
        }

        int delCount = this.stockOptionMapper.deleteByPrimaryKey(dboption.getId());
        if (delCount > 0) {
            return ServerResponse.createBySuccessMsg("?????????????????????");
        }
        return ServerResponse.createByErrorMsg("????????????, ?????????");
    }



    public ServerResponse isOption(String code, HttpServletRequest request) {
        User user = getCurrentUser(request);
        String stockcode = code;
        if(code.contains("hf")){
            stockcode = code.split("_")[1].toString();
        }
        stockcode = stockcode.replace("sh","").replace("sz","");
        return this.iStockOptionService.isOption(user.getId(), stockcode);
    }




    public ServerResponse getUserInfo(HttpServletRequest request) {
        String loginToken = CookieUtils.readLoginToken(request, PropertiesUtil.getProperty("user.cookie.name"));
        String userJson = RedisShardedPoolUtils.get(loginToken);
        User user = (User)JsonUtil.string2Obj(userJson, User.class);
        User dbuser = this.userMapper.selectByPrimaryKey(user.getId());

        UserInfoVO userInfoVO = assembleUserInfoVO(dbuser);

        return ServerResponse.createBySuccess(userInfoVO);
    }


    public ServerResponse updatePwd(String oldPwd, String newPwd, HttpServletRequest request) {
        if (StringUtils.isBlank(oldPwd) || StringUtils.isBlank(newPwd)) {
            return ServerResponse.createByErrorMsg("??????????????????");
        }

        User user = getCurrentRefreshUser(request);
        if (!oldPwd.equals(user.getUserPwd())) {
            return ServerResponse.createByErrorMsg("????????????");
        }

        user.setUserPwd(newPwd);
        int updateCount = this.userMapper.updateByPrimaryKeySelective(user);
        if (updateCount > 0) {
            return ServerResponse.createBySuccessMsg("????????????");
        }
        return ServerResponse.createByErrorMsg("????????????");
    }



    public ServerResponse checkPhone(String phone) {
        User user = this.userMapper.findByPhone(phone);
        if (user != null) {
            return ServerResponse.createBySuccessMsg("???????????????");
        }
        return ServerResponse.createByErrorMsg("???????????????");
    }



    public ServerResponse updatePwd(String phone, String code, String newPwd) {
        if (StringUtils.isBlank(phone) ||
                StringUtils.isBlank(code) ||
                StringUtils.isBlank(newPwd)) {
            return ServerResponse.createByErrorMsg("??????????????????");
        }


        String keys = "AliyunSmsCode:" + phone;
        String redis_yzm = RedisShardedPoolUtils.get(keys);

        log.info("redis_yzm = {} , code = {}", redis_yzm, code);
        if (!code.equals(redis_yzm)) {
            return ServerResponse.createByErrorMsg("????????????????????????????????????");
        }

        User user = this.userMapper.findByPhone(phone);
        if (user == null) {
            return ServerResponse.createByErrorMsg("???????????????");
        }

        user.setUserPwd(newPwd);
        int updateCount = this.userMapper.updateByPrimaryKeySelective(user);
        if (updateCount > 0) {
            return ServerResponse.createBySuccess("?????????????????????");
        }
        return ServerResponse.createByErrorMsg("??????????????????!");
    }


    public ServerResponse update(User user) {
        log.info("#####??????????????????####,??????????????? = {} ???????????? = {}", user
                .getUserAmt(), user.getEnableAmt());
        log.info("#####??????????????????####,??????index????????? = {} index???????????? = {}", user
                .getUserIndexAmt(), user.getEnableIndexAmt());
        if (user.getAgentId() != null) {
            AgentUser agentUser = this.agentUserMapper.selectByPrimaryKey(user.getAgentId());
            if (agentUser != null) {
                user.setAgentName(agentUser.getAgentName());
            }
        }

        int updateCount = this.userMapper.updateByPrimaryKeySelective(user);
        if (updateCount > 0) {
            return ServerResponse.createBySuccessMsg("????????????");
        }
        return ServerResponse.createByErrorMsg("????????????");
    }






    public ServerResponse auth(String realName, String idCard, String img1key, String img2key, String img3key, HttpServletRequest request) {
        if (StringUtils.isBlank(realName) ||
                StringUtils.isBlank(idCard))
//                StringUtils.isBlank(img1key) ||
//                StringUtils.isBlank(img2key))
        {

            return ServerResponse.createByErrorMsg("??????????????????");
        }

        User user = getCurrentRefreshUser(request);
        if (user == null) {
            return ServerResponse.createByErrorMsg("????????????");
        }

        if (((0 != user.getIsActive().intValue())) & ((3 != user.getIsActive().intValue()) ))
        {
            return ServerResponse.createByErrorMsg("????????????????????????");
        }

        user.setNickName(realName);
        user.setRealName(realName);
        user.setIdCard(idCard);

        user.setImg1Key(img1key);
        user.setImg2Key(img2key);
        user.setImg3Key(img3key);
        user.setIsActive(Integer.valueOf(1));

        log.info("##### ???????????? ####,??????????????? = {} ???????????? = {}", user
                .getUserAmt(), user.getEnableAmt());

        int updateCount = this.userMapper.updateByPrimaryKeySelective(user);
        if (updateCount > 0) {
            return ServerResponse.createBySuccessMsg("???????????????");
        }
        return ServerResponse.createByErrorMsg("??????????????????");
    }



    public ServerResponse transAmt(Integer amt, Integer type, HttpServletRequest request) {
        User user = getCurrentRefreshUser(request);
        if (amt.intValue() <= 0) {
            return ServerResponse.createByErrorMsg("???????????????");
        }


        if (1 == type.intValue()) {
            if (user.getEnableAmt().compareTo(new BigDecimal(amt.intValue())) == -1) {
                return ServerResponse.createByErrorMsg("??????????????????????????????");
            }

            BigDecimal userAmt = user.getUserAmt().subtract(new BigDecimal(amt.intValue()));
            BigDecimal enableAmt = user.getEnableAmt().subtract(new BigDecimal(amt.intValue()));
            BigDecimal userIndexAmt = user.getUserIndexAmt().add(new BigDecimal(amt.intValue()));
            BigDecimal enableIndexAmt = user.getEnableIndexAmt().add(new BigDecimal(amt.intValue()));

            user.setUserAmt(userAmt);
            user.setEnableAmt(enableAmt);
            user.setUserIndexAmt(userIndexAmt);
            user.setEnableIndexAmt(enableIndexAmt);
            int updateCount = this.userMapper.updateByPrimaryKeySelective(user);
            if (updateCount > 0) {
                saveAmtTransLog(user, type, amt);
                return ServerResponse.createBySuccessMsg("????????????");
            }
            return ServerResponse.createByErrorMsg("????????????");
        }



        if (2 == type.intValue()) {
            if (user.getEnableIndexAmt().compareTo(new BigDecimal(amt.intValue())) == -1) {
                return ServerResponse.createByErrorMsg("??????????????????????????????");
            }

            BigDecimal userAmt = user.getUserAmt().add(new BigDecimal(amt.intValue()));
            BigDecimal enableAmt = user.getEnableAmt().add(new BigDecimal(amt.intValue()));
            BigDecimal userIndexAmt = user.getUserIndexAmt().subtract(new BigDecimal(amt.intValue()));
            BigDecimal enableIndexAmt = user.getEnableIndexAmt().subtract(new BigDecimal(amt.intValue()));

            user.setUserAmt(userAmt);
            user.setEnableAmt(enableAmt);
            user.setUserIndexAmt(userIndexAmt);
            user.setEnableIndexAmt(enableIndexAmt);
            int updateCount = this.userMapper.updateByPrimaryKeySelective(user);
            if (updateCount > 0) {
                saveAmtTransLog(user, type, amt);
                return ServerResponse.createBySuccessMsg("????????????");
            }
            return ServerResponse.createByErrorMsg("????????????");
        }



        if (3 == type.intValue()) {
            if (user.getEnableAmt().compareTo(new BigDecimal(amt.intValue())) == -1) {
                return ServerResponse.createByErrorMsg("??????????????????????????????");
            }

            BigDecimal userAmt = user.getUserAmt().subtract(new BigDecimal(amt.intValue()));
            BigDecimal enableAmt = user.getEnableAmt().subtract(new BigDecimal(amt.intValue()));
            BigDecimal userFutAmt = user.getUserFutAmt().add(new BigDecimal(amt.intValue()));
            BigDecimal enableFutAmt = user.getEnableFutAmt().add(new BigDecimal(amt.intValue()));

            user.setUserAmt(userAmt);
            user.setEnableAmt(enableAmt);
            user.setUserFutAmt(userFutAmt);
            user.setEnableFutAmt(enableFutAmt);
            int updateCount = this.userMapper.updateByPrimaryKeySelective(user);
            if (updateCount > 0) {
                saveAmtTransLog(user, type, amt);
                return ServerResponse.createBySuccessMsg("????????????");
            }
            return ServerResponse.createByErrorMsg("????????????");
        }



        if (4 == type.intValue()) {
            if (user.getEnableFutAmt().compareTo(new BigDecimal(amt.intValue())) == -1) {
                return ServerResponse.createByErrorMsg("??????????????????????????????");
            }

            BigDecimal userAmt = user.getUserAmt().add(new BigDecimal(amt.intValue()));
            BigDecimal enableAmt = user.getEnableAmt().add(new BigDecimal(amt.intValue()));
            BigDecimal userFutAmt = user.getUserFutAmt().subtract(new BigDecimal(amt.intValue()));
            BigDecimal enableFutAmt = user.getEnableFutAmt().subtract(new BigDecimal(amt.intValue()));

            user.setUserAmt(userAmt);
            user.setEnableAmt(enableAmt);
            user.setUserFutAmt(userFutAmt);
            user.setEnableFutAmt(enableFutAmt);

            int updateCount = this.userMapper.updateByPrimaryKeySelective(user);
            if (updateCount > 0) {
                saveAmtTransLog(user, type, amt);
                return ServerResponse.createBySuccessMsg("????????????");
            }
            return ServerResponse.createByErrorMsg("????????????");
        }


        return ServerResponse.createByErrorMsg("????????????");
    }


    private void saveAmtTransLog(User user, Integer type, Integer amt) {
        String amtFrom = "";
        String amtTo = "";
        if (1 == type.intValue()) {
            amtFrom = "??????";
            amtTo = "??????";
        }
        else if (2 == type.intValue()) {
            amtFrom = "??????";
            amtTo = "??????";
        }
        else if (3 == type.intValue()) {
            amtFrom = "??????";
            amtTo = "??????";
        }
        else if (4 == type.intValue()) {
            amtFrom = "??????";
            amtTo = "??????";
        }

        SiteAmtTransLog siteAmtTransLog = new SiteAmtTransLog();
        siteAmtTransLog.setUserId(user.getId());
        siteAmtTransLog.setRealName(user.getRealName());
        siteAmtTransLog.setAgentId(user.getAgentId());
        siteAmtTransLog.setAmtFrom(amtFrom);
        siteAmtTransLog.setAmtTo(amtTo);
        siteAmtTransLog.setTransAmt(new BigDecimal(amt.intValue()));
        siteAmtTransLog.setAddTime(new Date());
        this.siteAmtTransLogMapper.insert(siteAmtTransLog);
    }




    public void ForceSellTask() {
        List<Integer> userIdList = this.iUserPositionService.findDistinctUserIdList();

        log.info("????????????????????????????????? ??? {}", Integer.valueOf(userIdList.size()));

        for (int i = 0; i < userIdList.size(); i++) {
            log.info("=====================");
            Integer userId = (Integer)userIdList.get(i);
            User user = this.userMapper.selectByPrimaryKey(userId);
            if(user == null){
                continue;
            }


            List<UserPosition> userPositions = this.iUserPositionService.findPositionByUserIdAndSellIdIsNull(userId);

            log.info("??????id = {} ?????? = {} ????????????????????? {}", new Object[] { userId, user.getRealName(), Integer.valueOf(userPositions.size()) });


            BigDecimal enable_user_amt = user.getEnableAmt();


            BigDecimal all_freez_amt = new BigDecimal("0");
            for (UserPosition position : userPositions) {

                BigDecimal actual_amt = position.getOrderTotalPrice().divide(new BigDecimal(position
                        .getOrderLever().intValue()), 2, 4);



                all_freez_amt = all_freez_amt.add(actual_amt);
            }


            BigDecimal all_profit_and_lose = new BigDecimal("0");
            PositionVO positionVO = this.iUserPositionService.findUserPositionAllProfitAndLose(userId);
            all_profit_and_lose = positionVO.getAllProfitAndLose();
            SiteSetting siteSetting = this.iSiteSettingService.getSiteSetting();
            BigDecimal force_stop_percent = siteSetting.getForceStopPercent();
            /*BigDecimal force_stop_amt = force_stop_percent.multiply(all_freez_amt);
            BigDecimal user_force_amt = enable_user_amt.add(force_stop_amt);
            boolean isProfit = false;
            isProfit = (all_profit_and_lose.compareTo(new BigDecimal("0")) == -1 && user_force_amt.compareTo(all_profit_and_lose.negate()) != 1);
            */
            BigDecimal force_stop_amt = enable_user_amt.add(all_freez_amt);

            //(??????)??????????????? = (?????????????????? + ???????????????) *  0.8
            BigDecimal user_force_amt = force_stop_percent.multiply(force_stop_amt);
            BigDecimal fu_user_force_amt = user_force_amt.negate(); //????????????
            log.info("??????????????????????????? = {}", user_force_amt);

            boolean isProfit = false;

            //?????????<=0  ??????  ??????????????????>=?????????
            isProfit = (all_profit_and_lose.compareTo(new BigDecimal("0")) < 1 && fu_user_force_amt.compareTo(all_profit_and_lose) > -1);
            if (isProfit) {
                log.info("???????????????????????????????????????");

                int[] arrs = new int[userPositions.size()];
                for (int k = 0; k < userPositions.size(); k++) {
                    UserPosition position = (UserPosition)userPositions.get(k);
                    arrs[k] = position.getId().intValue();
                    try {
                        if(!DateTimeUtil.sameDate(DateTimeUtil.getCurrentDate(),position.getBuyOrderTime())){
                            this.iUserPositionService.sell(position.getPositionSn(), 0);
                        }

                    } catch (Exception e) {
                        log.error("[????????????????????????]??????????????????...");
                    }
                }


                SiteTaskLog siteTaskLog = new SiteTaskLog();
                siteTaskLog.setTaskType("????????????-????????????");
                String accountType = (user.getAccountType().intValue() == 0) ? "????????????" : "????????????";
                String taskcnt = accountType + "-" + user.getRealName() + "?????????[??????????????????????????????] ??????id = " + user.getId() + ", ???????????? = " + enable_user_amt + "??????????????? = " + all_freez_amt + ", ???????????? = " + force_stop_percent + ", ?????????" + all_profit_and_lose + ", ?????????:" + user_force_amt;




                siteTaskLog.setTaskCnt(taskcnt);
                String tasktarget = "????????????" + userPositions.size() + "?????????????????????, ????????????" + Arrays.toString(arrs);
                siteTaskLog.setTaskTarget(tasktarget);
                siteTaskLog.setAddTime(new Date());
                siteTaskLog.setIsSuccess(Integer.valueOf(0));
                siteTaskLog.setErrorMsg("");
                int insertTaskCount = this.siteTaskLogMapper.insert(siteTaskLog);
                if (insertTaskCount > 0) {
                    log.info("[????????????????????????]??????????????????task????????????");
                } else {
                    log.info("[????????????????????????]??????????????????task????????????");
                }
            } else {
                log.info("???????????????????????????????????????????????????...");
            }

            log.info("=====================");
        }
    }

    /*???????????????-??????????????????-????????????*/
    public void ForceSellOneStockTask() {
        List<Integer> userIdList = this.iUserPositionService.findDistinctUserIdList();
        log.info("????????????????????????????????? ??? {}", Integer.valueOf(userIdList.size()));
        SiteSetting siteSetting = this.iSiteSettingService.getSiteSetting();
        BigDecimal force_stop_percent = siteSetting.getForceStopPercent();
        for (int i = 0; i < userIdList.size(); i++) {
            log.info("=====================");
            Integer userId = (Integer)userIdList.get(i);
            User user = this.userMapper.selectByPrimaryKey(userId);
            if(user == null){
                continue;
            }
            List<UserPosition> userPositions = this.iUserPositionService.findPositionByUserIdAndSellIdIsNull(userId);
            log.info("??????id = {} ?????? = {} ????????????????????? {}", new Object[] { userId, user.getRealName(), Integer.valueOf(userPositions.size()) });

            BigDecimal enable_user_amt = user.getEnableAmt();
            BigDecimal all_freez_amt = new BigDecimal("0");
            for (UserPosition position : userPositions) {
                PositionProfitVO positionProfitVO = iUserPositionService.getPositionProfitVO(position);

                //(??????)??????????????????????????? = (??????????????? / ?????? + ???????????????) *  0.8
                BigDecimal user_force_amt = position.getOrderTotalPrice().divide(new BigDecimal(position.getOrderLever())).add(position.getMarginAdd()).multiply(force_stop_percent);
                BigDecimal fu_user_force_amt = user_force_amt.negate(); //????????????
                log.info("??????????????????????????? = {}", user_force_amt);
                /*if("1601344387923698".equals( position.getPositionSn())){
                    log.info("test = {}", position.getPositionSn());
                }*/
                boolean isProfit = false;
                //?????????<=0  ??????  ??????????????????>=?????????
                isProfit = (positionProfitVO.getAllProfitAndLose().compareTo(new BigDecimal("0")) < 1 && fu_user_force_amt.compareTo(positionProfitVO.getAllProfitAndLose()) > -1);
                if(isProfit && !DateTimeUtil.sameDate(DateTimeUtil.getCurrentDate(),position.getBuyOrderTime())){
                    try {
                        this.iUserPositionService.sell(position.getPositionSn(), 0);

                        SiteTaskLog siteTaskLog = new SiteTaskLog();
                        siteTaskLog.setTaskType("??????????????????-????????????");
                        String accountType = (user.getAccountType().intValue() == 0) ? "????????????" : "????????????";
                        String taskcnt = accountType + "-" + user.getRealName() + "?????????[??????????????????????????????] ??????id = " + user.getId() + ", ???????????? = " + enable_user_amt + "??????????????? = " + all_freez_amt + ", ???????????? = " + force_stop_percent + ", ?????????" + positionProfitVO.getAllProfitAndLose() + ", ?????????:" + user_force_amt;
                        siteTaskLog.setTaskCnt(taskcnt);
                        String tasktarget = "???????????????????????????" + position.getPositionSn();
                        siteTaskLog.setTaskTarget(tasktarget);
                        siteTaskLog.setAddTime(new Date());
                        siteTaskLog.setIsSuccess(Integer.valueOf(0));
                        siteTaskLog.setErrorMsg("");
                        int insertTaskCount = this.siteTaskLogMapper.insert(siteTaskLog);
                        if (insertTaskCount > 0) {
                            log.info("[????????????????????????]??????????????????task????????????");
                        } else {
                            log.info("[????????????????????????]??????????????????task????????????");
                        }
                    } catch (Exception e) {
                        log.error("[????????????????????????]??????????????????...");
                    }
                }

            }
            log.info("=====================");
        }
    }

    /*?????????????????????-??????????????????????????????*/
    public void ForceSellMessageTask() {
        List<Integer> userIdList = this.iUserPositionService.findDistinctUserIdList();

        log.info("????????????????????????????????? ??? {}", Integer.valueOf(userIdList.size()));

        for (int i = 0; i < userIdList.size(); i++) {
            log.info("=====================");
            Integer userId = (Integer)userIdList.get(i);
            User user = this.userMapper.selectByPrimaryKey(userId);
            if(user == null){
                continue;
            }


            List<UserPosition> userPositions = this.iUserPositionService.findPositionByUserIdAndSellIdIsNull(userId);

            log.info("??????id = {} ?????? = {} ????????????????????? {}", new Object[] { userId, user.getRealName(), Integer.valueOf(userPositions.size()) });


            BigDecimal enable_user_amt = user.getEnableAmt();


            BigDecimal all_freez_amt = new BigDecimal("0");
            for (UserPosition position : userPositions) {

                BigDecimal actual_amt = position.getOrderTotalPrice().divide(new BigDecimal(position
                        .getOrderLever().intValue()), 2, 4);



                all_freez_amt = all_freez_amt.add(actual_amt);
            }


            BigDecimal all_profit_and_lose = new BigDecimal("0");
            PositionVO positionVO = this.iUserPositionService.findUserPositionAllProfitAndLose(userId);
            all_profit_and_lose = positionVO.getAllProfitAndLose();
            SiteSetting siteSetting = this.iSiteSettingService.getSiteSetting();
            BigDecimal force_stop_percent = siteSetting.getForceStopRemindRatio();
            /*BigDecimal force_stop_amt = force_stop_percent.multiply(all_freez_amt);
            BigDecimal user_force_amt = enable_user_amt.add(force_stop_amt);
            boolean isProfit = false;
            isProfit = (all_profit_and_lose.compareTo(new BigDecimal("0")) == -1 && user_force_amt.compareTo(all_profit_and_lose.negate()) != 1);
            */
            BigDecimal force_stop_amt = enable_user_amt.add(all_freez_amt);

            //(??????)??????????????? = (?????????????????? + ???????????????) *  0.8
            BigDecimal user_force_amt = force_stop_percent.multiply(force_stop_amt);
            BigDecimal fu_user_force_amt = user_force_amt.negate(); //????????????
            log.info("??????????????????????????? = {}", user_force_amt);

            boolean isProfit = false;

            //?????????<=0  ??????  ??????????????????>=?????????
            isProfit = (all_profit_and_lose.compareTo(new BigDecimal("0")) < 1 && fu_user_force_amt.compareTo(all_profit_and_lose) > -1);
            if (isProfit) {
                log.info("???????????????????????????????????????");
                int count = iSiteMessageService.getIsDayCount(userId,"????????????");
                if(count == 0){
                    //?????????????????????????????????????????????
                    SiteMessage siteMessage = new SiteMessage();
                    siteMessage.setUserId(userId);
                    siteMessage.setUserName(user.getRealName());
                    siteMessage.setTypeName("????????????");
                    siteMessage.setStatus(1);
                    siteMessage.setContent("????????????????????????????????????id = "+user.getId() + ", ???????????? = " + enable_user_amt + "??????????????? = " + all_freez_amt + ", ???????????? = " + force_stop_percent + ", ?????????" + all_profit_and_lose + ", ?????????:" + user_force_amt +"????????????????????????");
                    siteMessage.setAddTime(DateTimeUtil.getCurrentDate());
                    iSiteMessageService.insert(siteMessage);
                }

            } else {
                log.info("???????????????????????????????????????????????????...");
            }

            log.info("=====================");
        }
    }





    public void ForceSellIndexTask() {
        List<Integer> userIdList = this.iUserIndexPositionService.findDistinctUserIdList();

        log.info("????????? ???????????? ??????????????? ??? {}", Integer.valueOf(userIdList.size()));

        for (int i = 0; i < userIdList.size(); i++) {
            log.info("=====================");
            Integer userId = (Integer)userIdList.get(i);
            User user = this.userMapper.selectByPrimaryKey(userId);
            if(user == null){
                continue;
            }


            List<UserIndexPosition> userIndexPositions = this.iUserIndexPositionService.findIndexPositionByUserIdAndSellPriceIsNull(userId);

            log.info("??????id = {} ?????? = {} ??????????????????: {}", new Object[] { userId, user
                    .getRealName(), Integer.valueOf(userIndexPositions.size()) });


            IndexPositionVO indexPositionVO = this.iUserIndexPositionService.findUserIndexPositionAllProfitAndLose(userId);


            BigDecimal enable_index_amt = user.getEnableIndexAmt();


            BigDecimal all_freez_amt = indexPositionVO.getAllIndexFreezAmt();

            BigDecimal all_profit_and_lose = indexPositionVO.getAllIndexProfitAndLose();

            log.info("?????? {} ???????????? = {} ?????????????????? = {} ??????????????????????????? = {}", new Object[] { userId, enable_index_amt, all_freez_amt, all_profit_and_lose });



            SiteIndexSetting siteIndexSetting = this.iSiteIndexSettingService.getSiteIndexSetting();
            BigDecimal force_stop_percent = siteIndexSetting.getForceSellPercent();
            BigDecimal force_stop_amt = enable_index_amt.add(all_freez_amt);

            //(??????)??????????????? = (?????????????????? + ???????????????) *  0.8
            BigDecimal user_force_amt = force_stop_percent.multiply(force_stop_amt);
            BigDecimal fu_user_force_amt = user_force_amt.negate(); //????????????
            log.info("??????????????????????????? = {}", user_force_amt);
            boolean isProfit = false;
            //?????????<=0  ??????  ??????????????????>=?????????
            isProfit = (all_profit_and_lose.compareTo(new BigDecimal("0")) < 1 && fu_user_force_amt.compareTo(all_profit_and_lose) > -1);

            if (isProfit) {
                log.info("?????????????????????????????????????????????");

                int[] arrs = new int[userIndexPositions.size()];
                for (int k = 0; k < userIndexPositions.size(); k++) {
                    UserIndexPosition userIndexPosition = (UserIndexPosition)userIndexPositions.get(k);
                    arrs[k] = userIndexPosition.getId().intValue();
                    try {
                        this.iUserIndexPositionService.sellIndex(userIndexPosition.getPositionSn(), 0);
                    }
                    catch (Exception e) {
                        log.error("[????????????????????????]????????????????????????...");
                    }
                }


                SiteTaskLog siteTaskLog = new SiteTaskLog();
                siteTaskLog.setTaskType("????????????-????????????");
                String accountType = (user.getAccountType().intValue() == 0) ? "????????????" : "????????????";
                String taskcnt = accountType + "-" + user.getRealName() + "????????? [??????????????????????????????] ?????? id = " + user.getId() + ", ???????????? = " + enable_index_amt + ", ???????????? = " + all_freez_amt + ", ???????????? = " + force_stop_percent + ", ????????? = " + all_profit_and_lose + ", ????????? = " + user_force_amt;




                siteTaskLog.setTaskCnt(taskcnt);

                String tasktarget = "????????????" + userIndexPositions.size() + "?????????????????????, ????????????" + Arrays.toString(arrs);
                siteTaskLog.setTaskTarget(tasktarget);
                siteTaskLog.setAddTime(new Date());
                siteTaskLog.setIsSuccess(Integer.valueOf(0));
                siteTaskLog.setErrorMsg("");
                int insertTaskCount = this.siteTaskLogMapper.insert(siteTaskLog);
                if (insertTaskCount > 0) {
                    log.info("[????????????????????????] ?????????????????? ?????? task????????????");
                } else {
                    log.info("[????????????????????????] ?????????????????? ?????? task????????????");
                }
            } else {
                log.info("??????????????????????????????????????????, ??????????????????...");
            }

            log.info("=====================");
        }
    }

    /*??????????????????????????????????????????????????????*/
    public void ForceSellIndexsMessageTask() {
        List<Integer> userIdList = this.iUserIndexPositionService.findDistinctUserIdList();

        log.info("????????? ???????????? ??????????????? ??? {}", Integer.valueOf(userIdList.size()));

        for (int i = 0; i < userIdList.size(); i++) {
            log.info("=====================");
            Integer userId = (Integer)userIdList.get(i);
            User user = this.userMapper.selectByPrimaryKey(userId);
            if(user == null){
                continue;
            }


            List<UserIndexPosition> userIndexPositions = this.iUserIndexPositionService.findIndexPositionByUserIdAndSellPriceIsNull(userId);

            log.info("??????id = {} ?????? = {} ??????????????????: {}", new Object[] { userId, user
                    .getRealName(), Integer.valueOf(userIndexPositions.size()) });


            IndexPositionVO indexPositionVO = this.iUserIndexPositionService.findUserIndexPositionAllProfitAndLose(userId);


            BigDecimal enable_index_amt = user.getEnableIndexAmt();


            BigDecimal all_freez_amt = indexPositionVO.getAllIndexFreezAmt();

            BigDecimal all_profit_and_lose = indexPositionVO.getAllIndexProfitAndLose();

            log.info("?????? {} ???????????? = {} ?????????????????? = {} ??????????????????????????? = {}", new Object[] { userId, enable_index_amt, all_freez_amt, all_profit_and_lose });



            SiteIndexSetting siteIndexSetting = this.iSiteIndexSettingService.getSiteIndexSetting();
            BigDecimal force_stop_percent = siteIndexSetting.getForceStopRemindRatio();
            BigDecimal force_stop_amt = enable_index_amt.add(all_freez_amt);

            //(??????)??????????????? = (?????????????????? + ???????????????) *  0.8
            BigDecimal user_force_amt = force_stop_percent.multiply(force_stop_amt);
            BigDecimal fu_user_force_amt = user_force_amt.negate(); //????????????
            log.info("??????????????????????????? = {}", user_force_amt);
            boolean isProfit = false;
            //?????????<=0  ??????  ??????????????????>=?????????
            isProfit = (all_profit_and_lose.compareTo(new BigDecimal("0")) < 1 && fu_user_force_amt.compareTo(all_profit_and_lose) > -1);

            if (isProfit) {
                log.info("?????????????????????????????????????????????");

                int count = iSiteMessageService.getIsDayCount(userId,"????????????");
                if(count == 0){
                    //?????????????????????????????????????????????
                    SiteMessage siteMessage = new SiteMessage();
                    siteMessage.setUserId(userId);
                    siteMessage.setUserName(user.getRealName());
                    siteMessage.setTypeName("????????????");
                    siteMessage.setStatus(1);
                    siteMessage.setContent("????????????????????????????????????id = "+user.getId() + ", ???????????? = " + enable_index_amt + ", ???????????? = " + all_freez_amt + ", ???????????? = " + force_stop_percent + ", ????????? = " + all_profit_and_lose + ", ????????? = " + user_force_amt +"????????????????????????");
                    siteMessage.setAddTime(DateTimeUtil.getCurrentDate());
                    iSiteMessageService.insert(siteMessage);
                }

            } else {
                log.info("??????????????????????????????????????????, ??????????????????...");
            }

            log.info("=====================");
        }
    }

    public void qh1() {
        this.stockPoll.qh1();
    }

    public void zs1() {
        this.stockPoll.zs1();
    }

    public void ForceSellFuturesTask() {
        List<Integer> userIdList = this.iUserFuturesPositionService.findDistinctUserIdList();


        for (int i = 0; i < userIdList.size(); i++) {
            log.info("===================== \n");
            Integer userId = (Integer)userIdList.get(i);
            System.out.println("userId"+userId);
            User user = this.userMapper.selectByPrimaryKey(userId);
            if(user == null){
                continue;
            }

            List<UserFuturesPosition> userFuturesPositions = this.iUserFuturesPositionService.findFuturesPositionByUserIdAndSellPriceIsNull(userId);
            System.out.println("userFuturesPositions"+userFuturesPositions);
            System.out.println("??????");
            log.info("??????id = {} ?????? = {} ???????????????????????? {}", new Object[] { userId, user
                    .getRealName(), Integer.valueOf(userFuturesPositions.size()) });

            FuturesPositionVO futuresPositionVO = this.iUserFuturesPositionService.findUserFuturesPositionAllProfitAndLose(userId);

            BigDecimal enable_Futures_amt = user.getEnableFutAmt();

            BigDecimal all_deposit_amt = futuresPositionVO.getAllFuturesDepositAmt();

            BigDecimal all_profit_and_lose = futuresPositionVO.getAllFuturesProfitAndLose();

            log.info("?????? {} ???????????? = {} ?????????????????? = {} ??????????????????????????? = {}", new Object[] { userId, enable_Futures_amt, all_deposit_amt, all_profit_and_lose });



            SiteFuturesSetting siteFuturesSetting = this.iSiteFuturesSettingService.getSetting();
            BigDecimal force_stop_percent = siteFuturesSetting.getForceSellPercent();
            BigDecimal force_stop_amt = enable_Futures_amt.add(all_deposit_amt);

            //(??????)??????????????? = (?????????????????? + ???????????????) *  0.8
            BigDecimal user_force_amt = force_stop_percent.multiply(force_stop_amt);
            BigDecimal fu_user_force_amt = user_force_amt.negate(); //????????????
            log.info("??????????????????????????? = {}", user_force_amt);

            boolean isProfit = false;

            //?????????<=0  ??????  ??????????????????>=?????????
            isProfit = (all_profit_and_lose.compareTo(new BigDecimal("0")) < 1 && fu_user_force_amt.compareTo(all_profit_and_lose) > -1);

            if (isProfit) {
                log.info("?????????????????? {} ????????? ?????? ?????????", user.getId());

                int[] arrs = new int[userFuturesPositions.size()];
                for (int k = 0; k < userFuturesPositions.size(); k++) {
                    UserFuturesPosition userFuturesPosition = (UserFuturesPosition)userFuturesPositions.get(k);
                    arrs[k] = userFuturesPosition.getId().intValue();
                    try {
                        this.iUserFuturesPositionService.sellFutures(userFuturesPosition.getPositionSn(), 0);
                    }
                    catch (Exception e) {
                        log.error("[????????????????????????] ???????????? ?????? ??????...");
                    }
                }

                SiteTaskLog siteTaskLog = new SiteTaskLog();
                siteTaskLog.setTaskType("????????????-????????????");
                String accountType = (user.getAccountType().intValue() == 0) ? "????????????" : "????????????";
                String taskcnt = accountType + "-" + user.getRealName() + "?????????[??????????????????????????????]??????id = " + user.getId() + ", ???????????? = " + enable_Futures_amt + ", ??????????????? = " + all_deposit_amt + ", ???????????? = " + force_stop_percent + ", ?????????" + all_profit_and_lose + ", ?????????:" + user_force_amt;




                siteTaskLog.setTaskCnt(taskcnt);

                String tasktarget = "????????????" + userFuturesPositions.size() + "?????????????????????, ????????????" + Arrays.toString(arrs);
                siteTaskLog.setTaskTarget(tasktarget);
                siteTaskLog.setAddTime(new Date());
                siteTaskLog.setIsSuccess(Integer.valueOf(0));
                siteTaskLog.setErrorMsg("");
                int insertTaskCount = this.siteTaskLogMapper.insert(siteTaskLog);
                if (insertTaskCount > 0) {
                    log.info("[????????????????????????]?????????????????? ?????? task????????????");
                } else {
                    log.info("[????????????????????????]?????????????????? ?????? task????????????");
                }
            } else {
                log.info("????????????;???????????????????????????????????????????????????...");
            }
            log.info("===================== \n");
        }
    }

    public void ForceSellFuturesMessageTask() {
        List<Integer> userIdList = this.iUserFuturesPositionService.findDistinctUserIdList();


        for (int i = 0; i < userIdList.size(); i++) {
            log.info("===================== \n");
            Integer userId = (Integer)userIdList.get(i);
            System.out.println("userId"+userId);
            User user = this.userMapper.selectByPrimaryKey(userId);
            if(user == null){
                continue;
            }

            List<UserFuturesPosition> userFuturesPositions = this.iUserFuturesPositionService.findFuturesPositionByUserIdAndSellPriceIsNull(userId);
            System.out.println("userFuturesPositions"+userFuturesPositions);
            System.out.println("??????");
            log.info("??????id = {} ?????? = {} ???????????????????????? {}", new Object[] { userId, user
                    .getRealName(), Integer.valueOf(userFuturesPositions.size()) });

            FuturesPositionVO futuresPositionVO = this.iUserFuturesPositionService.findUserFuturesPositionAllProfitAndLose(userId);

            BigDecimal enable_Futures_amt = user.getEnableFutAmt();

            BigDecimal all_deposit_amt = futuresPositionVO.getAllFuturesDepositAmt();

            BigDecimal all_profit_and_lose = futuresPositionVO.getAllFuturesProfitAndLose();

            log.info("?????? {} ???????????? = {} ?????????????????? = {} ??????????????????????????? = {}", new Object[] { userId, enable_Futures_amt, all_deposit_amt, all_profit_and_lose });

            SiteFuturesSetting siteFuturesSetting = this.iSiteFuturesSettingService.getSetting();
            BigDecimal force_stop_percent = siteFuturesSetting.getForceStopRemindRatio();
            BigDecimal force_stop_amt = enable_Futures_amt.add(all_deposit_amt);

            //(??????)??????????????? = (?????????????????? + ???????????????) *  0.4
            BigDecimal user_force_amt = force_stop_percent.multiply(force_stop_amt);
            BigDecimal fu_user_force_amt = user_force_amt.negate(); //????????????
            log.info("????????????????????????????????? = {}", user_force_amt);

            boolean isProfit = false;

            //?????????<=0  ??????  ??????????????????>=?????????
            isProfit = (all_profit_and_lose.compareTo(new BigDecimal("0")) < 1 && fu_user_force_amt.compareTo(all_profit_and_lose) > -1);

            if (isProfit) {
                log.info("?????????????????? {} ????????? ?????? ?????????", user.getId());
                int count = iSiteMessageService.getIsDayCount(userId,"????????????");
                if(count == 0){
                    //?????????????????????????????????????????????
                    SiteMessage siteMessage = new SiteMessage();
                    siteMessage.setUserId(userId);
                    siteMessage.setUserName(user.getRealName());
                    siteMessage.setTypeName("????????????");
                    siteMessage.setStatus(1);
                    siteMessage.setContent("????????????????????????????????????id = "+user.getId() + ", ???????????? = " + enable_Futures_amt + ", ??????????????? = " + all_deposit_amt + ", ???????????? = " + force_stop_percent + ", ?????????" + all_profit_and_lose + ", ?????????:" + user_force_amt +"????????????????????????");
                    siteMessage.setAddTime(DateTimeUtil.getCurrentDate());
                    iSiteMessageService.insert(siteMessage);
                }


            } else {
                log.info("????????????;???????????????????????????????????????????????????...");
            }
            log.info("===================== \n");
        }
    }




    public ServerResponse listByAgent(String realName, String phone, Integer agentId, Integer accountType, int pageNum, int pageSize, HttpServletRequest request) {
        SiteSetting siteSetting = this.iSiteSettingService.getSiteSetting();
        SiteIndexSetting siteIndexSetting = this.iSiteIndexSettingService.getSiteIndexSetting();
        SiteFuturesSetting siteFuturesSetting = this.iSiteFuturesSettingService.getSetting();


        AgentUser currentAgent = this.iAgentUserService.getCurrentAgent(request);

        if (agentId != null) {
            AgentUser agentUser = this.agentUserMapper.selectByPrimaryKey(agentId);
            if (agentUser.getParentId() != currentAgent.getId()) {
                return ServerResponse.createByErrorMsg("???????????????????????????????????????");
            }
        }
        Integer searchId = null;
        if (agentId == null) {
            searchId = currentAgent.getId();
        } else {
            searchId = agentId;
        }

        PageHelper.startPage(pageNum, pageSize);

        List<User> users = this.userMapper.listByAgent(realName, phone, searchId, accountType);

        List<AgentUserListVO> agentUserListVOS = Lists.newArrayList();
        for (User user : users) {
            AgentUserListVO agentUserListVO = assembleAgentUserListVO(user, siteSetting
                    .getForceStopPercent(), siteIndexSetting
                    .getForceSellPercent(), siteFuturesSetting.getForceSellPercent());
            agentUserListVOS.add(agentUserListVO);
        }

        PageInfo pageInfo = new PageInfo(users);
        pageInfo.setList(agentUserListVOS);

        return ServerResponse.createBySuccess(pageInfo);
    }



    public ServerResponse addSimulatedAccount(Integer agentId, String phone, String pwd, String amt, Integer accountType, HttpServletRequest request) {
        if (StringUtils.isBlank(phone) || StringUtils.isBlank(pwd)) {
            return ServerResponse.createByErrorMsg("??????????????????");
        }


        User dbUser = this.userMapper.findByPhone(phone);
        if (dbUser != null) {
            return ServerResponse.createByErrorMsg("??????????????????");
        }


        if ((new BigDecimal(amt)).compareTo(new BigDecimal("200000")) == 1) {
            return ServerResponse.createByErrorMsg("??????????????????????????????20???");
        }

        amt = "0";   //??????????????????????????????????????????0
        User user = new User();
        user.setAccountType(accountType);
        user.setPhone(phone);
        user.setUserPwd(pwd);
        user.setUserAmt(new BigDecimal(amt));
        user.setEnableAmt(new BigDecimal(amt));
        user.setSumChargeAmt(new BigDecimal("0"));
        user.setSumBuyAmt(new BigDecimal("0"));
        user.setIsLock(Integer.valueOf(0));
        user.setIsLogin(Integer.valueOf(0));
        user.setIsActive(Integer.valueOf(0));
        user.setRegTime(new Date());

        if (accountType.intValue() == 1) {
            user.setNickName("????????????");
        }

        user.setUserIndexAmt(new BigDecimal("0"));
        user.setEnableIndexAmt(new BigDecimal("0"));
        user.setUserFutAmt(new BigDecimal("0"));
        user.setEnableFutAmt(new BigDecimal("0"));

        if (agentId != null) {
            AgentUser agentUser = this.agentUserMapper.selectByPrimaryKey(agentId);
            user.setAgentName(agentUser.getAgentName());
            user.setAgentId(agentUser.getId());
        }

        int insertCount = this.userMapper.insert(user);
        if (insertCount > 0) {
            return ServerResponse.createBySuccessMsg("??????????????????");
        }
        return ServerResponse.createByErrorMsg("??????????????????");
    }





    public ServerResponse listByAdmin(String realName, String phone, Integer agentId, Integer accountType, int pageNum, int pageSize, HttpServletRequest request) {
        PageHelper.startPage(pageNum, pageSize);

        List<User> users = this.userMapper.listByAdmin(realName, phone, agentId, accountType);

        PageInfo pageInfo = new PageInfo(users);

        return ServerResponse.createBySuccess(pageInfo);
    }



    public ServerResponse findByUserId(Integer userId) { return ServerResponse.createBySuccess(this.userMapper.selectByPrimaryKey(userId)); }




    public ServerResponse updateLock(Integer userId) {
        User user = this.userMapper.selectByPrimaryKey(userId);
        if (user == null) {
            return ServerResponse.createByErrorMsg("???????????????");
        }

        if (user.getIsLock().intValue() == 1) {
            user.setIsLock(Integer.valueOf(0));
        } else {
            user.setIsLock(Integer.valueOf(1));
        }

        int updateCount = this.userMapper.updateByPrimaryKeySelective(user);
        if (updateCount > 0) {
            return ServerResponse.createBySuccess("????????????");
        }
        return ServerResponse.createByErrorMsg("????????????");
    }



    @Transactional
    public ServerResponse updateAmt(Integer userId, Integer amt, Integer direction) {
        if (userId == null || amt == null || direction == null) {
            return ServerResponse.createByErrorMsg("??????????????????");
        }

        User user = this.userMapper.selectByPrimaryKey(userId);
        if (user == null) {
            return ServerResponse.createByErrorMsg("???????????????");
        }

        BigDecimal user_amt = user.getUserAmt();
        BigDecimal user_enable = user.getEnableAmt();

        BigDecimal user_amt_back = new BigDecimal("0");
        BigDecimal user_enable_back = new BigDecimal("0");
        if (direction.intValue() == 0) {
            user_amt_back = user_amt.add(new BigDecimal(amt.intValue()));
            user_enable_back = user_enable.add(new BigDecimal(amt.intValue()));
        } else if (direction.intValue() == 1) {

            if (user_amt.compareTo(new BigDecimal(amt.intValue())) == -1) {
                return ServerResponse.createByErrorMsg("????????????, ???????????????");
            }
            if (user_enable.compareTo(new BigDecimal(amt.intValue())) == -1) {
                return ServerResponse.createByErrorMsg("????????????, ???????????????");
            }

            user_amt_back = user_amt.subtract(new BigDecimal(amt.intValue()));
            user_enable_back = user_enable.subtract(new BigDecimal(amt.intValue()));
        } else {
            return ServerResponse.createByErrorMsg("??????????????????");
        }


        user.setUserAmt(user_amt_back);
        user.setEnableAmt(user_enable_back);
        this.userMapper.updateByPrimaryKeySelective(user);


        SiteTaskLog siteTaskLog = new SiteTaskLog();
        siteTaskLog.setTaskType("?????????????????????");
        StringBuffer cnt = new StringBuffer();
        cnt.append("????????????????????? - ")
                .append((direction.intValue() == 0) ? "??????" : "??????")
                .append(amt).append("???");
        siteTaskLog.setTaskCnt(cnt.toString());

        StringBuffer target = new StringBuffer();
        target.append("??????id : ").append(user.getId())
                .append("????????? ????????? = ").append(user_amt).append(" ?????? = ").append(user_enable)
                .append("????????? ????????? = ").append(user_amt_back).append(" ?????? = ").append(user_enable_back);
        siteTaskLog.setTaskTarget(target.toString());

        siteTaskLog.setIsSuccess(Integer.valueOf(0));
        siteTaskLog.setAddTime(new Date());

        int insertCount = this.siteTaskLogMapper.insert(siteTaskLog);
        if (insertCount > 0) {
            return ServerResponse.createBySuccessMsg("??????????????????");
        }
        return ServerResponse.createByErrorMsg("??????????????????");
    }




    public ServerResponse delete(Integer userId, HttpServletRequest request) {
        String cookie_name = PropertiesUtil.getProperty("admin.cookie.name");
        String logintoken = CookieUtils.readLoginToken(request, cookie_name);
        String adminJson = RedisShardedPoolUtils.get(logintoken);
        SiteAdmin siteAdmin = (SiteAdmin)JsonUtil.string2Obj(adminJson, SiteAdmin.class);

        log.info("????????? {} ???????????? {}", siteAdmin.getAdminName(), userId);


        int delChargeCount = this.iUserRechargeService.deleteByUserId(userId);
        if (delChargeCount > 0) {
            log.info("?????? ?????? ????????????");
        } else {
            log.info("?????? ?????? ????????????");
        }


        int delWithdrawCount = this.iUserWithdrawService.deleteByUserId(userId);
        if (delWithdrawCount > 0) {
            log.info("?????? ?????? ????????????");
        } else {
            log.info("?????? ?????? ????????????");
        }


        int delCashCount = this.iUserCashDetailService.deleteByUserId(userId);
        if (delCashCount > 0) {
            log.info("?????? ?????? ????????????");
        } else {
            log.info("?????? ?????? ????????????");
        }


        int delPositionCount = this.iUserPositionService.deleteByUserId(userId);
        if (delPositionCount > 0) {
            log.info("?????? ?????? ????????????");
        } else {
            log.info("?????? ?????? ????????????");
        }


        int delLogCount = this.iSiteLoginLogService.deleteByUserId(userId);
        if (delLogCount > 0) {
            log.info("?????? ?????? ????????????");
        } else {
            log.info("?????? ?????? ????????????");
        }


        int delUserCount = this.userMapper.deleteByPrimaryKey(userId);
        if (delUserCount > 0) {
            return ServerResponse.createBySuccessMsg("????????????");
        }
        return ServerResponse.createByErrorMsg("????????????, ????????????");
    }





    public int CountUserSize(Integer accountType) { return this.userMapper.CountUserSize(accountType); }





    public BigDecimal CountUserAmt(Integer accountType) { return this.userMapper.CountUserAmt(accountType); }




    public BigDecimal CountEnableAmt(Integer accountType) { return this.userMapper.CountEnableAmt(accountType); }




    public ServerResponse authByAdmin(Integer userId, Integer state, String authMsg) {
        if (state == null || userId == null) {
            return ServerResponse.createByErrorMsg("id???state????????????");
        }

        User user = this.userMapper.selectByPrimaryKey(userId);
        if (user == null) {
            return ServerResponse.createByErrorMsg("??????????????????");
        }

        if (state.intValue() == 3) {
            if (StringUtils.isBlank(authMsg)) {
                return ServerResponse.createByErrorMsg("????????????????????????");
            }
            user.setAuthMsg(authMsg);
        }

        user.setIsActive(state);

        int updateCount = this.userMapper.updateByPrimaryKeySelective(user);
        if (updateCount > 0) {
            return ServerResponse.createBySuccessMsg("????????????");
        }
        return ServerResponse.createByErrorMsg("????????????");
    }

    @Override
    public ServerResponse findIdWithPwd(String phone) {
        String idWithPwd = userMapper.findIdWithPwd(phone);

        if (idWithPwd==null){
            return ServerResponse.createByErrorMsg("????????????????????????");
        }else {
            return ServerResponse.createBySuccessMsg("???????????????,???????????????");
        }
    }

    @Override
    public ServerResponse updateWithPwd(String with_pwd, String phone) {

        if (StringUtils.isBlank(with_pwd)||StringUtils.isBlank(phone)){
            return ServerResponse.createByErrorMsg("??????????????????");
        }

        String withPwd = userMapper.findWithPwd(with_pwd);

        if (withPwd!=null){
            return ServerResponse.createByErrorMsg("?????????????????????????????????");
        }

        int i = userMapper.updateWithPwd(with_pwd, phone);
        if (i>0){
            return ServerResponse.createBySuccessMsg("???????????????");
        }else {
            return ServerResponse.createByErrorMsg("???????????????");
        }
    }


    private AgentUserListVO assembleAgentUserListVO(User user, BigDecimal forcePercent, BigDecimal indexForcePercent, BigDecimal futuresForcePercent) {
        AgentUserListVO agentUserListVO = new AgentUserListVO();

        agentUserListVO.setId(user.getId());
        agentUserListVO.setAgentId(user.getAgentId());
        agentUserListVO.setAgentName(user.getAgentName());
        agentUserListVO.setPhone(user.getPhone());
        agentUserListVO.setRealName(user.getRealName());
        agentUserListVO.setIdCard(user.getIdCard());
        agentUserListVO.setAccountType(user.getAccountType());
        agentUserListVO.setIsLock(user.getIsLock());
        agentUserListVO.setIsLogin(user.getIsLogin());
        agentUserListVO.setRegAddress(user.getRegAddress());
        agentUserListVO.setIsActive(user.getIsActive());


        agentUserListVO.setUserAmt(user.getUserAmt());
        agentUserListVO.setEnableAmt(user.getEnableAmt());

        agentUserListVO.setUserIndexAmt(user.getUserIndexAmt());
        agentUserListVO.setEnableIndexAmt(user.getEnableIndexAmt());

        agentUserListVO.setUserFuturesAmt(user.getUserFutAmt());
        agentUserListVO.setEnableFuturesAmt(user.getEnableFutAmt());



        PositionVO positionVO = this.iUserPositionService.findUserPositionAllProfitAndLose(user.getId());
        BigDecimal allProfitAndLose = positionVO.getAllProfitAndLose();
        BigDecimal allFreezAmt = positionVO.getAllFreezAmt();
        agentUserListVO.setAllProfitAndLose(allProfitAndLose);
        agentUserListVO.setAllFreezAmt(allFreezAmt);

        BigDecimal forceLine = forcePercent.multiply(allFreezAmt);
        forceLine = forceLine.add(user.getEnableAmt());
        agentUserListVO.setForceLine(forceLine);



        IndexPositionVO indexPositionVO = this.iUserIndexPositionService.findUserIndexPositionAllProfitAndLose(user.getId());
        agentUserListVO.setAllIndexProfitAndLose(indexPositionVO.getAllIndexProfitAndLose());
        agentUserListVO.setAllIndexFreezAmt(indexPositionVO.getAllIndexFreezAmt());

        BigDecimal indexForceLine = indexForcePercent.multiply(indexPositionVO.getAllIndexFreezAmt());
        indexForceLine = indexForceLine.add(user.getEnableIndexAmt());
        agentUserListVO.setIndexForceLine(indexForceLine);



        FuturesPositionVO futuresPositionVO = this.iUserFuturesPositionService.findUserFuturesPositionAllProfitAndLose(user.getId());
        agentUserListVO.setAllFuturesFreezAmt(futuresPositionVO.getAllFuturesDepositAmt());
        agentUserListVO.setAllFuturesProfitAndLose(futuresPositionVO.getAllFuturesProfitAndLose());

        BigDecimal futuresForceLine = futuresForcePercent.multiply(futuresPositionVO.getAllFuturesDepositAmt());
        futuresForceLine = futuresForceLine.add(user.getEnableFutAmt());
        agentUserListVO.setFuturesForceLine(futuresForceLine);



        UserBank userBank = this.iUserBankService.findUserBankByUserId(user.getId());
        if (userBank != null) {
            agentUserListVO.setBankName(userBank.getBankName());
            agentUserListVO.setBankNo(userBank.getBankNo());
            agentUserListVO.setBankAddress(userBank.getBankAddress());
        }

        return agentUserListVO;
    }

    private UserInfoVO assembleUserInfoVO(User user) {
        UserInfoVO userInfoVO = new UserInfoVO();

        userInfoVO.setId(user.getId());
        userInfoVO.setAgentId(user.getAgentId());
        userInfoVO.setAgentName(user.getAgentName());
        userInfoVO.setPhone(user.getPhone());
        userInfoVO.setNickName(user.getNickName());
        userInfoVO.setRealName(user.getRealName());
        userInfoVO.setIdCard(user.getIdCard());
        userInfoVO.setAccountType(user.getAccountType());
        userInfoVO.setRecomPhone(user.getRecomPhone());
        userInfoVO.setIsLock(user.getIsLock());
        userInfoVO.setRegTime(user.getRegTime());
        userInfoVO.setRegIp(user.getRegIp());
        userInfoVO.setRegAddress(user.getRegAddress());
        userInfoVO.setImg1Key(user.getImg1Key());
        userInfoVO.setImg2Key(user.getImg2Key());
        userInfoVO.setImg3Key(user.getImg3Key());
        userInfoVO.setIsActive(user.getIsActive());
        userInfoVO.setAuthMsg(user.getAuthMsg());

        userInfoVO.setEnableAmt(user.getEnableAmt());
        userInfoVO.setTradingAmount(user.getTradingAmount());


        PositionVO positionVO = this.iUserPositionService.findUserPositionAllProfitAndLose(user.getId());
        userInfoVO.setAllFreezAmt(positionVO.getAllFreezAmt());
        BigDecimal allProfitAndLose = positionVO.getAllProfitAndLose();
        userInfoVO.setAllProfitAndLose(allProfitAndLose);

        BigDecimal userAllAmt = user.getUserAmt();
//        userAllAmt = userAllAmt.add(allProfitAndLose);
        userInfoVO.setAllPositionMoney(positionVO.getAllPositionMoney());

        BigDecimal allPositionMoney=positionVO.getAllPositionMoney();
        if(ObjectUtils.isEmpty(allPositionMoney)){
            allPositionMoney=BigDecimal.ZERO;
        }
        //?????????=????????????+????????????
        userAllAmt=allPositionMoney.add(user.getEnableAmt());
        userInfoVO.setUserAmt(userAllAmt);
        userInfoVO.setEnableIndexAmt(user.getEnableIndexAmt());


//        IndexPositionVO indexPositionVO = this.iUserIndexPositionService.findUserIndexPositionAllProfitAndLose(user.getId());
//        BigDecimal allIndexProfitAndLose = indexPositionVO.getAllIndexProfitAndLose();
//        userInfoVO.setAllIndexProfitAndLose(allIndexProfitAndLose);
//        userInfoVO.setAllIndexFreezAmt(indexPositionVO.getAllIndexFreezAmt());

//        BigDecimal userAllIndexAmt = user.getUserIndexAmt();
//        userAllIndexAmt = userAllIndexAmt.add(allIndexProfitAndLose);
//        userInfoVO.setUserIndexAmt(userAllIndexAmt);
//
//        userInfoVO.setEnableFuturesAmt(user.getEnableFutAmt());

//
//        FuturesPositionVO futuresPositionVO = this.iUserFuturesPositionService.findUserFuturesPositionAllProfitAndLose(user.getId());
//
//        userInfoVO.setAllFuturesFreezAmt(futuresPositionVO.getAllFuturesDepositAmt());
//
//
//        BigDecimal allFuturesProfitAndLose = futuresPositionVO.getAllFuturesProfitAndLose();
//        userInfoVO.setAllFuturesProfitAndLose(allFuturesProfitAndLose);


//        BigDecimal userAllFuturesAmt = user.getUserFutAmt();
//        userAllFuturesAmt = userAllFuturesAmt.add(allFuturesProfitAndLose);
//        userInfoVO.setUserFuturesAmt(userAllFuturesAmt);

        return userInfoVO;
    }


    public static void main(String[] args) {
        int a = 3;

        System.out.println((a != 0));
        System.out.println((a != 3));

        System.out.println(((a != 0) ? 1 : 0) & ((a != 3) ? 1 : 0));
        System.out.println((a != 0 && a != 3));


        if (a != 0 && a != 3) {
            System.out.println("????????????");
        } else {
            System.out.println("????????????");
        }
    }


    @Override
    public void updateUserAmt(Double amt, Integer user_id) {
        userMapper.updateUserAmt(amt, user_id);
    }

}

