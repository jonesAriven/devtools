package com.frp.manager.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.frp.manager.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    SysUser findByUsername(String username);
    void initAdminUser();
}
