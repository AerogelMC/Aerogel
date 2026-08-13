# Aerogel Mixin 플러그인 작성 가이드

## 1. 대상 이름 찾기

Minecraft 26.2부터 배포 JAR가 비난독화되었으므로 공식 클래스·필드·메서드 이름을
그대로 사용합니다. 정확한 descriptor와 반환형을 확인하고, 오버로드가 있으면
다음처럼 descriptor까지 지정하는 편이 안전합니다.

```java
@Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
```

문자열 target을 사용하면 플러그인 자체는 Minecraft JAR 없이도 컴파일할 수 있습니다.
Minecraft 타입을 콜백 인자로 직접 사용하거나 `@Shadow`의 타입으로 사용하려면 개발
환경에서 공식 26.2 서버 클래스를 `compileOnly`로 추가해야 합니다. 공식 JAR를 플러그인
배포물 안에 포함하면 안 됩니다.

## 2. 자주 쓰는 주입 방식

### 메서드 앞/뒤에 코드 실행

```java
@Inject(method = "runServer", at = @At("HEAD"))
private void plugin$beforeRun(CallbackInfo ci) {
}

@Inject(method = "runServer", at = @At("RETURN"))
private void plugin$afterRun(CallbackInfo ci) {
}
```

### 호출 지점 앞에 주입

```java
@Inject(
    method = "someMethod",
    at = @At(
        value = "INVOKE",
        target = "Lsome/package/Target;calledMethod()V",
        shift = At.Shift.BEFORE
    )
)
private void plugin$beforeCall(CallbackInfo ci) {
}
```

### 반환값 변경

```java
@Inject(method = "someBooleanMethod", at = @At("RETURN"), cancellable = true)
private void plugin$changeResult(CallbackInfoReturnable<Boolean> cir) {
    if (cir.getReturnValue()) {
        cir.setReturnValue(false);
    }
}
```

### 대상 멤버 사용

```java
@Shadow private int tickCount;

@Shadow protected abstract void someProtectedMethod();
```

이름과 타입 또는 descriptor가 대상과 정확히 일치해야 합니다. `@Shadow`는 대상 멤버를
복사하지 않고 Mixin이 그 참조를 대상 클래스의 실제 멤버로 연결합니다.

### 접근자 인터페이스

```java
@Mixin(targets = "some.package.Target")
public interface TargetAccessor {
    @Accessor("privateField")
    int plugin$getPrivateField();

    @Invoker("privateMethod")
    void plugin$invokePrivateMethod();
}
```

적용 이후 대상 인스턴스를 이 인터페이스로 캐스팅해 접근할 수 있습니다.

## 3. 안전 규칙

- 주입 메서드 이름에는 `플러그인ID$설명` 접두사를 사용해 충돌을 줄입니다.
- `required: true`와 `defaultRequire: 1`을 유지합니다. 대상이 바뀌었는데 조용히
  넘어가는 것보다 서버 시작을 중단하는 편이 안전합니다.
- 필요하면 injector에 `require`, `expect`, `allow`를 명시합니다.
- `@Overwrite`는 다른 플러그인과 합성이 어렵습니다. 가능한 한 `@Inject`,
  `@ModifyArg`, `@ModifyVariable`, `@Redirect`처럼 작은 범위의 주입을 사용합니다.
- `@Redirect`도 동일 호출 지점을 독점하기 쉬우므로 최소한으로 사용합니다.
- 생성자는 `@At("RETURN")` 등 객체가 완전히 초기화된 지점을 우선합니다.
- 서버 스레드를 막는 파일/네트워크 작업은 주입 메서드에서 직접 하지 않습니다.
- Mixin은 보안 샌드박스가 아닙니다. 신뢰하는 플러그인만 설치하십시오.

## 4. 플러그인 의존성 계층

`aerogel.plugin.json`의 `depends`는 필수 플러그인 그래프입니다.

```json
"depends": {
  "shared_api": ">=2.1.0"
}
```

지원 범위 표현은 `*`, 정확한 버전, `=`, `>=`, `>`, `<=`, `<`입니다. Aerogel은
누락, 버전 불일치, 순환 의존성을 서버 클래스 로드 전에 거부하고 의존 플러그인을
먼저 로드합니다.

## 5. 호환성

Aerogel의 첫 지원 버전은 Minecraft 26.2 / Java 25입니다. Minecraft 업데이트에서
내부 메서드 descriptor나 제어 흐름이 바뀌면 같은 이름이 남아 있어도 injection point가
깨질 수 있습니다. 버전을 올릴 때는 `doctor`와 실제 서버 시작 테스트를 모두 수행하고,
월드 백업을 먼저 만드십시오.
