package bes.tcc.apiauthtest.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
@AllArgsConstructor 
public class Endpoint {

    private String url;
    private String method;
    private String operationId;
    private String summary;
    
}
