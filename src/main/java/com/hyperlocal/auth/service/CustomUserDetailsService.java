package com.hyperlocal.auth.service;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.UserStatus;
import com.hyperlocal.auth.repository.UserRepository;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService  implements UserDetailsService {

    private  final UserRepository userRepository;

    public  CustomUserDetailsService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException{
        User user =   userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email : "+ username));

        if(user.getStatus() == UserStatus.SUSPENDED){
            throw new DisabledException("Account has been suspended by the administrator");
        }

        return  user;
    }
}
