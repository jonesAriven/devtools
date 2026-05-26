package com.frp.manager.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.frp.manager.entity.FrpClient;
import com.frp.manager.mapper.FrpClientMapper;
import com.frp.manager.service.FrpClientService;
import org.springframework.stereotype.Service;

@Service
public class FrpClientServiceImpl extends ServiceImpl<FrpClientMapper, FrpClient> implements FrpClientService {
}
