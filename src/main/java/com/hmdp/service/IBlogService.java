package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    // 首页展示热点日志
    Result queryHotBlog(Integer current);

    // 首页点击后显示内容，根据id返回日志
    Result queryBlogById(Long id);

    // 用户点赞功能
    Result lickBlog(Long id);

    // 点赞排行
    Result queryBlogLike(Long id);

    // 保存探店笔记
    Result saveBlog(Blog blog);

    // 滚动查询收件箱中笔记
    Result queryBlogOfFollow(Long max, Integer offset);
}
