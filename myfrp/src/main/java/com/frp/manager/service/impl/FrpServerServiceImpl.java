package com.frp.manager.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.frp.manager.entity.FrpServer;
import com.frp.manager.mapper.FrpServerMapper;
import com.frp.manager.service.FrpServerService;
import org.springframework.stereotype.Service;

@Service
public class FrpServerServiceImpl extends ServiceImpl<FrpServerMapper, FrpServer> implements FrpServerService {
}
