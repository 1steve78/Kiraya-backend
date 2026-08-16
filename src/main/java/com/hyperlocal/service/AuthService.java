package com.hyperlocal.service;

import com.hyperlocal.dto.RegisterRequest;
import com.hyperlocal.dto.UserResponse;
import com.hyperlocal.entity.Role;
import com.hyperlocal.entity.User;
import com.hyperlocal.exception.UserAlreadyExistsException;
import com.hyperlocal.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private  final UserRepository userRepository;
    private  final PasswordEncoder passwordEncoder;

    public  AuthService(UserRepository userRepository , PasswordEncoder passwordEncoder){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request){

        if(userRepository.existsByEmail(request.getEmail())){
            throw  new UserAlreadyExistsException("Email is already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());

        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setRole(Role.CUSTOMER);

        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }
}
