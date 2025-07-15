package xyz.hxwang.jointaccountmanager;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateBalanceRequest {
    private String offset;
    private String comment;
}
