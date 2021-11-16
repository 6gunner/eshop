package com.yzxie.study.eshopbiz.cache;

import com.yzxie.study.eshopbiz.repository.ProductQuantityDAO;
import com.yzxie.study.eshopcommon.dto.OrderStatus;
import com.yzxie.study.eshopcommon.constant.RedisConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;

import static com.yzxie.study.eshopcommon.constant.RedisConst.SECKILL_NUMBER_KEY_PREFIX;

/**
 * Author: xieyizun
 * Version: 1.0
 * Date: 2019-08-27
 * Description:
 **/
@Component
public class RedisCache {
	private static final Logger logger = LoggerFactory.getLogger(RedisCache.class);
	
	/**
	 * 锁对象
	 */
	private static final Object LOCK = new Object();
	
	@Autowired
	private RedisTemplate<String, Object> redisTemplate;
	
	@Autowired
	private ProductQuantityDAO productQuantityDAO;
	
	@Autowired
	private RedisLock redisLock;
	
	/**
	 * lua脚本，先获取指定产品的秒杀数量，再递减
	 */
	private static final String DESC_LUA_SCRIPT = " local remain_num = redis.call('get', KEYS[1]); "
		+ " if remain_num then "
		+ "     if remain_num - ARGV[1] >= 0 then return redis.call('decrby', KEYS[1], ARGV[1]); "
		+ "     else return -1; end; "
		+ " else return nil; end; ";
	
	/**
	 * 使用Lua脚本来实现原子递减
	 *
	 * @param key       redis的key
	 * @param value     需要递减的值
	 * @param productId
	 * @return 大于0，则说明还存在库存
	 */
	public long descValueWithLua(String key, long value, long productId) {
		if (value <= 0){
			return -1;
		}
		// lua脚本原子更新库存数量
		DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
		redisScript.setScriptText(DESC_LUA_SCRIPT);
		redisScript.setResultType(Long.class);
		Long remainNum = redisTemplate.execute(redisScript, Collections.singletonList(key), value);
		
		// 缓存不存在值，从数据库加载
		if (remainNum == null) {
			// 加锁，避免缓存没有秒杀数量时，大量访问数据库
			synchronized (LOCK) {
				// double check，实现同一个部署实例只有一个线程从数据库加载一次即可
				remainNum = getSecKillNum(productId);
				if (remainNum == null) {
					// 从数据库加载，如果数据库不存在，则返回-1
					remainNum = productQuantityDAO.getProductQuantity(productId);
					if (remainNum == null) {
						return -1;
					}
					// 分布式锁，避免不同机器实例的并发对Redis进行设值
					final String lockKey = RedisLock.SECKILL_LOCK_PREFIX + productId;
					// 值value使用UUID生成随机值
					final String lockValue = UUID.randomUUID().toString().replace("-", "");
					try {
						// 分布式加锁，用来设置库存数
						boolean lock = redisLock.tryLock(lockKey, lockValue, 10);
						if (lock) {
							// double check检查，保证不会又其他线程进来
							if (getSecKillNum(productId) == null) {
								// 初始化商品库存数量到Redis缓存
								setSecKillNum(productId, remainNum);
							}
						}
					} catch (Exception e) {
						logger.error("redis try lock error {}", productId, e);
					} finally {
						// 解锁
						redisLock.release(lockKey, lockValue);
					}
				}
				// 递减
				remainNum = redisTemplate.execute(redisScript, Collections.singletonList(key), value);
			}
			
		}
		return remainNum;
	}
	
	/**
	 * 设置指定产品的秒杀数量
	 *
	 * @param productId
	 * @param num
	 */
	public void setSecKillNum(long productId, long num) {
		try {
			BoundValueOperations<String, Object> valueOperations = redisTemplate.boundValueOps(SECKILL_NUMBER_KEY_PREFIX + productId);
			valueOperations.set(num);
		} catch (Exception e) {
			logger.error("setSecKillNum {} {}", productId, num, e);
		}
	}
	
	/**
	 * 获取指定产品的秒杀数量
	 *
	 * @param productId
	 * @return
	 */
	public Long getSecKillNum(long productId) {
		try {
			BoundValueOperations<String, Object> valueOperations = redisTemplate.boundValueOps(SECKILL_NUMBER_KEY_PREFIX + productId);
			Object value = valueOperations.get();
			return value == null ? null : Long.valueOf(value.toString());
		} catch (Exception e) {
			logger.error("getSecKillNum {}", productId, e);
		}
		return null;
	}
	
	/**
	 * 设置抢购结果
	 *
	 * @param productId
	 * @param orderUuid
	 * @param orderStatus
	 */
	public void setSeckillResult(long productId, String orderUuid, OrderStatus orderStatus) {
		try {
			BoundHashOperations<String, String, Object> hashOperations =
				redisTemplate.boundHashOps(RedisConst.SECKILL_RESULT_KEY_PREFIX + productId);
			hashOperations.put(orderUuid, orderStatus.getStatus());
		} catch (Exception e) {
			logger.error("setSeckillResult {} {} {}", productId, orderUuid, orderStatus.getStatus(), e);
		}
	}
	
	/**
	 * 获取用户的抢购结果
	 *
	 * @param userId
	 * @param orderUuid
	 * @return
	 */
	public Integer getSeckillResult(String userId, String orderUuid) {
		try {
			BoundHashOperations<String, String, Object> hashOperations =
				redisTemplate.boundHashOps(RedisConst.SECKILL_RESULT_KEY_PREFIX + userId);
			Object status = hashOperations.get(orderUuid);
			return (Integer) status;
		} catch (Exception e) {
			logger.error("getSeckillResult {} {}", userId, orderUuid, e);
		}
		return null;
	}
}
