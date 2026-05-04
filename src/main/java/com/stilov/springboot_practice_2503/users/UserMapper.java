package com.stilov.springboot_practice_2503.users;

import com.stilov.springboot_practice_2503.entities.UserEntity;
import com.stilov.springboot_practice_2503.reservations.ReservationDTO;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserDTO toUserDTO(UserEntity user){
        return new UserDTO(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreatedAt()
        );
    }

    public UserEntity toUserEntity(UserDTO userDTO){
        return new UserEntity(
                userDTO.id(),
                userDTO.email(),
                userDTO.firstName(),
                userDTO.lastName(),
                userDTO.createdAt()
        );
    }

    public UserEntity сreateDTOToEntity(UserCreateDTO userCreateDTO){
        UserEntity entity = new UserEntity();
        entity.setEmail(userCreateDTO.email());
        entity.setFirstName(userCreateDTO.firstName());
        entity.setLastName(userCreateDTO.lastName());
        return entity;
    }
}
