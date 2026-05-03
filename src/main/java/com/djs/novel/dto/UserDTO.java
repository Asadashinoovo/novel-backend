package com.djs.novel.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    Long id;
    private String username;
    private String password;
    private String imgUrl;

}