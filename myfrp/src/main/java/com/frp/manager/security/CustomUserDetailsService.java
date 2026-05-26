package com.frp.manager.security;

import com.frp.manager.entity.SysUser;
import com.frp.manager.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserService sysUserService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser sysUser = sysUserService.findByUsername(username);
        if (sysUser == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        if (sysUser.getStatus() == 0) {
            throw new UsernameNotFoundException("User disabled: " + username);
        }
        return new User(
                sysUser.getUsername(),
                sysUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + sysUser.getRole()))
        );
    }
}
