package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author asleer
 * @description 工具类，功能：封装redis常用方法
 * @date 2022/10/30 21:04
 */

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 设置逻辑过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存穿透问题
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1. 从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2. 判断redis中是否存在商铺缓存
        if(StrUtil.isNotBlank(json)){
            // 存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        //3. 判断命中的是否是空字符串，如果命中""则是为了防止缓存穿透，如果是null则意味着这是第一次访问
        if(json != null){
            // 返回一个错误信息
            return null;
        }
        //4. 不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        //5. 不存在，返回错误
        if(r == null){
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6. 存在，写入redis
        this.set(key, r, time, unit);
        //7. 返回
        return r;
    }

    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 设置逻辑过期时间解决缓存击穿
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type,
            Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        //1. 从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2. 判断redis中是否存在商铺缓存
        if(StrUtil.isBlank(json)){
            // 未命中，直接返回
            return null;
        }
        //3. 命中，则把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //4. 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 未过期，直接返回店铺信息
            return r;
        }
        // 已过期，缓存重建
        //6. 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //7. 判断是否获取成功
        if(isLock){
            try {
                // 获取锁成功应该再次检测redis缓存是否存在，做DoubleCheck
                json = stringRedisTemplate.opsForValue().get(key);
                if(StrUtil.isNotBlank(json)){
                    // 存在，并且未过期，则直接返回
                    redisData = JSONUtil.toBean(json, RedisData.class);
                    r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
                    expireTime = redisData.getExpireTime();
                    if(expireTime.isAfter(LocalDateTime.now())){
                        // 未过期，直接返回店铺信息
                        return r;
                    }
                }
                // 获取成功，开启独立线程，实现缓存重建
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                        // 查询数据库，封装逻辑过期时间
                        R newR = dbFallback.apply(id);
                        // 写入redis
                        this.setWithLogicalExpire(key, newR, time, unit);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unlock(lockKey);
            }
        }
        //8. 返回过期的商铺信息
        return r;
    }

    /**
     * 获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
