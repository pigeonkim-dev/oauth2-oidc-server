package com.pigeonkim.oauth2authorizationserver.web.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SignupForm {

    @NotBlank(message = "이메일을 입력하세요")
    @Email(message = "이메일 형식이 아닙니다")
    private String email;

    @NotBlank(message = "비밀번호를 입력하세요")
    @Size(min = 12, message = "비밀번호는 12자 이상이어야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{12,20}$",
            message = "비밀번호는 대문자, 숫자, 특수문자를 각각 1개 이상 포함한 12~20자여야 합니다.")
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력하세요")
    private String confirmPassword;

    @NotBlank(message = "표시 이름을 입력하세요")
    @Size(max = 50, message = "표시 이름은 50자 이하로 설정 가능 합니다.")
    private String displayName;

    @AssertTrue(message = "비밀번호가 일치하지 않습니다")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}
