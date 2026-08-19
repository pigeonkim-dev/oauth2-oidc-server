package com.pigeonkim.oauth2authorizationserver.web.controller;

import com.pigeonkim.oauth2authorizationserver.exception.DuplicateCredentialException;
import com.pigeonkim.oauth2authorizationserver.exception.VerificationFailedException;
import com.pigeonkim.oauth2authorizationserver.service.MemberService;
import com.pigeonkim.oauth2authorizationserver.web.dto.SignupForm;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 회원 라이프사이클 "화면" 컨트롤러 (Thymeleaf MVC).
 *
 * 회원이 보는 화면을 담당한다 — 지금은 가입(/signup), 이후 P3 에서 수정(/profile)·탈퇴(/withdraw) 추가.
 * (리뷰 A1: 화면은 MVC, 클라이언트용 REST 와 분리.) 서비스를 호출해 계정 생성 + 검증메일 발급을 수행한다.
 */
@Controller
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

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
                         Model model,
                         RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "signup";
        }

        try {
            memberService.signup(form.getEmail(), form.getPassword(), form.getDisplayName());
        }catch (DuplicateCredentialException e){
            bindingResult.reject("duplicate", "이미 가입된 이메일입니다. 로그인하세요.");

            return "signup";
        }

        redirectAttributes.addFlashAttribute("email", form.getEmail());

        return "redirect:/signup/check-email";
    }

    /**
     * 가입 성공 후 "인증메일을 보냈습니다" 안내 화면 (PRG 패턴에서 redirect 목적지).
     * /signup/** 는 이미 permitAll 이라 비로그인 접근 가능. (화면 렌더만, 로직 없음)
     */
    @GetMapping("/signup/check-email")
    public String checkEmail() {
        return "check-email";
    }

    /**
     * 이메일 인증 코드 제출 처리. check-email 화면 폼이 email+code 를 POST 한다.
     * (검증 성공 시 credential.email_verified 는 VerificationService 가, account.status 는 MemberService 가 바꾼다.)
     */
    @PostMapping("/signup/verify")
    public String verify(@RequestParam String email,
                         @RequestParam String code,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try{
            memberService.verifyEmail(email, code);
        } catch (VerificationFailedException e){
            redirectAttributes.addFlashAttribute("verifyError", "인증에 실패 했습니다. 코드를 다시 확인 하세요.");
            redirectAttributes.addFlashAttribute("email", email);

            return "redirect:/signup/check-email";
        }

        return "redirect:/login?verified";
    }
}
