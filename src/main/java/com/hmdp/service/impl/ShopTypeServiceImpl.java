package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 首页查询分类
     */
    @Override
    public Result queryList() {
        //1. 从redis中查询分类
        String typeJson = stringRedisTemplate.opsForValue().get(CACHE_TYPE_KEY);
        //2. 判断redis中是否存在分类
        if(StrUtil.isNotBlank(typeJson)){
            //3. 存在，直接返回
            List<ShopType> shopTypeList = JSONUtil.toList(typeJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        //4. 不存在，则查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //5. 不存在，则返回错误
        if(shopTypeList == null){
            return Result.fail("分类信息不存在");
        }
        //6. 存在，则写入redis
        stringRedisTemplate.opsForValue().set(CACHE_TYPE_KEY, JSONUtil.toJsonStr(shopTypeList));
        //7. 返回
        return Result.ok(shopTypeList);
    }
}
