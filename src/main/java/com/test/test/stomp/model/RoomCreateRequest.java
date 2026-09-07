package com.test.test.stomp.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomCreateRequest {

    @NotBlank(message = "채팅방 이름은 필수입니다.")
    @Size(max = 50, message = "채팅방 이름은 50자 이하여야 합니다.")
    private String name;
}
