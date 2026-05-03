package com.stilov.springboot_practice_2503.users;

import com.stilov.springboot_practice_2503.entities.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper mapper;

    private final UserRepository userRepository;

    public UserService(UserMapper mapper, UserRepository userRepository) {
        this.mapper = mapper;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers(){
        List<UserEntity> allUserEntities = userRepository.findAll();
        return allUserEntities.stream()
                .map(mapper::toUserDTO).toList();
    }


}
