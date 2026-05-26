package com.frp.manager.config;

import com.frp.manager.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitRunner implements CommandLineRunner {

    private final SysUserService sysUserService;

    @Override
    public void run(String... args) {
        sysUserService.initAdminUser();
        log.info("✅ 默认管理员账号已初始化");
    }
}
