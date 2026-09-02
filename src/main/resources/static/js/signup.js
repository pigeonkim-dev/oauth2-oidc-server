// 비밀번호 확인 일치 검사 — 클라이언트 즉시 피드백(UX).
// 서버의 @AssertTrue 가 최종 방어선이고, 이건 오타를 바로 잡아주는 보조 장치다.
const password = document.getElementById('password');
const confirmPassword = document.getElementById('confirmPassword');

function checkMatch() {
    // 값이 다르면 confirmPassword 에 '커스텀 유효성 메시지'를 걸어 브라우저가 제출을 막게 한다.
    if (confirmPassword.value && password.value !== confirmPassword.value) {
        confirmPassword.setCustomValidity('비밀번호가 일치하지 않습니다');
    } else {
        confirmPassword.setCustomValidity(''); // 일치하면 해제 → 제출 허용
    }
}

password.addEventListener('input', checkMatch);
confirmPassword.addEventListener('input', checkMatch);
