package com.pigeonkim.oauth2authorizationserver.web.controller;

import com.pigeonkim.oauth2authorizationserver.service.SignupService;
import com.pigeonkim.oauth2authorizationserver.web.dto.SignupForm;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 회원 가입 "화면" 컨트롤러 (Thymeleaf MVC).
 *
 * 사람이 보는 가입 화면을 담당하는 @Controller (리뷰 A1: 화면은 MVC, 클라이언트용 REST 와 분리).
 * SignupService 를 호출해 계정 생성 + 검증메일 발급을 수행한다.
 */
@Controller
@RequiredArgsConstructor
public class SignupPageController {

    private final SignupService signupService;

    /**
     * 가입 화면 렌더. 빈 폼 객체를 모델에 담아야 Thymeleaf 의 th:object/th:field 가 바인딩된다.
     * (이 자리는 화면 배선이라 채워둠.)
     */
    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("signupForm", new SignupForm());
        return "signup"; // templates/signup.html
    }

    /**
     * 가입 제출 처리.
     *
     * @Valid 가 SignupForm 의 검증 애노테이션(@Email/@Size 등)을 발동시키고, 실패 결과는
     * BindingResult 에 담긴다. BindingResult 는 반드시 @ModelAttribute 바로 다음 파라미터여야 한다.
     */
    @PostMapping("/signup")
    public String submit(@Valid @ModelAttribute("signupForm") SignupForm form,
                         BindingResult bindingResult,
                         Model model) {

        // TODO 1) 입력 검증 실패 처리:
        //    bindingResult.hasErrors() 이면 화면으로 되돌린다(return "signup").
        //    이때 폼 값과 필드 에러가 그대로 화면에 다시 뿌려진다(th:errors). 서비스 호출은 하지 않는다.

        // TODO 2) 서비스 호출(중복 체크 포함):
        //    signupService.signup(email, password, displayName) 을 호출한다.
        //    내부에서 existsByEmailAndType 로 중복이면 DuplicateCredentialException 이 던져진다.
        //    이 예외를 try/catch 로 잡아서 JSON 에러가 아니라 "화면 에러"로 바꾼다:
        //      bindingResult.reject("duplicate", "이미 가입된 이메일입니다. 로그인하세요.")  // 전역 에러
        //    그리고 return "signup" 으로 되돌린다.
        //    (왜 전역 에러? 중복은 특정 필드 형식 문제가 아니라 상태 충돌이라 폼 전체 메시지로 안내.)

        // TODO 3) 성공 시:
        //    "인증메일을 보냈습니다" 안내 화면으로 보낸다.
        //    새로고침 재제출(중복 POST)을 막으려면 redirect 를 쓴다: return "redirect:/signup/check-email";
        //    (PRG 패턴 — POST-Redirect-GET. 그 안내 화면/매핑은 다음 단계에서 만든다.)

        return "signup"; // 임시: TODO 채우면서 교체
    }
}
