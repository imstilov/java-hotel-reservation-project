package com.stilov.springboot_practice_2503.users;

import com.stilov.springboot_practice_2503.web.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hotel/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers(){
        log.info("Called getAllUsers method");

        return ResponseEntity.ok(ApiResponse.responseOk(userService.getAllUsers(), "User were found successfully."));
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@RequestBody @Valid UserCreateDTO userCreateDTO){
        log.info("Called createUser method");
        return ResponseEntity.ok(ApiResponse.responseOk(userService.createUser(userCreateDTO), "User created successfully."));
    }


}
