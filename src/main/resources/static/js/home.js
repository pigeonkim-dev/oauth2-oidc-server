// 같은 출처(8080)라 세션 쿠키가 자동으로 실림 → 서버가 '지금 로그인한 사람'을 알 수 있음
fetch('/api/me')
    .then(r => r.ok ? r.json() : Promise.reject())
    .then(me => {
        document.getElementById('status').innerHTML =
            '로그인됨 — <b>' + me.email + '</b> 님 환영합니다';
    })
    .catch(() => {
        document.getElementById('status').innerHTML =
            '로그인 안 됨 — <a href="/login">로그인하러 가기</a>';
    });
