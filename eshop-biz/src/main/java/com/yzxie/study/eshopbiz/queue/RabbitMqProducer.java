package com.yzxie.study.eshopbiz.queue;

import com.yzxie.study.eshopcommon.dto.OrderDTO;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.yzxie.study.eshopcommon.constant.OrderConst.ORDER_QUEUE;

/**
 * Author: xieyizun
 * Version: 1.0
 * Date: 2019-08-25
 * Description:
 **/
@Component
public class RabbitMqProducer {

    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    

    /**
     * 发送订单请求到队列order-queue
     * @param orderDTO
     */
    public void send(OrderDTO orderDTO) {
//        amqpTemplate.convertAndSend(ORDER_QUEUE, orderDTO);
        rabbitTemplate.convertAndSend(ORDER_QUEUE, orderDTO);
    }
}
