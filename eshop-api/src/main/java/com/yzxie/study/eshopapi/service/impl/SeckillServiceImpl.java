package com.yzxie.study.eshopapi.service.impl;

import com.yzxie.study.eshopapi.service.SeckillService;
import com.yzxie.study.eshopcommon.dto.OrderResult;
import com.yzxie.study.eshopcommon.exception.ApiException;
import com.yzxie.study.eshopcommon.rpc.SeckillRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Author: xieyizun
 * Version: 1.0
 * Date: 2019-08-25
 * Description:
 **/
@Service
public class SeckillServiceImpl implements SeckillService {
    private static final Logger logger = LoggerFactory.getLogger(SeckillService.class);

    @Autowired
    private SeckillRpcService seckillRpcService;

    @Override
    public OrderResult createOrder(long productId, int num, double price, String uuid) {
        try {
            // RPC调用发送到队列
            OrderResult orderResult = seckillRpcService.sendOrderToMq(productId, num, price, uuid);
            return orderResult;
        } catch (Exception e) {
            logger.error("处理订单错误", e);
            throw new ApiException("服务异常，请稍后再试");
        }
    }

    @Override
    public OrderResult checkOrderStatus(String userId, String orderUuId) {
        try {
            // RPC调用发送到队列
            int orderStatus = seckillRpcService.getOrderStatus(userId, orderUuId);
            OrderResult result = new OrderResult(orderUuId, orderStatus);
            return result;
        } catch (Exception e) {
            logger.error("checkOrderStatus {} {}", userId, orderUuId, e);
            throw new ApiException("服务异常，请稍后再试");
        }
    }
}
