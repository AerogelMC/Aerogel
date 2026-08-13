# Aerogel

Aerogel은 Minecraft Java Edition **26.2 이상 전용 서버 플러그인 로더**입니다.
일반 플러그인 진입점과 Sponge Mixin 기반 바이트코드 주입을 함께 제공하여,
바닐라 서버 내부 동작까지 플러그인에서 확장할 수 있습니다.

## 먼저: Mixin 플러그인은 어떻게 만드는가

가장 작은 Mixin은 다음 세 부분으로 구성됩니다.

1. 대상과 주입 위치를 선언한 Java 클래스
2. 그 클래스를 나열하는 `*.mixins.json`
3. 플러그인 정보와 Mixin 설정을 연결하는 `aerogel.plugin.json`

```java
package example.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerMixin {
    @Inject(method = "runServer", at = @At("HEAD"))
    private void example$beforeRun(CallbackInfo ci) {
        System.out.println("서버 루프 시작 직전");
    }
}
```

26.2는 비난독화된 공식 이름을 제공하므로 위처럼 `net.minecraft...` 클래스와
`runServer` 메서드 이름을 직접 사용합니다. 메서드가 값을 반환하면
`CallbackInfo` 대신 `CallbackInfoReturnable<T>`를 사용합니다. 실행을 취소하려면
`@Inject(..., cancellable = true)`를 지정하고 콜백에서 `ci.cancel()` 또는
`cir.setReturnValue(...)`를 호출합니다.

`src/main/resources/example.mixins.json`:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "example.mixin",
  "compatibilityLevel": "JAVA_25",
  "mixins": ["MinecraftServerMixin"],
  "injectors": { "defaultRequire": 1 }
}
```

`src/main/resources/aerogel.plugin.json`:

```json
{
  "schemaVersion": 1,
  "id": "example_plugin",
  "version": "1.0.0",
  "name": "Example Plugin",
  "minecraft": ">=26.2",
  "entrypoints": ["example.ExamplePlugin"],
  "mixins": ["example.mixins.json"],
  "depends": {}
}
```

진입점은 선택 사항입니다. Mixin만 있는 플러그인이라면 `entrypoints`를 생략할 수
있습니다. 진입점을 쓸 때는 `AerogelPlugin`을 구현합니다.

```java
public final class ExamplePlugin implements AerogelPlugin {
    @Override
    public void onLoad(PluginContext context) {
        context.logger().info("loaded");
    }
}
```

더 자세한 주입 방식, `@Shadow`, accessor, 실패 안전장치는
[docs/MIXINS.md](docs/MIXINS.md)에 정리되어 있습니다. 완성된 예제는
[`example-plugin`](example-plugin)에 있습니다.

## 구조

```text
Aerogel launcher (Java 25)
  ├─ 공식 버전 manifest에서 server.jar 다운로드 + SHA-1 검증
  ├─ Mojang server-bundler index 추출 + 각 파일 SHA-256 검증
  ├─ plugins/*.jar 탐색
  ├─ depends 그래프 검증 및 위상 정렬
  └─ 단일 child-first 변환 ClassLoader
       ├─ Minecraft 서버와 공식 라이브러리
       ├─ 의존성 순서의 Aerogel 플러그인
       └─ Mixin transformer
```

플러그인은 의존성 순서로 로드됩니다. Minecraft, 서버 라이브러리, 플러그인은 같은
변환 계층에서 해석되므로 Mixin이 바닐라 클래스와 다른 플러그인의 클래스를 대상으로
삼을 수 있습니다. Aerogel API와 Mixin/ASM 자체는 부모 계층에 고정하여 플러그인이
로더 코어를 덮어쓰지 못하게 합니다.

## 빌드와 실행

요구 사항은 JDK 25입니다. 시스템 Gradle 설치는 필요하지 않습니다.

```powershell
.\gradlew.bat clean build
.\gradlew.bat :aerogel-loader:installDist
```

설치형 실행 스크립트는 아래에 생성됩니다.

```text
aerogel-loader/build/install/aerogel/bin/aerogel.bat
```

Minecraft 약관을 직접 읽고 동의한 경우에만 설정 명령에 명시적으로 플래그를 줍니다.

```powershell
.\aerogel-loader\build\install\aerogel\bin\aerogel.bat setup `
  --game-dir C:\minecraft\aerogel-server `
  --accept-minecraft-eula

Copy-Item .\example-plugin\build\libs\aerogel-example-plugin-0.1.0.jar `
  C:\minecraft\aerogel-server\plugins\

.\aerogel-loader\build\install\aerogel\bin\aerogel.bat doctor `
  --game-dir C:\minecraft\aerogel-server

.\aerogel-loader\build\install\aerogel\bin\aerogel.bat run `
  --game-dir C:\minecraft\aerogel-server `
  --jvm-arg=-Xms2G --jvm-arg=-Xmx4G
```

Minecraft 인자는 `--` 뒤에 둡니다. 예: `run --game-dir ... -- --port 25566`.

## 배포 및 라이선스 경계

- Aerogel 소스는 Apache-2.0입니다.
- Aerogel 배포물에는 Minecraft 클래스, 소스 또는 `server.jar`를 넣지 않습니다.
- `setup`이 Mojang 공식 manifest를 조회해 운영자 디렉터리에 공식 JAR를 내려받고
  공개된 해시를 검증합니다.
- Minecraft 사용 권리는 Aerogel 라이선스가 부여하지 않습니다. 운영자는 Minecraft
  EULA와 Usage Guidelines를 별도로 준수해야 합니다.
- 바이트코드 엔진은 MIT 라이선스 Sponge Mixin의 Java 25 대응 배포물입니다.
  **Fabric Loader는 의존하거나 포함하지 않습니다.** 전체 고지는
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)에 있습니다.

이 프로젝트는 Mojang 또는 Microsoft의 공식 제품이 아니며, 승인이나 제휴를
의미하지 않습니다.
