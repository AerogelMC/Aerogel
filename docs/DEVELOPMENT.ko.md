# Aerogel 플러그인 개발 가이드

> **대상:** Aerogel `26.2-2`, Minecraft Java Edition `26.2+`, Java `25`  
> **언어:** [English](DEVELOPMENT.md) · 한국어

이 문서는 프로젝트 생성부터 메타데이터, 생명주기, 바닐라 접근, 이벤트, 명령어, GUI, 번역, 리로드, Mixin, 패키징과 문제 해결까지 Aerogel 플러그인 개발의 전체 흐름을 설명합니다.

Aerogel은 의도적으로 두 계층을 함께 사용합니다.

- **Aerogel API**는 반복 작업과 플러그인 소유 자원의 생명주기를 담당합니다. 이벤트, 명령어, 작업, 인벤토리, 스코어보드, 보스바, 다이얼로그, 번역, 관리형 파일이 여기에 해당합니다.
- **Minecraft 서버 클래스**는 그 외 작업을 위해 그대로 열려 있습니다. 이벤트는 복사본이나 범용 래퍼가 아니라 실제 `ServerPlayer`, `ServerLevel`, `Entity`, `ItemStack`, 패킷, 컴포넌트 객체를 제공합니다.

판단 기준은 간단합니다. 원하는 기능을 표현하는 Aerogel 고수준 API가 있다면 먼저 사용하고, 더 세밀한 제어가 필요하면 바닐라 API를 직접 사용하며, 둘 다 적절한 지점을 제공하지 않을 때만 Mixin을 사용합니다.

---

## 목차

- [적절한 계층 선택하기](#적절한-계층-선택하기)
- [요구 사항](#요구-사항)
- [프로젝트 만들기](#프로젝트-만들기)
- [플러그인 메타데이터](#플러그인-메타데이터)
- [진입점과 생명주기](#진입점과-생명주기)
- [PluginContext와 자원 소유권](#plugincontext와-자원-소유권)
- [이벤트](#이벤트)
- [명령어와 자동완성](#명령어와-자동완성)
- [스케줄링과 스레드](#스케줄링과-스레드)
- [플레이어, 월드, 엔티티, 패킷](#플레이어-월드-엔티티-패킷)
- [인벤토리와 GUI](#인벤토리와-gui)
- [스코어보드, 보스바, 다이얼로그](#스코어보드-보스바-다이얼로그)
- [컴포넌트, 채팅, 번역](#컴포넌트-채팅-번역)
- [플러그인 데이터와 의존성](#플러그인-데이터와-의존성)
- [Mixin](#mixin)
- [빌드, 설치, 리로드](#빌드-설치-리로드)
- [오류 격리](#오류-격리)
- [문제 해결](#문제-해결)
- [배포 전 확인 사항](#배포-전-확인-사항)

---

## 적절한 계층 선택하기

| 필요한 작업 | 우선 사용할 도구 | 이유 |
|---|---|---|
| 지원되는 동작 관찰 또는 취소 | Aerogel 이벤트 | 의미와 안전한 취소 시점이 정의되어 있음 |
| 명령어 등록 | `context.commands()`를 통한 바닐라 Brigadier | 명령어 트리, 인자, 권한 조건, 리다이렉트, 자동완성을 그대로 사용 가능 |
| 메시지 전송 또는 플레이어 조작 | `ServerPlayer` | 별도의 플레이어 추상화가 없음 |
| 로드된 월드 읽기 또는 수정 | `ServerLevel`과 바닐라 API | Minecraft 상태에 완전하게 접근 가능 |
| 상자 GUI, 보스바, 다이얼로그, 스코어보드 생성 | Aerogel 서비스 | 소유권과 리로드 정리를 자동 처리 |
| 나중에 코드 실행 | Aerogel 스케줄러 | 작업 생명주기가 플러그인에 귀속됨 |
| 플러그인 상태 저장 | `context.storage()` | 변경을 합치는 비동기 I/O와 원자적 파일 교체 |
| 적절한 API나 이벤트가 없는 내부 동작 가로채기 | Mixin | 자유도는 가장 높지만 호환성 비용도 큼 |

이미 같은 동작을 나타내는 이벤트가 있다면 Mixin을 사용하지 않는 편이 좋습니다. 이벤트는 취소가 안전한 시점을 보장하지만, Mixin 주입점은 특정 Minecraft 구현 세부 사항에 결합됩니다.

## 요구 사항

- JDK 25
- Gradle 8 호환 프로젝트
- Aerogel Gradle 플러그인 `26.2-2`
- Minecraft Java Edition 서버 `26.2` 또는 설치한 Aerogel 빌드가 지원하는 이후 버전
- IntelliJ IDEA 등 Gradle을 지원하는 Java IDE

Aerogel Gradle 플러그인은 Mojang 공식 서버 파일을 다운로드하고, 공개된 해시를 검증한 뒤, 추출한 서버 클래스 경로를 `compileOnly`로 추가합니다. Minecraft 코드는 플러그인 JAR에 복사되지 않습니다.

## 프로젝트 만들기

### 1. 프로젝트 구조 만들기

```text
my-plugin/
├─ settings.gradle.kts
├─ build.gradle.kts
└─ src/
   └─ main/
      ├─ java/
      │  └─ com/example/myplugin/MyPlugin.java
      └─ resources/
         └─ assets/my_plugin/lang/
            ├─ en_us.json
            └─ ko_kr.json
```

Gradle 플러그인을 사용한다면 `aerogel.plugin.json`을 직접 만들지 마세요. `aerogel` 블록을 기준으로 자동 생성됩니다.

### 2. Aerogel 플러그인 저장소 연결하기

Gradle 플러그인이 공개 저장소에 배포되기 전까지 `aerogel-gradle-plugin-26.2-2.zip`을 압축 해제하고, 그 안의 Maven 저장소를 `pluginManagement`에 연결합니다.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven { url = uri("path/to/extracted/aerogel-gradle-plugin-26.2-2") }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "my-plugin"
```

Windows 경로에는 슬래시(`/`)를 사용하거나 역슬래시를 올바르게 이스케이프하세요.

### 3. 플러그인 설정하기

```kotlin
// build.gradle.kts
plugins {
    id("dev.aerogel.plugin") version "26.2-2"
}

group = "com.example"
version = "1.0.0"

aerogel {
    minecraft.set("26.2")

    plugin {
        id.set("my_plugin")
        name.set("My Plugin")
        entrypoint("com.example.myplugin.MyPlugin")
    }
}
```

이 플러그인은 Java 툴체인, `--release 25`, UTF-8 컴파일, Aerogel API, 공식 Minecraft 클래스 경로, Mixin, JetBrains 애노테이션을 설정합니다.

### 4. IDE 자동완성 준비하기

```powershell
.\gradlew.bat setupAerogelDevelopment
```

실행이 끝나면 IDE에서 Gradle 프로젝트를 새로고침합니다. `ServerPlayer`, `Component`, `Commands`, `Blocks` 등의 import와 자동완성이 정상적으로 표시되어야 합니다.

이 작업은 Java 컴파일 전에 자동 실행되기도 하지만, 처음에는 직접 실행해야 다운로드나 해시 검증 오류를 찾기 쉽습니다.

IntelliJ IDEA에서는 이 작업이 `@EventHandler` 메서드와 Aerogel Gradle DSL에
선언한 진입점 클래스도 리플렉션 진입점으로 등록합니다. 따라서 정상적인
플러그인 콜백과 플러그인 클래스가 사용되지 않는 선언으로 표시되지 않습니다.
IDE 메타데이터만 다시 반영하려면 `./gradlew configureAerogelIdea`를 실행합니다.

### 5. 진입점 만들기

```java
package com.example.myplugin;

import dev.aerogel.api.AerogelPlugin;
import dev.aerogel.api.PluginContext;
import dev.aerogel.api.event.server.ServerStartedEvent;

public final class MyPlugin implements AerogelPlugin {
    @Override
    public void onLoad(PluginContext context) {
        context.logger().info("Loading " + context.pluginId());

        context.events().listen(ServerStartedEvent.class, event ->
            context.logger().info("The Minecraft server is ready."));
    }

    @Override
    public void onUnload(PluginContext context) {
        context.logger().info("Unloading " + context.pluginId());
    }
}
```

## 플러그인 메타데이터

자동 생성되는 `aerogel.plugin.json`은 스키마 버전 `1`을 사용합니다.

| 필드 | 필수 | 의미 |
|---|---:|---|
| `schemaVersion` | 예 | 메타데이터 형식. 현재 값은 `1` |
| `id` | 예 | 변하지 않는 소문자 식별자. `[a-z][a-z0-9_-]{1,63}`과 일치해야 함 |
| `version` | 예 | Aerogel에 표시되고 의존성 검사에 사용되는 플러그인 버전 |
| `name` | 아니요 | 사용자에게 보이는 이름. 생략하면 ID 사용 |
| `minecraft` | 아니요 | Minecraft 버전 조건. 기본값 `>=26.2` |
| `entrypoints` | 아니요 | `AerogelPlugin`을 구현하는 클래스 목록 |
| `mixins` | 아니요 | JAR 안의 Mixin 설정 리소스 목록 |
| `depends` | 아니요 | 필수 플러그인 ID와 버전 조건 |

생성 결과 예시:

```json
{
  "schemaVersion": 1,
  "id": "my_plugin",
  "version": "1.0.0",
  "name": "My Plugin",
  "minecraft": ">=26.2",
  "entrypoints": [
    "com.example.myplugin.MyPlugin"
  ],
  "mixins": [],
  "depends": {
    "shared_api": ">=2.0.0"
  }
}
```

지원하는 의존성 조건은 `*`, 정확한 버전, `=`, `>=`, `>`, `<=`, `<`입니다. 복합 범위와 캐럿(`^`) 문법은 현재 지원하지 않습니다.

의존성은 로드 순서와 클래스 로더 가시성을 결정합니다. 선택적 의존성이 아니며, 선언만으로 별도의 서비스 레지스트리가 만들어지는 것도 아닙니다.

JAR에 자동 탐색되는 `@EventHandler` 메서드만 있다면 진입점을 생략할 수 있습니다. 여러 진입점을 선언할 수도 있으며, 메타데이터 순서대로 로드되고 역순으로 언로드됩니다.

## 진입점과 생명주기

### `onLoad`

`onLoad`에서는 플러그인 소유 자원을 선언합니다.

- 명령어와 이벤트 리스너 등록
- 작업 예약
- 설정 파일 읽기
- 플러그인이 소유할 서비스 생성

명령어는 실제 서버가 준비되기 전에 등록할 수 있습니다. Aerogel이 서버 준비 시점에 명령어를 설치합니다.

초기 `onLoad`에서 `context.minecraft()`를 무조건 호출하면 안 됩니다. 아직 `MinecraftServer` 객체가 없을 수 있습니다. 실행 중인 서버가 필요한 작업은 `context.server().ready()`를 확인하거나 `ServerStartedEvent`까지 기다리세요.

```java
context.events().listen(ServerStartedEvent.class, event -> {
    var server = event.server();
    server.broadcast(Component.literal("Plugin ready."));
});
```

### `onUnload`

`onUnload`에서는 Aerogel이 소유하지 않는 상태를 정리합니다.

- 저장되지 않은 플러그인 데이터 기록
- 플러그인이 직접 만든 executor나 스레드 종료
- 파일, 소켓, 데이터베이스 풀, 파일 감시기 닫기
- `PluginContext` 밖에서 관리되는 연동 해제
- 정적 참조 제거

Aerogel은 플러그인 클래스 로더를 해제하기 전에 자신이 소유한 등록을 닫습니다. 정리 코드는 빠르고, 여러 번 실행해도 안전하며, 플러그인이 일부만 초기화된 상황도 처리할 수 있어야 합니다.

### 리로드 생명주기

리로드 시에는 새 플러그인 클래스 로더와 새 플러그인 인스턴스를 사용합니다.

```text
기존 onUnload → 기존 클래스 로더 해제 → 새로운 onLoad
```

인스턴스 필드나 정적 필드가 리로드 후에도 유지된다고 가정하지 마세요. `onReload` 메서드가 편의용으로 존재하지만, 일반적인 로더 리로드는 기존 인스턴스에 이 메서드를 호출하는 대신 새 인스턴스를 생성합니다.

## PluginContext와 자원 소유권

`PluginContext`에는 플러그인 식별 정보, 디렉터리, 로거, 이벤트와 플러그인 범위 서비스가 들어 있습니다.

| 메서드 | 용도 |
|---|---|
| `pluginId()` | 메타데이터의 고정 ID |
| `pluginVersion()` | 현재 로드된 플러그인 버전 |
| `serverDirectory()` | 전용 서버 루트 디렉터리 |
| `dataDirectory()` | 쓰기 가능한 `plugins/<id>` 디렉터리 |
| `logger()` | 플러그인 이름이 붙는 서버 로거 |
| `events()` | 타입이 지정된 동기 이벤트 버스 |
| `server()` | Aerogel 서비스와 서버 준비 상태 |
| `minecraft()` | 실제 `MinecraftServer`. 준비된 뒤에만 사용 |
| `commands()` | Brigadier 명령어 등록 |
| `scheduler()` | 동기·비동기 작업 |
| `inventories()` | 상자 인벤토리 생성·래핑 |
| `scoreboards()` | 메인 스코어보드 접근 |
| `bossBars()` | 보스바 생성 |
| `dialogs()` | 알림, 확인, 바닐라 다이얼로그 |
| `translations()` | 플러그인 언어 리소스 |
| `storage()` | 타입이 지정된 비동기 영속 데이터 |

### Aerogel이 소유하는 자원

다음 자원은 플러그인 언로드 시 Aerogel이 자동으로 해제합니다.

- 이벤트와 명령어 등록
- 예약 작업
- Aerogel 인벤토리와 열린 화면
- 플러그인 서비스로 생성한 스코어 목표와 팀
- 보스바와 시청자 목록
- 다이얼로그와 콜백
- 제한 시간이 있는 최종 flush를 포함한 관리형 데이터 파일

모든 자원은 `Registration`을 구현합니다. 언로드 전에 먼저 끝내야 할 때만 `close()`를 호출하세요. 여러 번 호출해도 안전합니다.

```java
var bar = context.bossBars().create(Component.literal("Round"));

// 플러그인 언로드보다 먼저 없애야 할 때
bar.close();
```

실제 Minecraft 객체는 플러그인 소유 자원이 아닙니다. `MinecraftServer`, `ServerLevel`, `ServerPlayer`를 닫거나 교체하려 하지 말고, 오랫동안 유효하다고 가정해 보관하지도 마세요.

## 이벤트

Aerogel은 두 가지 등록 방식을 지원하는 하나의 타입 지정 동기 이벤트 버스를 사용합니다. 두 방식의 우선순위와 취소 규칙은 같으며, 리로드 시 모두 자동 제거됩니다.

전체 이벤트 목록과 정확한 시점은 [EVENTS.md](EVENTS.md)를 참고하세요.

### 람다 리스너

작은 처리기나 진입점·서비스 인스턴스에 자연스럽게 속하는 처리기에는 람다를 사용합니다.

```java
context.events().listen(PlayerJoinEvent.class, event -> {
    ServerPlayer player = event.player();
    player.sendSystemMessage(Component.literal("Welcome!"));
});
```

우선순위와 취소된 이벤트 수신 여부도 지정할 수 있습니다.

```java
context.events().listen(
    BlockBreakEvent.class,
    EventPriority.EARLY,
    true,
    event -> {
        if (isProtected(event.position())) {
            event.cancel();
        }
    }
);
```

### 자동 탐색 애노테이션

기능별로 리스너 클래스를 나눌 때 애노테이션 방식을 사용합니다. 리스너 클래스를 직접 등록할 필요가 없습니다.

```java
public final class ProtectionListener {
    private final PluginContext context;

    public ProtectionListener(PluginContext context) {
        this.context = context;
    }

    @EventHandler(priority = EventPriority.EARLY)
    private void onBreak(BlockBreakEvent event) {
        if (isProtected(event.position())) {
            event.cancel();
            event.player().sendSystemMessage(Component.literal("Protected area."));
        }
    }
}
```

Aerogel은 모든 클래스를 초기화하지 않고 클래스 메타데이터만 먼저 검사합니다. 리스너 클래스는 `PluginContext` 생성자 또는 인자 없는 생성자를 사용할 수 있습니다. 정적 처리기는 인스턴스가 필요 없습니다. 처리기 메서드는 private이어도 되지만, 반환형이 `void`이고 정확히 하나의 `AerogelEvent` 하위 타입을 받아야 합니다.

### 우선순위와 취소

리스너는 다음 순서로 실행됩니다.

1. `EARLY`
2. `NORMAL`
3. `LATE`
4. `MONITOR`

같은 우선순위에서는 등록 순서가 유지됩니다. 취소된 이벤트는 `receiveCancelled`를 요청하지 않은 리스너를 건너뜁니다. 단, `MONITOR`는 항상 최종 상태를 관찰합니다. `MONITOR` 처리기에서 취소 상태를 변경하면 Aerogel이 기존 상태를 복원하고 오류를 기록합니다.

`CancellableEvent`를 구현하는 이벤트만 실제 동작을 막을 수 있습니다. 관찰용 이벤트는 이미 결과가 만들어진 뒤 발생하므로 안전하게 취소할 수 없습니다.

### 수정 가능한 이벤트 결과

바닐라가 아직 동작을 확정하지 않은 시점이라면 Aerogel은 의미 있는 입력값에 setter를 제공하고, 수정된 값을 실제 동작에 반영합니다. 피해량과 회복량, 효과, 장비, 텔레포트 목적지, 타깃, 드롭 아이템, 경험치, 폭발, 블록 상태 변경, 명령어 문자열 등이 이에 해당합니다. 이미 결과가 확정된 통지 이벤트는 값을 바꿔도 일관된 바닐라 결과를 만들 수 없으므로 읽기 전용으로 유지합니다.

`EntityDeathEvent`는 취소할 수 없지만 드롭 아이템과 경험치는 수정할 수 있습니다. 바닐라가 두 결과를 먼저 계산하고, Aerogel은 모든 리스너가 끝날 때까지 실제 생성을 보류합니다.

```java
@EventHandler
private void onDeath(EntityDeathEvent event) {
    event.clearDrops();
    event.addDrop(reward.copy());
    event.setDroppedExperience(25);
}
```

`drops()`는 직접 수정할 수 있는 목록입니다. 흔한 작업에는 `setDrops(...)`, `addDrop(...)`, `clearDrops()`를 사용할 수 있습니다. 최종 목록의 비어 있지 않은 스택은 각각 복사된 뒤 생성되며, 이때도 일반 엔티티 생성 이벤트 경로를 거칩니다.

### 올바른 블록 이벤트 선택하기

```text
클라이언트의 원시 요청
  └─ BlockBreakAttemptEvent
      └─ 바닐라가 채굴을 허용함
          ├─ BlockMiningStartEvent
          ├─ BlockMiningProgressEvent
          ├─ BlockMiningStopEvent / BlockMiningAbortEvent
          └─ 바닐라가 실제 파괴를 승인함
              ├─ BlockBreakEvent      (마지막 취소 가능 시점)
              └─ BlockBrokenEvent     (블록 제거 성공)
```

보호나 드롭 대체에는 `BlockBreakEvent`를 사용하세요. 원시 입력 자체가 필요한 경우에만 `BlockBreakAttemptEvent`를 사용합니다. 예를 들어 크리에이티브 플레이어가 검으로 블록을 때리면 시도 이벤트는 발생할 수 있지만, 바닐라가 파괴를 거부하므로 확정 파괴 이벤트는 발생하지 않습니다.

### 패킷 이벤트

`PlayerPacketEvent` 하위 이벤트는 바닐라가 서버바운드 패킷을 처리하기 전에 실행됩니다. 이벤트를 취소하면 해당 패킷의 바닐라 처리가 생략됩니다. 타입이 지정된 원본 패킷은 `event.packet()`으로 접근할 수 있습니다.

정규화된 고수준 이벤트로 아직 표현되지 않는 프로토콜 규칙이나 세부 정보가 필요할 때 패킷 이벤트를 사용하세요. 같은 동작의 의미 기반 이벤트가 있다면 그 이벤트를 먼저 선택합니다.

이동, 입력, 인벤토리, 명령어 제안 패킷은 매우 자주 발생할 수 있습니다. 처리기는 짧게 유지하고 블로킹 I/O를 실행하지 마세요.

### 청크 사전 로드 취소 주의점

`ChunkPreLoadEvent` 취소는 강한 로드 거부입니다. 바닐라에는 로드되지 않은 결과가 반환되고, 티켓 기반 호출자는 다시 시도할 수 있습니다. 플러그인이 요청 동작 자체도 제어하는 경우가 아니라면 스폰이나 기반 시설 청크의 로드를 막지 마세요. 청크가 반드시 필요하다고 가정하는 동기 바닐라 코드는 가짜 청크를 받는 대신 로드 실패를 표면화할 수 있습니다.

### 이벤트 콜백 오류

Aerogel 이벤트 리스너가 일반 예외를 던지면 해당 플러그인의 오류로 기록되며, 리스너와 플러그인은 계속 활성 상태를 유지합니다. 고빈도 리스너가 계속 실패하면 로그가 넘치고 틱 시간을 소모하므로 반복 오류는 빠르게 수정하세요.

## 명령어와 자동완성

Aerogel은 바닐라 Brigadier 트리를 직접 등록합니다. 별도의 명령어 모델이 없으므로 중첩 리터럴, 타입 인자, 리다이렉트, 실행 조건, 툴팁, 비동기 제안 제공자를 Minecraft 방식 그대로 사용할 수 있습니다.

### 중첩 명령어

```java
context.commands().register(
    Commands.literal("game")
        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        .then(Commands.literal("start")
            .then(Commands.literal("confirmed")
                .executes(command -> {
                    command.getSource().sendSuccess(
                        () -> Component.literal("Game started."),
                        true
                    );
                    return 1;
                })))
);
```

이 코드는 `/game start confirmed`를 만듭니다. `.then(...)`을 한 번 더 사용할 때마다 트리 단계가 하나 늘어납니다. 문법 자체가 자유 문자열인 경우가 아니라면 전체 하위 명령어를 하나의 greedy string으로 직접 파싱하지 마세요.

### 타입 인자와 자동완성

```java
context.commands().register(
    Commands.literal("game")
        .then(Commands.literal("join")
            .then(Commands.argument("arena", StringArgumentType.word())
                .suggests((command, builder) -> {
                    for (String arena : arenaNames()) {
                        builder.suggest(arena);
                    }
                    return builder.buildFuture();
                })
                .executes(command -> {
                    String arena = StringArgumentType.getString(command, "arena");
                    joinArena(command.getSource(), arena);
                    return 1;
                })))
);
```

플레이어, 엔티티, 좌표, 리소스, 차원, 아이템처럼 Minecraft 인자 타입과 제안 제공자로 표현할 수 있는 값은 해당 타입을 사용하세요. 클라이언트에서 올바른 검증과 더 풍부한 자동완성을 받을 수 있습니다.

Aerogel은 명령어 실행과 자동완성 콜백을 보호합니다. 플러그인의 일반 예외는 로그에 기록되고 실패한 명령어나 빈 제안으로 바뀌며, 서버를 종료하지 않습니다.

명령어 등록은 플러그인 소유이므로 리로드 시 제거됩니다. 언로드보다 먼저 제거해야 할 때만 반환된 등록 객체를 보관하세요.

## 스케줄링과 스레드

Minecraft 월드 상태는 대부분 단일 스레드에서 관리됩니다. 월드, 엔티티, 인벤토리, 패킷 변경은 동기 작업에서 실행하세요.

```java
context.scheduler().later(20, () -> {
    context.logger().info("20 TPS 기준 약 1초 뒤입니다.");
});

context.scheduler().repeat(0, 20, this::updateBossBar);
```

실제 Minecraft 상태를 건드리지 않는 작업만 비동기로 실행합니다.

```java
context.scheduler().async(() -> {
    Result result = loadFromDatabase();

    context.scheduler().run(() -> {
        applyResultToWorld(result);
    });
});
```

중요한 규칙:

- 서버가 밀리면 1틱이 반드시 50ms인 것은 아닙니다.
- `asyncLater`는 지연 틱을 50ms 단위의 실제 시간으로 환산하며 서버 TPS와 동기화되지 않습니다.
- 바닐라 API가 안전하다고 명시하지 않았다면 비동기 작업에서 변경 가능한 바닐라 컬렉션을 읽지 마세요.
- 서버 스레드에서 `.join()`, 데이터베이스 대기, `sleep`, 네트워크·파일 I/O를 실행하지 마세요.
- Aerogel 스케줄러 작업은 언로드 시 취소됩니다. 플러그인이 직접 만든 executor와 스레드는 직접 종료해야 합니다.

## 플레이어, 월드, 엔티티, 패킷

Aerogel의 편의 메서드는 해당 동작을 소유하는 바닐라 객체에 직접 추가되어 있습니다.

### 플레이어와 전체 메시지

```java
MinecraftServer server = context.minecraft();
ServerPlayer player = server.findPlayer("Steve").orElseThrow();

player.sendSystemMessage(Component.literal("Hello"));
player.sendOverlayMessage(Component.literal("Ready"));
player.sendTitle(
    Component.literal("Round start"),
    Component.literal("Good luck"),
    10, 60, 20
);
player.giveItem(new ItemStack(Items.DIAMOND));

server.broadcast(Component.literal("Round complete"));
```

`kick`, `clearTitle`, 조건 기반 `removeItems`, `clearInventory`, `sendPacket`, 온라인 플레이어 목록, 이름·UUID 조회도 제공됩니다. 기존 바닐라 메서드도 함께 사용할 수 있습니다.

`ServerPlayer replacement = player.respawn()`은 바닐라의 전체 리스폰 절차를 실행합니다.
호출 직후 기존 `player` 객체는 오래된 인스턴스가 되므로 반드시 반환된 객체를 사용하세요.
boolean 오버로드는 바닐라의 `keepEverything` 경로를 선택하며 서버 스레드에서 호출해야 합니다.

특정 클라이언트에게만 보이는 표현도 수신자인 `ServerPlayer`에 있습니다. 가짜 블록은
`setBlock`/`resetBlock`, 엔티티 표시 여부는 `setVisible`, 발광·투명·불·장비 오버라이드는
`setGlowing`, `setInvisible`, `setOnFire`, `setEquipment`을 사용합니다. `false`도 명시적인
강제 값이므로 실제 엔티티 상태를 다시 따르려면 각각의 `reset...` 메서드를 사용하세요.
`setGlowColorOverride`는 바닐라 `TeamColor`를 받으며 순정 클라이언트에서는 임의 RGB
외곽선을 지원하지 않습니다. `clearViewOverrides`는 Aerogel이 추적하는 해당 플레이어의
표현 오버라이드를 한 번에 실제 상태로 복원합니다. 위치·애니메이션·파티클·사운드·HUD·
날씨·월드 경계 메서드는 스냅샷 패킷이므로 이후 바닐라 동기화로 바뀔 수 있습니다.

### 월드와 엔티티

```java
ServerLevel level = context.minecraft().overworld();
Collection<ServerLevel> loaded = context.worlds().loaded();
ServerLevel arena = context.worlds().createFlat("arena");
ServerLevel seededArena = context.worlds().createFlat("practice", 12345L);
ServerLevel empty = context.worlds().createVoid("empty");
ServerLevel nether = context.worlds().createVanilla(
    "nether_arena", 12345L, VanillaDimension.NETHER
);
ServerLevel islands = context.worlds().create(
    "islands", 12345L, new IslandChunkGenerator(biomeSource)
);

level.setDayTime(6000);
level.clearWeather(20 * 60);
level.block(0, 64, 0, Blocks.STONE.defaultBlockState(), 3);

Collection<Entity> nearby = level.nearbyEntities(
    0, 64, 0, 16,
    entity -> entity instanceof LivingEntity
);

level.findEntity(uniqueId).ifPresent(Entity::discard);
level.teleport(player, 0.5, 65, 0.5);
```

일반적인 작업에는 Aerogel 편의 메서드를 사용하고, 레지스트리, 청크, 레시피, 파티클, 사운드, 데이터 컴포넌트, 엔티티 세부 API가 필요하면 바닐라 API를 직접 이어서 사용하세요.

`worlds().loaded()`는 현재 로드된 모든 월드의 불변 스냅샷을 반환합니다. 네임스페이스가 없는 월드 ID에는 플러그인 ID가 자동으로 붙으므로 `arena`는 `<plugin-id>:arena`가 됩니다. 여러 플러그인이 의도적으로 같은 월드를 공유한다면 `shared:arena`처럼 전체 ID를 사용하세요. `createVoid`는 블록이나 발판을 자동 설치하지 않는 완전한 공허 월드를 만듭니다. `FlatLevelGeneratorSettings`를 받는 `createFlat` 오버로드에서는 마인크래프트 평지 월드의 레이어, 바이옴, 구조물, 호수, 장식 규칙을 모두 지정할 수 있습니다. `createVanilla`는 올바른 차원 타입과 함께 바닐라 오버월드, 네더, 엔드 생성기를 만듭니다. `create(id, generator)`와 seed·차원 타입 오버로드에는 플러그인이 구현한 바닐라 호환 `ChunkGenerator`를 직접 전달합니다. Aerogel 전용 지형 콜백으로 기능을 줄이지 않으므로 26.2 생성 파이프라인을 그대로 제어할 수 있습니다. 반환된 `ServerLevel`은 서버 소유이므로 플러그인이 직접 닫으면 안 됩니다. 월드 생성은 서버가 연결된 뒤 서버 스레드에서 실행해야 하므로 최초 `onLoad`가 아니라 `ServerStartedEvent`를 사용합니다. 생성된 월드는 플러그인 리로드 후에도 서버 소유로 유지되고 청크도 정상 저장되지만, 서버를 완전히 다시 켤 때는 생성기를 다시 만들고 `create`를 호출해 런타임 차원 등록을 복원해야 합니다. 청크 생성은 비동기로 실행될 수 있으므로 생성기는 스레드 안전하고 결정적이어야 하며, 변하는 실시간 월드 상태를 읽어 지형을 만들면 안 됩니다.

`context.worlds().unload(id)`는 동적 월드를 저장한 뒤 남아 있는 플레이어를 기본 오버월드 스폰으로 이동시키고 안전하게 언로드합니다. `delete(id)`는 같은 안전 언로드를 수행한 다음 해당 차원의 저장 폴더를 영구 삭제합니다. 두 함수 모두 마인크래프트 기본 오버월드·네더·엔드를 거부하며 서버 스레드에서 호출해야 합니다. `delete`는 복구할 수 없으므로 저장된 월드가 다시 필요하지 않을 때만 사용하세요.

### 패킷

```java
player.sendPacket(new ClientboundClearTitlesPacket(true));
context.minecraft().broadcastPacket(packet);
```

패킷은 버전에 민감하며 잘못된 상태로 만들면 클라이언트 연결이 끊길 수 있습니다. 같은 결과를 컴포넌트, 플레이어, 인벤토리, 보스바, 다이얼로그 API로 만들 수 있다면 그 API를 먼저 사용하세요.

퇴장한 뒤나 전체 서버 재시작을 거친 뒤에도 `ServerPlayer`가 유효하다고 가정하면 안 됩니다. UUID를 저장하고 현재 접속한 실제 플레이어를 다시 조회하세요.

## 영속 데이터와 게임 객체 API

서버, 플레이어, 엔티티, 블록 엔티티, 월드, 블록, 아이템에 플러그인별 소규모 값을 보존할 때는
`context.persistentData()`를 사용하세요. 바닐라 객체가 이미 있다면 `server.data()`,
`level.data()`, `level.data(pos)`, `entity.data()`, `blockEntity.data()`, `stack.data()`에서
`.namespace(context)`로 플러그인 영역을 선택할 수 있습니다. 실제 바닐라 `ItemStack`
컴포넌트 편집에는 `new ItemStack(item).edit()` 또는 `stack.edit()`, 플러그인 소유 바닐라
레시피와 루트 테이블에는 `context.recipes()`와
`context.loot()`, 클릭 라우팅 GUI에는 `context.menus()`, 일부 클라이언트에만 존재하는
미스폰 엔티티에는 `context.virtualEntities()`, 청크 단위로 동기화하는 대량 블록 변경에는
`context.blockBatches()`를 사용합니다.

영속 데이터는 바닐라 저장 객체가 직접 소유합니다. 플레이어·엔티티·블록 엔티티 데이터는 해당
객체의 일반 NBT에, 아이템 데이터는 `CUSTOM_DATA`에, 서버·월드·좌표 데이터는
해당 월드의 `SavedData` 파일에 저장됩니다. `plugins/<id>`로 복제되는 파일은 없습니다.
서버 스레드에서 사용하세요. 플레이어와 엔티티 API가 UUID가 아니라 실제 객체를 받는
이유도 별도 저장 데이터베이스를 만들지 않고 바닐라 객체 수명을 그대로 따르기 위해서입니다.

같은 원칙으로 `player.openMenu(menu)`, `entity.virtual(context, viewers)`, `level.batch()`를
직접 사용할 수 있습니다. `RecipeHolder.register(context)`와
`LootTable.register(context, path)`도 지원합니다. 등록 작업은 리로드 시 정리할 플러그인
소유권이 필요하므로 `context`를 명시적으로 받으며, 호출 스택이나 전역 상태로 추측하지 않습니다.

각 API의 생명주기, 주의점, 예시는 [API.md](API.md)에 정리되어 있습니다.

## 인벤토리와 GUI

Aerogel은 1~6줄 상자 인벤토리를 생성하거나 호환되는 실제 바닐라 `Container`를 감쌀 수 있습니다.

```java
Inventory menu = context.inventories().create(
    3,
    Component.literal("Choose a game")
);

menu.item(13, new ItemStack(Items.DIAMOND));
InventoryView view = menu.open(player);
```

아이템을 가져갈 수 없는 표시용 GUI를 만들려면 해당 메뉴의 클릭을 취소하고 슬롯 동작을 직접 처리합니다.

```java
context.events().listen(InventoryClickEvent.class, event -> {
    if (event.player().containerMenu != view.menu()) {
        return;
    }

    event.cancel();

    if (event.slot() == 13) {
        startGame(event.player());
        view.close();
    }
});
```

다음 사항을 주의하세요.

- 모든 슬롯 번호를 검증하세요. 사용자 인벤토리 슬롯 범위는 `0`부터 `size() - 1`입니다.
- 클릭 패킷은 위쪽 컨테이너뿐 아니라 플레이어 인벤토리를 가리킬 수도 있습니다. 메뉴와 슬롯 의미를 확인한 뒤 동작하세요.
- `InventoryClickEvent`를 취소하면 바닐라 패킷 처리가 막힙니다. 서버 인벤토리 상태를 기준으로 유지하세요.
- 플러그인 언로드 시 인벤토리와 열려 있는 모든 화면이 자동으로 닫힙니다.
- 고급 동작에는 `Inventory#vanilla()`로 실제 `Container`에 접근할 수 있습니다.

## 스코어보드, 보스바, 다이얼로그

### 스코어보드

```java
Scoreboard board = context.scoreboards().main();

Objective coins = board.objective("coins", Component.literal("Coins"))
    .display(DisplaySlot.SIDEBAR)
    .score(player.getScoreboardName(), 10);

Team builders = board.team("builders")
    .prefix(Component.literal("[Build] "))
    .friendlyFire(false)
    .add(player.getScoreboardName());
```

플러그인 서비스를 통해 만든 목표와 팀은 언로드 시 제거됩니다. `findObjective` 또는 `findTeam`으로 찾은 객체는 이미 존재하는 바닐라 상태를 감쌀 뿐, 소유권을 가져오지 않습니다.

메인 스코어보드의 이름 충돌을 피하려면 플러그인 ID를 접두사로 붙인 고유 이름을 사용하세요.

### 보스바

```java
BossBar bar = context.bossBars().create(
    Component.literal("Raid"),
    BossBarColor.RED,
    BossBarOverlay.NOTCHED_10
).progress(0.5f).add(player);
```

진행률은 `0.0`부터 `1.0` 사이여야 합니다. 시청자, 표시 여부, 색상, 구분선, 음악, 안개, 화면 어둡게 표시를 제어할 수 있습니다.

### 다이얼로그

```java
Dialog dialog = context.dialogs().confirmation(
    Component.literal("Continue?"),
    List.of(Component.literal("This action changes the world.")),
    Component.literal("Yes"),
    Component.literal("No"),
    result -> confirm(result.player()),
    result -> cancel(result.player())
);

dialog.show(player);
```

일반적인 경우에는 `notice`와 `confirmation`을 사용합니다. 고수준 빌더에 아직 없는 26.2 기능이 필요하면 실제 `net.minecraft.server.dialog.Dialog`를 `nativeDialog`에 전달하세요.

다이얼로그 콜백도 다른 Aerogel 콜백처럼 오류가 격리됩니다. 입력 데이터는 선택적인 바닐라 NBT `Tag`로 제공됩니다.

## 컴포넌트, 채팅, 번역

### 바닐라 Component 사용하기

Aerogel은 모든 메시지에 `net.minecraft.network.chat.Component`를 사용합니다. 따라서 색상, 클릭 이벤트, 호버 이벤트, 번역 컴포넌트를 그대로 유지할 수 있습니다.

```java
Component message = Component.literal("Open website")
    .withStyle(style -> style
        .withColor(ChatFormatting.AQUA)
        .withUnderlined(true));

player.sendSystemMessage(message);
```

컴포넌트 색상은 지원되는 플레이어 출력과 Aerogel 콘솔 렌더링에 반영됩니다.

### 플러그인 메시지 번역하기

언어 파일은 플러그인 JAR 안의 `assets/<plugin-id>/lang/`에 둡니다.

```text
src/main/resources/
└─ assets/my_plugin/lang/
   ├─ en_us.json
   ├─ ko_kr.json
   └─ ja_jp.json
```

```json
// en_us.json
{
  "my_plugin.game.started": "The game has started.",
  "my_plugin.player.welcome": "Welcome, %s!"
}
```

```json
// ko_kr.json
{
  "my_plugin.game.started": "게임이 시작되었습니다.",
  "my_plugin.player.welcome": "%s님, 환영합니다!"
}
```

```java
Component welcome = context.translations().componentFor(
    player,
    "my_plugin.player.welcome",
    player.getDisplayName()
);

player.sendSystemMessage(welcome);
```

`componentFor`는 플레이어의 클라이언트 언어를 기준으로 fallback을 선택합니다. `component`는 `en_us`, `componentForLocale`은 지정한 언어, `text`는 로그나 컴포넌트가 아닌 출력에 사용할 일반 문자열을 반환합니다. 언어 코드는 소문자와 밑줄 형태로 정규화됩니다.

항상 `en_us`를 제공하세요. 요청 언어나 키가 없으면 `en_us`, 그마저 없으면 키 문자열 자체가 사용됩니다.

키 충돌을 막기 위해 플러그인 ID 접두사를 사용하세요. 플레이어에게 보이는 플러그인 메시지는 번역 리소스를 사용하는 편이 좋고, 운영 로그는 특별한 이유가 없다면 간결한 영어로 유지하는 편이 문제 검색에 유리합니다.

### 전체 채팅 모양 바꾸기

`PlayerChatEvent#setMessage`는 표시되는 메시지 본문을 바꿉니다. 접두사, 플레이어 이름, 괄호, 접미사까지 바꾸려면 renderer를 사용하세요.

```java
@EventHandler
private void onChat(PlayerChatEvent event) {
    event.setRenderer((player, message) -> ChatRender.builder(message)
        .prefix(
            Component.literal("[").withStyle(ChatFormatting.DARK_GRAY),
            player.getDisplayName().copy().withStyle(ChatFormatting.AQUA),
            Component.literal("] ").withStyle(ChatFormatting.GRAY)
        )
        .suffix(Component.literal(" ✓").withStyle(ChatFormatting.GREEN))
        .build());
}
```

접두사와 접미사는 독립된 컴포넌트 목록이므로 괄호 한 글자마다 다른 스타일을 지정할 수 있습니다. 전달받은 `message`를 본문으로 그대로 사용하면 표시 형식을 바꾸면서도 서명된 본문을 유지할 수 있습니다.

이 이벤트는 서명 메시지 검증 뒤, 플레이어 브로드캐스트와 콘솔 기록 직전에 실행됩니다. 서명 상태를 임의로 만들거나 다른 의미로 해석하지 마세요.

## 플러그인 데이터와 의존성

### 데이터 디렉터리

변경 가능한 플러그인 데이터는 `context.dataDirectory()` 아래에만 기록하세요.

```java
Path configFile = context.dataDirectory().resolve("config.json");
```

구조화된 상태를 저장할 때는 `Files.read*`, `Files.write*`를 직접 호출하는 대신 관리형 저장소를 우선 사용하세요.

```java
record PluginData(int round, Map<UUID, Integer> scores) {
    static PluginData empty() {
        return new PluginData(0, Map.of());
    }
}

DataFile<PluginData> data = context.storage().json(
    "state.json",
    PluginData.class,
    PluginData::empty
);

data.load().thenAccept(loaded -> context.scheduler().run(() ->
    applyLoadedState(loaded)
));
```

파일을 열면 Aerogel 공용 저장 작업자에서 비동기 로드를 시작합니다. 서버 스레드에서 `load().join()`이나 `flush().join()`으로 기다리지 마세요. `load()`에 바로 연결한 콜백도 I/O 작업자에서 실행되므로, Minecraft 상태 변경은 위 예시처럼 `scheduler().run(...)`으로 동기 스레드에 넣어야 합니다.

가능하면 불변 객체 교체 방식을 사용하세요.

```java
data.update(previous -> new PluginData(
    previous.round() + 1,
    previous.scores()
));
```

변경 가능한 컬렉션은 Aerogel이 변경을 알 수 있도록 `edit`으로 수정합니다.

```java
DataFile<Map<UUID, Integer>> coins = context.storage().json(
    Path.of("coins.json"),
    new TypeRef<Map<UUID, Integer>>() { },
    HashMap::new
);

coins.load().thenRun(() -> coins.edit(values -> values.put(playerId, 10)));
```

`set`, `update`, `edit`은 값을 dirty 상태로 표시합니다. 자동 저장은 기본적으로 250ms 기다린 뒤, 짧은 시간에 몰린 변경을 순서가 보장된 한 번의 쓰기로 합칩니다. `save()`는 `flush()`의 사용자용 별칭이며 둘 다 호출 시점까지 보이는 모든 변경을 즉시 디스크에 기록하고 `CompletableFuture`를 반환합니다. 플러그인 언로드 시에는 제한 시간 안에서 마지막 flush를 실행합니다.

저장할 때 같은 디렉터리에 임시 파일을 만들고 내용을 디스크에 강제로 반영한 뒤, 파일 시스템이 지원하면 목적 파일을 원자적으로 교체합니다. 기존 파일 형식이 잘못되었다면 기본값으로 조용히 덮어쓰지 않고 로드에 실패합니다. 최근 로드·저장 오류는 `lastFailure()`로 확인할 수 있습니다.

하위 디렉터리는 사용할 수 있지만 경로는 반드시 `context.dataDirectory()` 안에 있어야 합니다. 기본 파일 크기 제한은 64MiB입니다. `StorageOptions`로 자동 저장 지연, 언로드 대기 시간, 자동 저장 여부, 크기 제한을 바꿀 수 있습니다. `StorageOptions.manual()`은 백그라운드 자동 저장을 끄지만 언로드 시 마지막 flush는 그대로 실행됩니다.

### Minecraft 값을 JSON으로 저장하기

`ItemStack`을 일반 Gson 리플렉션 직렬화에 넣으면 안 됩니다. Aerogel의 Minecraft 인식 저장소는 실제 26.2 바닐라 `Codec`과 현재 서버의 고정된 레지스트리 접근을 사용합니다. 따라서 아이템 ID, 개수, 전체 데이터 컴포넌트 패치, 커스텀 데이터, 이름, 인챈트, 내부 컨테이너 내용, 프로필을 포함해 현재 레지스트리에 등록된 모든 컴포넌트가 함께 왕복 보존됩니다.

```java
DataFile<ItemStack> reward = context.storage().itemStack(
    "reward.json",
    () -> ItemStack.EMPTY
);

DataFile<List<ItemStack>> slots = context.storage().itemStacks(
    "slots.json",
    List::of
);
```

`itemStacks`는 각 원소에도 빈 스택을 허용하는 코덱을 사용합니다. 빈 칸까지 저장되므로 List 인덱스를 인벤토리 슬롯 번호로 안전하게 사용할 수 있습니다. `Component`, `CompoundTag`, `BlockState`, `DataComponentPatch`, `GlobalPos`, `BlockPos`, `Identifier`용 내장 메서드도 있습니다.

Minecraft 값이 플러그인 record 내부 필드라면 `minecraftJson`을 사용하세요.

```java
record Kit(String id, Component title, List<ItemStack> slots, CompoundTag metadata) { }

DataFile<List<Kit>> kits = context.storage().minecraftJson(
    "kits.json",
    new TypeRef<List<Kit>>() { },
    List::of
);
```

Minecraft 인식 Gson 계층은 지원하는 Minecraft 값만 정식 코덱으로 처리하고, 그 바깥의 record·List·Map은 일반 플러그인 데이터로 처리합니다. 그 외 바닐라 또는 플러그인이 정의한 Mojang `Codec<T>`는 범용 연결 API로 저장할 수 있습니다.

```java
DataFile<MyRule> rule = context.storage().codecJson(
    "rule.json",
    MyRule.CODEC,
    MyRule::defaults
);
```

이 파일들은 `onLoad`에서 열어도 되지만, 실제 비동기 로드는 서버 레지스트리 접근이 준비될 때까지 기다립니다. `onLoad` 중 이미 로드되었다고 가정하지 말고 항상 `load()` 이후에 사용하세요. Aerogel은 먼저 `NbtOps`로 인코딩한 뒤 태그 트리를 구조화된 JSON으로 옮깁니다. 일반 문자열·int·compound·list는 평범한 JSON 형태를 유지하고, byte·short·long·float·double·타입이 있는 배열에만 작은 `$nbt` 표식을 붙입니다. 따라서 파일 가독성을 유지하면서 JSON 텍스트 왕복으로 NBT 타입 구분이 사라지는 문제를 막습니다.

JSON은 record와 선언 타입이 명확한 POJO에 가장 적합합니다. 제네릭 Map·List에는 `TypeRef`가 필요하고, 실행 시점의 다양한 하위 타입을 보존하려면 사용자 정의 `DataCodec<T>`를 사용해야 합니다. 실제 `ServerPlayer`, `ServerLevel`, `Entity`, 메뉴, 레지스트리, 패킷 등 Minecraft 실행 객체를 저장하지 마세요. UUID와 리소스 키를 저장한 뒤 현재 서버에서 새 객체를 다시 조회해야 합니다.

`value()`는 메모리에 있는 실제 객체를 반환합니다. 이 객체를 직접 수정하면 자동 저장이 변경을 감지할 수 없으므로 항상 `set`, `update`, `edit`을 통해 수정하세요.

플러그인 JAR, 스테이징된 플러그인 캐시, Minecraft 자체 파일에는 쓰지 마세요. Minecraft 파일을 직접 다루는 연동 기능이라면 소유 범위와 실패 복구 방식을 명확히 해야 합니다.

권장 사항:

- UTF-8 사용
- 새 설정을 실제 상태에 반영하기 전에 검증
- 가능하면 임시 파일에 쓴 뒤 목적 파일을 원자적으로 교체
- 저장 데이터에 스키마 버전 포함
- `onUnload`와 서버 종료 이벤트에서 중요한 상태 저장
- 틱 도중 긴 동기 디스크 쓰기 금지

### 외부 라이브러리

Aerogel API, Minecraft, Mixin, 애노테이션은 `compileOnly`입니다. 해당 클래스들을 플러그인 JAR에 포함하면 안 됩니다. `check` 과정에서 실행되는 `validateAerogelPluginJar`가 잘못 포함된 클래스를 거부합니다.

일반 Gradle `implementation` 의존성은 평범한 JAR에 자동 복사되지 않습니다. 외부 라이브러리가 필요하면 플러그인 산출물에 shade하고, 충돌 위험이 있으면 패키지를 relocate하세요. 다음 항목은 shade하면 안 됩니다.

- `dev.aerogel.api.*`
- `net.minecraft.*`
- Minecraft가 제공하는 `com.mojang.*` 서버·Brigadier 클래스
- `org.spongepowered.asm.mixin.*`

포함한 모든 라이브러리의 라이선스를 준수하고 필요한 고지 파일을 함께 제공하세요.

### 플러그인 의존성

```kotlin
aerogel {
    plugin {
        id.set("game")
        dependsOn("shared_api", ">=2.0.0")
    }
}
```

Aerogel은 누락, 버전 불일치, ID 중복, 순환이 있는 필수 의존성을 로드 전에 거부합니다. 의존성을 선언한 플러그인은 해당 의존 플러그인의 클래스를 볼 수 있습니다.

다른 플러그인에서 받은 객체를 리로드 너머까지 보관하지 마세요. 공용 API나 구현을 바꾼 뒤에는 관련 플러그인을 모두 함께 리로드해야 소비자도 호환되는 클래스 로더 타입을 받습니다.

## Mixin

Mixin은 지원 이벤트나 API로 표현할 수 없는 동작을 위한 탈출구입니다. 일반적인 플러그인 개발에는 필요하지 않습니다.

다음 상황에서 Mixin을 고려합니다.

- 공개 hook이 없는 바닐라 내부 분기를 미리 가로채야 할 때
- accessor로 private 필드나 메서드를 노출해야 할 때
- 정확한 호출 지점의 반환값이나 인자를 바꿔야 할 때
- 현재 이벤트 목록으로 의미를 표현할 수 없는 기능을 구현할 때

단순 메시지 전송, 명령어 생성, 인벤토리 조작, 기존 Aerogel 이벤트 관찰을 위해 Mixin을 사용하지 마세요.

### 설정

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.example.myplugin.mixin",
  "compatibilityLevel": "JAVA_25",
  "mixins": [
    "MinecraftServerMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

Gradle 메타데이터에도 리소스를 선언합니다.

```kotlin
aerogel {
    plugin {
        id.set("my_plugin")
        mixin("my-plugin.mixins.json")
    }
}
```

```java
@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerMixin {
    @Inject(method = "runServer", at = @At("HEAD"))
    private void my_plugin$beforeServerLoop(CallbackInfo callbackInfo) {
        // 주입된 작업은 짧게 유지합니다.
    }
}
```

### Mixin 규칙

- 주입 메서드 이름에 플러그인 ID 접두사를 붙이세요.
- `@Overwrite`보다 범위가 좁은 `@Inject`, `@ModifyArg`, `@ModifyVariable`을 우선 사용하세요.
- 해당 hook 없이는 플러그인이 동작할 수 없다면 `required: true`와 `defaultRequire: 1`을 유지하세요. 조용히 잘못 동작하는 것보다 시작 단계에서 실패하는 편이 진단하기 쉽습니다.
- 정확한 Minecraft 버전에서 descriptor와 target을 확인하세요.
- 서버 스레드 주입 메서드에서 블로킹 작업을 하지 마세요.
- Mixin 코드는 샌드박스가 없는 신뢰 코드로 취급하세요.
- Mixin 준비·적용 오류는 일반 플러그인 오류 격리가 시작되기 전에 발생하므로 서버 시작을 막을 수 있습니다.

Mixin 리로드는 최선 시도입니다. 메서드 본문 변경은 hot swap될 수 있지만, 구조 변경, 새로운 target, 필드, 인터페이스, 상속 구조, 이미 변환된 클래스는 전체 `/restart`가 필요할 수 있습니다. hot swap 실패 시 경고가 명확히 보이고 기존 동작을 이해할 수 있도록 설계하세요.

주입 패턴과 저수준 지침은 [MIXINS.md](MIXINS.md)를 참고하세요.

## 빌드, 설치, 리로드

### 빌드

```powershell
.\gradlew.bat clean build
```

플러그인 JAR은 `build/libs`에 생성됩니다. 빌드 과정에서 서버·API 클래스가 잘못 포함되지 않았는지도 검사합니다.

### 설치

플러그인 JAR만 서버의 `plugins` 디렉터리에 복사합니다.

```text
server/
├─ Aerogel-26.2-2.jar
└─ plugins/
   └─ my-plugin-1.0.0.jar
```

변경 가능한 데이터는 컴파일된 JAR 안이나 JAR 옆의 임의 파일이 아니라, 자동 생성되는 `plugins/<plugin-id>/` 디렉터리에 저장하세요.

### 조회와 리로드

Aerogel은 다음 명령어를 제공합니다.

```text
/plugins list
/plugins reload
/plugins reload <plugin-id>
/tps
/networkstats
/networkstats reset
/networkstats mode vanilla
/networkstats mode aerogel
/restart
```

`/plugins`만 입력하면 의도적으로 불완전한 명령어로 처리됩니다. `/plugins list`에는 표시 이름과 회색 `<id>`가 함께 나오며, 초기화 실패로 비활성화된 플러그인은 비활성 상태로 표시됩니다.

`/networkstats`는 인바운드 패킷 큐 지연의 평균, p50, p95, p99, 최대값을 표시합니다. 또한 유휴 펌프에서 처리한 패킷과 일반 틱 경계에서 처리한 패킷을 구분합니다. `mode vanilla`와 `mode aerogel`은 같은 실행 중인 서버에서 두 경로를 전환하고 측정 구간을 초기화하므로 통제된 A/B 비교가 가능합니다. 통계 초기화와 모드 변경에는 게임 마스터 권한이 필요합니다.

리로드 동작:

- 교체된 JAR은 변경되지 않는 스테이징 복사본에서 로드됨
- 새로 추가한 JAR은 `/plugins reload`에서 발견됨
- 제거한 JAR은 전체 플러그인 리로드에서 언로드됨
- 명령어, 이벤트, 작업, GUI 등 소유 자원이 해제됨
- 일반 클래스 변경은 새 플러그인 클래스 로더를 사용함
- Mixin 변경은 `/restart`가 필요할 수 있음

Minecraft 버전 변경, 로더 업데이트, Mixin 구조 변경, 네이티브 라이브러리, 기존 JVM 전역 상태가 남을 가능성이 있는 경우에는 `/restart`를 사용하세요.

## 오류 격리

Aerogel은 발생 단계에 따라 플러그인 오류를 다르게 처리합니다.

| 오류 | 결과 |
|---|---|
| 메타데이터, 의존성, 탐색 오류 | 진단 메시지와 함께 로드 또는 리로드 거부 |
| 진입점 생성자 또는 `onLoad` 오류 | 해당 플러그인 비활성화, 서버 시작은 계속 |
| 로드된 필수 의존성 없음 | 의존 플러그인 비활성화 |
| 이벤트, 명령어, 자동완성, 예약 작업, 다이얼로그 콜백 오류 | 오류 기록, 플러그인은 활성 상태 유지 |
| `onUnload` 정리 오류 | 경고 기록, 나머지 정리는 계속 |
| Mixin 준비·적용 오류 | 일반 플러그인 콜백 이전이므로 서버 시작을 중단할 수 있음 |
| JVM 자체 실패를 나타내는 `VirtualMachineError` | 안전하게 계속할 수 없어 다시 던짐 |

오류 격리는 보안 샌드박스가 아닙니다. 플러그인은 서버 프로세스의 파일 시스템, 네트워크, 리플렉션, JVM 권한으로 실행됩니다. 신뢰하는 플러그인만 설치하세요.

## 문제 해결

### IDE에서 Minecraft import가 빨간색으로 표시됨

1. 프로젝트 JDK가 25인지 확인합니다.
2. `setupAerogelDevelopment`를 실행합니다.
3. Gradle 프로젝트를 새로고침하거나 다시 가져옵니다.
4. `dev.aerogel.plugin`이 적용되어 있는지 확인합니다.
5. Gradle 작업 출력에서 다운로드 또는 해시 검증 오류를 찾습니다.

임의의 Minecraft 서버 JAR을 `implementation`으로 추가해 해결하지 마세요.

### `Minecraft server is not ready yet`

실제 서버 객체가 생기기 전에 `context.minecraft()`를 호출한 경우입니다. `onLoad`에서는 명령어와 리스너만 등록하고, 실제 서버 작업은 `ServerStartedEvent`, 명령어 콜백, 이후 동기 작업으로 옮기세요.

### Aerogel API 클래스 `NoClassDefFoundError`

일반적인 원인:

- 플러그인과 서버가 서로 다른 Aerogel API 변경판을 기준으로 빌드됨
- 플러그인 JAR 복사가 덜 끝났거나 외부에서 손상됨
- 외부 의존성을 shade하지 않음
- 실패한 빌드나 리로드 뒤 오래된 파일이 남음

플러그인을 다시 빌드하고 JAR을 검증하세요. Aerogel과 Gradle 플러그인을 함께 업데이트하고, API 구조가 바뀌었다면 전체 재시작하세요.

### 플러그인이 비활성화 상태로 표시됨

콘솔에서 해당 플러그인의 가장 처음 발생한 오류를 확인하세요. 뒤에 이어지는 링크 오류는 진입점 생성자, `onLoad`, 리스너 탐색, 메타데이터, 의존성 오류의 결과인 경우가 많습니다.

### 리로드해도 변경 사항이 반영되지 않음

일반 Java 클래스 변경은 새 클래스 로더에서 로드되어야 합니다. 반영되지 않는다면 다음을 확인하세요.

- 올바른 JAR을 다시 빌드해 복사했는지 확인
- 전역 스레드나 레지스트리에 정적 참조를 남기지 않기
- 플러그인이 만든 executor를 `onUnload`에서 종료
- 공용 API 변경 뒤 의존 플러그인을 함께 리로드
- Mixin 구조 변경이면 `/restart` 사용

### 콘솔 한글이 깨짐

소스와 리소스를 UTF-8로 저장하세요. Aerogel은 플러그인 Java 컴파일을 UTF-8로 설정합니다. 플랫폼 기본 인코딩을 사용하는 `FileReader`, `FileWriter`, `new String(bytes)` 대신 `StandardCharsets.UTF_8`을 명시하세요.

### 서버 틱이 멈추거나 밀림

이벤트 리스너, 명령어 콜백, 동기 예약 작업, Mixin 주입 코드에서 블로킹 작업을 찾으세요. 외부 I/O는 `scheduler().async(...)`로 옮기고, 최종 Minecraft 변경만 `scheduler().run(...)`으로 다시 동기 스레드에 넣습니다.

## 배포 전 확인 사항

플러그인을 배포하기 전에 확인하세요.

- [ ] JDK 25와 대상 Minecraft 버전에서 빌드하고 테스트함
- [ ] `clean build`를 실행했고 `validateAerogelPluginJar`가 통과함
- [ ] 플러그인 ID와 번역 리소스 경로가 정확히 일치함
- [ ] Aerogel, Minecraft, Mixin, Brigadier 클래스를 플러그인 JAR에 넣지 않음
- [ ] shade한 라이브러리의 라이선스와 고지를 포함함
- [ ] 깨끗한 최초 로드, `/plugins reload <id>`, `/plugins reload`를 테스트함
- [ ] Mixin이 있다면 `/restart`까지 테스트함
- [ ] 리로드 뒤 명령어와 클라이언트 자동완성이 정상임
- [ ] 올바른 이벤트 단계에서 취소가 작동함
- [ ] 설정 파일이 없거나 잘못된 경우도 테스트함
- [ ] `onUnload`에서 플러그인 스레드와 외부 자원을 정리함
- [ ] 언로드나 재시작을 넘어 플레이어, 월드, 엔티티, 메뉴, 레지스트리 객체를 보관하지 않음
- [ ] 저장 데이터 사용 전에 관리형 저장소 로드 실패를 기다려 확인하거나 명시적으로 처리함
- [ ] 사용자에게 지원 Aerogel·Minecraft 버전을 명시함

## 관련 문서

- [API 개요](API.md)
- [이벤트 목록과 동작 계약](EVENTS.md)
- [Gradle 플러그인 참고 자료](GRADLE_PLUGIN.md)
- [Mixin 가이드](MIXINS.md)
- [예제 플러그인](../example-plugin)
