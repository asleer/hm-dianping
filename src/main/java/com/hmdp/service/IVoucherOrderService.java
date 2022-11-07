package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    // 秒杀券抢购功能
    Result seckillVoucher(Long voucherId);

    // 使用代理使得事务生效
    void createVoucherOrder(VoucherOrder voucherOrder);
}
