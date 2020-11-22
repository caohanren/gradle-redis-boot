package com.renren.study.controller;

import com.renren.study.entity.RedPacketInfoEntity;
import com.renren.study.mapper.RedPacketInfoMapper;
import com.renren.study.redis.RedisService;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.transport.BindTransportException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * 模拟抢红包
 *
 */
@RequestMapping("/redPacket")
@RestController
public class RedPacketController {

    @Resource
    RedPacketInfoMapper redPacketInfoMapper;

    @Resource
    RedisService redisService;

    @Resource
    RedisTemplate redisTemplate;

    private static final String NUM = ":NUM";
    private static final String AMOUNT = ":AMOUNT";

    @RequestMapping("/inserRedPacket")
    public String inserRedPacket(){
        RedPacketInfoEntity redPacketInfo = new RedPacketInfoEntity();

        //雪花算法
        redPacketInfo.setUid(100);
        Long redPacketId = System.currentTimeMillis();
        redPacketInfo.setRedPacketId(redPacketId);
        //总金额度
        redPacketInfo.setTotalAmount(2000);
        //总数量
        redPacketInfo.setTotalPacket(20);
        redPacketInfo.setCreateTime(new Date());
        redPacketInfo.setUpdateTime(new Date());
        redPacketInfoMapper.insert(redPacketInfo);

        //存储金额到redis
        redisService.set(redPacketId+AMOUNT,redPacketInfo.getTotalAmount()+"");
        redisService.set(redPacketId+NUM,redPacketInfo.getTotalPacket()+"");

        return "success";
    }


    @RequestMapping("/getRedPacket")
    public String getRedPacket(@RequestParam String redPacketId){

        String num = null;
        try {
            num = (String) redisService.get(redPacketId + NUM);
        } catch (Exception e) {
            System.out.println("报错了。。。"+e.getMessage());
            e.printStackTrace();
        }
        if(StringUtils.isNoneBlank(num)){
            return num;
        }
        return "0";
    }

    @RequestMapping("/takeRedPacket")
    public String takeRedPacket(@RequestParam String redPacketId){
        //先判断红包数量
        String packetNum = redPacketId + NUM;
        String num = (String) redisService.get(packetNum);
        if (StringUtils.isBlank(num) || Integer.parseInt(num) == 0) {
            return "抱歉！红包已经抢完了";
        }

        //再判断红包金额
        String packetAmount = redPacketId + AMOUNT;
        String amount = (String)redisService.get(packetAmount);
        if(StringUtils.isBlank(amount) || Integer.parseInt(amount) == 0){
            return "抱歉！红包已经抢完了,没钱了";
        }

        Integer totalAmountInt = Integer.parseInt(amount);
        Integer totalNumInt = Integer.parseInt(num);
        Integer maxMoney = totalAmountInt / totalNumInt * 2;
        Random random = new Random();
        //获取的随机区间钱金额
        Integer randomAmount = random.nextInt(maxMoney);

        //原子操作减去数量、减去金额
        //redisService.decr(packetNum,1);
        //redisService.decr(packetAmount,randomAmount);
        System.out.println("随机金额:::"+randomAmount);
        Object remaiding = luaExpress(packetNum, packetAmount, randomAmount+"");
        System.out.println("剩余金额:::"+remaiding);

        return randomAmount+"";

    }


    /**
     * 获取lua结果
     * @param packetNum
     * @param packetAmount
     * @param randomAmount
     * @return
     */
    public Object luaExpress(String packetNum, String packetAmount,String randomAmount) {
        DefaultRedisScript<Integer> lockScript = new DefaultRedisScript<Integer>();
        lockScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("redis/decr.lua")));
        lockScript.setResultType(Integer.class);
        // 封装参数
        List<Object> keyList = new ArrayList<Object>();
        keyList.add(packetNum);
        keyList.add(packetAmount);
        keyList.add(randomAmount);
        Object execute = (Object)redisTemplate.execute(lockScript, keyList);
        return execute;
    }





}
