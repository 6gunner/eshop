package com.yzxie.study.eshopbiz.queue;

import com.rabbitmq.client.AMQP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.yzxie.study.eshopcommon.constant.OrderConst.ORDER_QUEUE;
import static com.yzxie.study.eshopcommon.constant.OrderConst.ORDER_TOPIC_EXCHANGE;

/**
 * Author: xieyizun
 * Version: 1.0
 * Date: 2019-08-25
 * Description:
 **/
@Configuration
public class RabbitMqConfig {
	private static final Logger logger = LoggerFactory.getLogger(RabbitMqConfig.class);
	
	@Bean
	public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory) {
		connectionFactory.setPublisherConfirms(true);
		connectionFactory.setPublisherReturns(true);
		RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		rabbitTemplate.setMandatory(true);
		rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> logger.info("消息发送成功:correlationData({}),ack({}),cause({})", correlationData, ack, cause));
		rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> logger.info("消息丢失:exchange({}),route({}),replyCode({}),replyText({}),message:{}", exchange, routingKey, replyCode, replyText, message));
		return rabbitTemplate;
	}
	
	
	@Bean
	public Queue orderQueue() {
		return new Queue(ORDER_QUEUE, true);
	}
	
	@Bean
	public TopicExchange orderExchange() {
		return new TopicExchange(ORDER_TOPIC_EXCHANGE);
	}
}
