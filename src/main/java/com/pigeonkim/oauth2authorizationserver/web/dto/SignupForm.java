package com.pigeonkim.oauth2authorizationserver.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thymeleaf 가입 화면의 폼 백킹 객체(form-backing bean).
 *
 * 화면 폼 바인딩은 getter/setter 기반이라 record 가 아닌 가변 클래스로 둔다.
 * (th:field 가 setEmail/getEmail 을 부른다.)
 *
 * 여기 붙은 애노테이션이 곧 "서버측 입력 검증" 1겹이다. 컨트롤러에서 @Valid 로 발동시킨다.
 */
public class SignupForm {

    @NotBlank(message = "이메일을 입력하세요")
    @Email(message = "이메일 형식이 아닙니다")
    private String email;

    // TODO(비밀번호 정책 결정): 지금은 최소 8자만. 복잡도(대문자/숫자/특수문자)를 강제하려면
    //   @Pattern(regexp = ...) 를 여기 추가한다. '최고수준 보안' 스탠스라면 이 정책을 정해서 붙일 것.
    @NotBlank(message = "비밀번호를 입력하세요")
    @Size(min = 8, message = "비밀번호는 8자 이상")
    private String password;

    @NotBlank(message = "표시 이름을 입력하세요")
    @Size(max = 50, message = "표시 이름은 50자 이하")
    private String displayName;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
