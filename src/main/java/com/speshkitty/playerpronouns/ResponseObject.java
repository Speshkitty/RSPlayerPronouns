package com.speshkitty.playerpronouns;


import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ResponseObject {
    @Getter
    @Setter
    int StatusCode;
    @Getter
    @Setter
    List<DataBean> body = new ArrayList<>();

    public class DataBean
    {
        @Getter
        @Setter
        String id;
        @Getter
        @Setter
        String pronoun;
    }

    public int findIndex(String username){
        log.debug("Looking for '" + username + "'");
        if(body == null){
            return -1;
        }
        for(int i=0;i<body.size();i++){
            //is username null?
            if(body.get(i) == null) {
                continue;
            }

            if(body.get(i).getId().equalsIgnoreCase(username)){
                return i;
            }
        }
        return -1;
    }
}
