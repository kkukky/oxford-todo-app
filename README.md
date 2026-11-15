# 옥스포드 투두 (안드로이드 앱)

옥스포드 노트패드 모양의 투두리스트를 안드로이드 앱(APK)으로 만든 프로젝트입니다.
웹 화면은 `www/index.html` 한 파일이고, Capacitor가 이를 앱으로 감쌉니다.

- 앱 이름: 옥스포드 투두 / 앱 ID: `com.donggun.oxfordtodo`
- 글꼴(손글씨, 본문)은 `www/fonts`에 들어 있어 인터넷 없이도 같은 모양으로 보입니다.
- 할 일, 로그, 보관함은 폰 안에만 저장됩니다.

> 이 프로젝트는 APK를 직접 빌드해 본 것이 아니라 설정까지 준비한 상태입니다.
> 아래 방법 A 또는 B로 처음 빌드할 때 오류가 나면 메시지를 그대로 알려 주세요.

---

## 방법 A. GitHub에서 자동으로 APK 만들기 (PC에 설치할 것 없음)

1. GitHub에서 새 저장소를 만듭니다. (비공개로 해도 됩니다)
2. 이 폴더 안에서 터미널을 열고 아래를 차례로 실행합니다.
   ```
   git init -b main
   git add .
   git commit -m "처음 올리기"
   git remote add origin https://github.com/내아이디/저장소이름.git
   git push -u origin main
   ```
   웹에서 끌어다 놓기로 올리면 파일 개수 제한(100개)에 걸려 글꼴이 빠집니다. 꼭 `git push`로 올려 주세요.
3. 저장소의 **Actions** 탭 → "APK 만들기"가 자동으로 실행됩니다. (5~10분)
4. 끝나면 실행 결과 화면 아래 **Artifacts**의 `oxford-todo-apk`를 내려받습니다. (zip 안에 `app-debug.apk`)

## 방법 B. 내 PC에서 직접 만들기 (안드로이드 스튜디오)

1. Node.js 22 이상, 안드로이드 스튜디오(JDK 21 포함)를 설치합니다.
2. 이 폴더에서 터미널을 열고:
   ```
   npm install
   npx cap sync android
   npx cap open android
   ```
3. 안드로이드 스튜디오가 열리면 처음에 Gradle 동기화가 끝날 때까지 기다린 뒤,
   메뉴 **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. 완료 알림의 **locate**를 누르면 `android/app/build/outputs/apk/debug/app-debug.apk`가 있습니다.

## 폰에 설치하기

1. `app-debug.apk`를 폰으로 옮깁니다. (카카오톡 나에게 보내기, 구글 드라이브, USB 등)
2. 폰에서 파일을 눌러 설치합니다. "출처를 알 수 없는 앱" 허용을 물으면, 그 앱(파일 앱이나 카카오톡)에 대해서만 허용합니다.
3. 설치가 끝나면 "옥스포드 투두" 아이콘이 생깁니다.
4. Play 프로텍트가 "확인되지 않은 앱"이라고 경고하면 **그래도 설치**를 고릅니다. (직접 만든 개인용 앱이라 나오는 경고입니다)

## 화면을 고쳤을 때 (업데이트)

1. `www/index.html`을 수정합니다.
2. 방법 A: 다시 `git add . && git commit -m "수정" && git push` → 새 APK가 만들어집니다.
   방법 B: `npx cap sync android` 후 다시 Build APK.
3. 새 APK를 폰에서 열어 **덮어 설치**합니다. 서명 키가 같아서 앱을 지우지 않고 업데이트되고, 저장된 할 일도 그대로 남습니다.

주의: 앱을 **삭제**하면 저장된 할 일이 모두 사라집니다. `appId`(com.donggun.oxfordtodo)도 바꾸지 마세요. 바꾸면 다른 앱으로 인식되어 기존 데이터가 따라오지 않습니다.

## AI 분석 연결하기 (선택)

클로드 화면 안에서는 AI 분석이 바로 되지만, 설치한 앱에서는 별도의 연결이 필요합니다.
`www/index.html` 맨 위쪽 스크립트의 `var AI_ENDPOINT = "";`에 내 서버 주소를 넣으면 됩니다.

- 앱이 `POST`로 `{ "prompt": "..." }`를 보냅니다.
- 서버는 Anthropic API를 호출해 `{ "text": "분석 결과" }`를 돌려줍니다.
- API 키는 앱에 넣지 말고 서버에만 두세요. (앱 안의 키는 누구나 꺼내 볼 수 있습니다)
- Cloudflare Workers처럼 작은 서버 하나로 충분하고, 서버 쪽 코드가 필요하면 만들어 드릴 수 있습니다.

`AI_ENDPOINT`가 비어 있으면 AI 분석 버튼은 "아직 연결되지 않았어요" 안내만 보여주고, 한 일 리스트와 숫자는 그대로 동작합니다.

## 구글 플레이에 올리고 싶어질 때

지금의 `app-debug.apk`는 개인 설치용입니다. 플레이 스토어에는 별도 서명 키로 만든 `.aab` 파일을 올려야 하며,
그때는 Play Console 개발자 계정(가입비 25달러)과 새 서명 키가 필요합니다.
