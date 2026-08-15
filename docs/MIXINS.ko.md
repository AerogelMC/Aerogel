# 믹스인

Aerogel은 일반 Sponge Mixin 클래스와 타입 안전한 Kotlin DSL을 함께 지원합니다. DSL은 플러그인 빌드 중 일반 Mixin 바이트코드로 컴파일됩니다. 서버가 스크립트를 해석하거나 별도의 변환 엔진을 사용하는 구조가 아닙니다.

생성할 Mixin마다 `.mixin.kts` 파일 하나를 만듭니다.

```kotlin
// src/main/mixins/ServerBrand.mixin.kts
import dev.aerogel.api.mixin.At
import dev.aerogel.api.mixin.mixin
import net.minecraft.server.MinecraftServer

mixin<MinecraftServer>(priority = 900) {
    inject(
        MinecraftServer::getServerModName,
        at = At.HEAD,
        cancellable = true
    ) { callback ->
        callback.returnValue = "example"
    }
}
```

파일 이름은 생성된 Mixin 이름이 됩니다. Aerogel은 Kotlin 멤버 참조에서 정확한 JVM 소유 클래스, 이름, 디스크립터를 구하고 Mixin 클래스와 `<플러그인-id>.generated.mixins.json`을 생성한 뒤 플러그인 메타데이터에 자동 등록합니다.

## DSL 지원 범위

Sponge Mixin 0.8.7의 실행형 인젝터 계열을 모두 지원합니다.

- `inject`, `injectStatic`
- `modifyArg`, `modifyArgStatic`
- `modifyArgs`, `modifyArgsStatic`
- `modifyVariable`, `modifyVariableStatic`
- `modifyConstant`, `modifyConstantStatic`
- 메서드·생성자·필드 접근을 지원하는 `redirect`, `redirectStatic`
- `overwrite`, `overwriteStatic`

표준 `@Accessor`, `@Invoker`, `@Shadow`, `@Final`, `@Mutable`, `@Unique` 멤버도 생성합니다. Minecraft의 private 필드를 일반 리플렉션으로 우회하는 방식이 아니라 Sponge Mixin이 실제 멤버를 병합합니다.

`@Pseudo`, soft `@Implements`, 사용자 정의 주입 지점, 복잡한 surrogate 묶음처럼 클래스 선언 자체가 핵심인 특수 기능에는 일반 Java Mixin을 함께 사용할 수 있습니다. Kotlin 생성 Mixin과 같은 플러그인에 공존할 수 있습니다.

## 대상 객체와 인자

인스턴스 메서드 handler의 receiver는 대상 객체입니다. 대상 메서드 인자가 먼저 오고 callback이 마지막에 옵니다.

```kotlin
mixin<MinecraftServer> {
    inject(MinecraftServer::tickServer, at = At.HEAD) { shouldKeepTicking, callback ->
        if (!shouldKeepTicking.asBoolean) callback.cancel()
    }
}
```

반환형이 void이면 `CallbackInfo`, 값을 반환하면 `CallbackInfoReturnable<R>`가 자동 추론됩니다. static 대상에는 `*Static` 함수를 사용해야 하며, 잘못 섞으면 빌드 단계에서 실패합니다.

## 주입 지점

다음 주입 지점을 타입 안전하게 사용할 수 있습니다.

- `At.HEAD`, `At.RETURN`, `At.TAIL`, `At.CTOR_HEAD`
- `At.invoke(메서드)`, `At.invokeAssign(메서드)`
- `At.invokeString(메서드, 문자열)`
- `At.field(프로퍼티)`
- `At.construct(클래스)`, `At.construct(::생성자)`
- `At.jump()`, `At.load()`, `At.store()`, `At.CONSTANT`

`configured`로 ordinal, opcode, shift, slice id, 주입 지점 인자를 지정할 수 있습니다.

```kotlin
val secondReturn = At.RETURN.configured(ordinal = 1)
val afterCall = At.invoke(Target::work).configured(shift = AtShift.AFTER)
```

여러 지점을 지정할 때는 `at(first, second)`, 검색 범위를 제한할 때는 `MixinSlice`를 사용합니다. Kotlin 참조 자체를 만들 수 없는 특수 대상에만 저수준 탈출구인 `At.explicit(...)`을 사용하세요.

## 생성자와 정적 초기화

```kotlin
mixin<Target> {
    injectConstructor(::Target, at = At.RETURN) { callback ->
        // this는 생성이 끝난 Target입니다.
    }

    classInitializer(at = At.TAIL) { callback ->
        // receiver가 없는 정적 초기화입니다.
    }
}
```

생성자 참조를 `redirect`에 전달하면 `NEW` 선택자와 생성자 redirect 디스크립터를 자동 생성합니다.

## 코드 수정과 redirect

```kotlin
mixin<Target> {
    modifyArg<Int>(
        Target::calculate,
        at = At.invoke(Helper::consume),
        index = 0
    ) { value -> value + 1 }

    modifyConstant<Int>(
        Target::limit,
        constant = ConstantSelector.value(64)
    ) { 128 }

    redirect(Target::render, Renderer::draw) { renderer, value ->
        renderer.draw(value)
    }
}
```

`ModifyArgs`는 Sponge의 `Args` 객체를 사용합니다. 필드 redirect는 참조된 필드를 조사해 GET/SET opcode를 정확히 선택합니다. 대상 메서드가 static이면 해당 `*Static` 함수를 사용합니다.

## Accessor, Invoker, Shadow, 고유 상태

```kotlin
mixin<Target> {
    val value = accessor(Target::value)
    val calculate = invoker(Target::calculate)
    val shadowed = shadow(Target::existingField)
    val counter = uniqueField<Int>()

    inject(Target::run, at = At.HEAD) { callback ->
        value[this] = calculate(this, value[this])
        counter[this] = counter[this] + 1
    }
}
```

- `accessor`와 `invoker`는 표준 Mixin 브리지를 생성합니다.
- `shadow`는 실제 shadow 멤버와 외부 Kotlin handler가 사용할 충돌 방지 브리지를 생성합니다.
- `mutableFinalShadow`는 `@Shadow @Final @Mutable`을 생성합니다. 실제 final 필드를 의도적으로 변경할 때만 사용하세요.
- `uniqueField<T>()`는 대상 인스턴스마다 실제 `@Unique` 필드를 추가합니다. 전역 맵에 상태를 숨기지 않으며 초기값은 해당 타입의 JVM 기본값입니다.

브리지 이름에는 생성된 Mixin의 고유 식별자가 들어가므로 여러 플러그인의 일반적인 accessor 이름이 충돌하지 않습니다.

## 로컬 변수 캡처

먼저 일반 `inject`에 `locals = LocalCapture.PRINT`를 지정해 사용 가능한 로컬을 확인합니다. 그다음 정확한 타입을 선언합니다.

```kotlin
injectLocals(
    Target::read,
    at = At.RETURN,
    capture = local<String>(),
    locals = LocalCapture.CAPTURE_FAILHARD
) { callback, localValue ->
    callback.returnValue = localValue
}
```

연속된 로컬 두 개는 `locals<A, B>()`로 지정합니다. 로컬 캡처는 대상 메서드의 프레임에 의존하므로 메서드 인자만 사용하는 주입보다 Minecraft 버전 변경에 민감합니다.

## 그룹, Slice, 검증 옵션

모든 인젝터에서 Sponge의 `require`, `expect`, `allow`, `order`, `remap`, `constraints`를 사용할 수 있습니다. 여러 호환 경로를 묶을 때는 그룹 이름을 반복하지 않아도 됩니다.

```kotlin
group("compatible-paths", min = 1, max = 1) {
    inject(Target::firstPath, at = At.HEAD, require = 0) { callback -> }
    inject(Target::secondPath, at = At.HEAD, require = 0) { callback -> }
}
```

Aerogel은 직접 멤버 참조, static/instance 형태, 디스크립터, 필드, overwrite 접근 제한자, 생성된 handler를 빌드 중 검증합니다. 가능한 오류는 서버 시작 시점까지 미루지 않고 해당 Mixin 빌드에서 실패시킵니다.

## 일반 Mixin 클래스

구조 자체가 Java 클래스 선언이어야 하는 기능에는 일반 Mixin을 사용합니다.

```java
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Inject(method = "runServer", at = @At("HEAD"))
    private void example$beforeRun(CallbackInfo callback) {
    }
}
```

Gradle 설정에 JSON을 등록합니다.

```kotlin
aerogel {
    plugin {
        mixin("example.mixins.json")
    }
}
```

`./gradlew build`를 실행하세요. 생성 소스, 바이트코드, 설정 파일은 빌드 결과물이므로 커밋하지 않습니다. 일반 플러그인 callback은 리로드할 수 있지만 이미 적용된 Mixin 구조가 바뀌면 서버 완전 재시작이 필요할 수 있습니다.
