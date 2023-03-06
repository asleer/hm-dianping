package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;
    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        // Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 互斥锁
        // Shop shop = queryWithMutex(id);
        // 逻辑过期
        // Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 10L, TimeUnit.SECONDS);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        //7. 返回
        return Result.ok(shop);
    }

/*    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    *//**
     * 设置逻辑过期时间解决缓存击穿
     * @param id
     * @return
     *//*
    public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1. 从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2. 判断redis中是否存在商铺缓存
        if(StrUtil.isBlank(shopJson)){
            // 未命中，直接返回
            return null;
        }
        //3. 命中，则把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //4. 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 未过期，直接返回店铺信息
            return shop;
        }
        // 已过期，缓存重建
        //6. 获取互斥锁
        boolean isLock = tryLock(LOCK_SHOP_KEY + id);
        //7. 判断是否获取成功
        if(isLock){
            try {
                // 获取锁成功应该再次检测redis缓存是否存在，做DoubleCheck
                json = stringRedisTemplate.opsForValue().get(key);
                if(StrUtil.isNotBlank(json)){
                    // 存在，并且未过期，则直接返回
                    redisData = JSONUtil.toBean(json, RedisData.class);
                    shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
                    expireTime = redisData.getExpireTime();
                    if(expireTime.isAfter(LocalDateTime.now())){
                        // 未过期，直接返回店铺信息
                        return shop;
                    }
                }
                // 获取成功，开启独立线程，实现缓存重建
                this.saveShop2Redis(id, 20L);
            });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unlock(LOCK_SHOP_KEY + id);
            }
        }
        //8. 返回过期的商铺信息
        return shop;
    }

    *//**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     *//*
    public Shop queryWithMutex(Long id){
        //1. 从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2. 判断redis中是否存在商铺缓存
        if(StrUtil.isNotBlank(shopJson)){
            // 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //3. 判断命中的是否是空字符串，如果命中""则是为了防止缓存穿透，如果是null则意味着这是第一次访问
        if(shopJson != null){
            // 返回一个错误信息
            return null;
        }
        Shop shop = null;
        try {
            //4. 未命中，实现缓存重建
            //4.1 尝试获取互斥锁
            boolean isLock = tryLock(LOCK_SHOP_KEY + id);
            //4.2 判断是否获取成功
            if(!isLock){
                //4.3 失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //5 获取锁成功应该再次检测redis缓存是否存在，做DoubleCheck
            shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if(StrUtil.isNotBlank(shopJson)){
                // 存在，直接返回
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            // 不存在则根据id查询数据库
            shop = getById(id);
            // 模拟重建的延时
            //Thread.sleep(200);
            //6. 不存在，返回错误
            if(shop == null){
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //7. 存在，写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //8. 释放互斥锁
            unlock(LOCK_SHOP_KEY + id);
        }
        //9. 返回
        return shop;
    }

    *//**
     * 原代码存在缓存击穿问题
     * @param id
     * @return
     *//*
    private Shop queryWithPassThrough(Long id){
        //1. 从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2. 判断redis中是否存在商铺缓存
        if(StrUtil.isNotBlank(shopJson)){
            // 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //3. 判断命中的是否是空字符串，如果命中""则是为了防止缓存穿透，如果是null则意味着这是第一次访问
        if(shopJson != null){
            // 返回一个错误信息
            return null;
        }
        //4. 不存在，根据id查询数据库
        Shop shop = getById(id);
        //5. 不存在，返回错误
        if(shop == null){
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6. 存在，写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7. 返回
        return shop;
    }

    *//**
     * 获取锁
     * @param key
     * @return
     *//*
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    *//**
     * 释放锁
     * @param key
     *//*
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    *//**
     * 给热点数据写入过期时间（预热）
     * @param id
     * @param expireSeconds
     *//*
    public void saveShop2Redis(Long id, Long expireSeconds){
        // 查询店铺信息
        Shop shop = getById(id);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }*/

    /**
     * 更新商铺信息，并添加事务管理
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("商铺id不能为空");
        }
        //1. 更新数据库
        updateById(shop);
        //2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 判断是否需要根据坐标查询
        if(x == null || y == null) {
            // 不需要坐标，按数据库查询
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        // 查询redis，按照距离排序后分页，结果返回shopId和distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 解析出id
        if(results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了
            return Result.ok(Collections.emptyList());
        }
        // 截取from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 根据id查询店铺
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
