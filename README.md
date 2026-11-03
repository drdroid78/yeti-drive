# YETI DRIVE 0.3 — clean Android build

Тестовый Android-каркас лаунчера в альбомной ориентации.

## Сборка через GitHub Actions

После загрузки структуры проекта в корень репозитория откройте вкладку Actions.
Успешная сборка опубликует артефакт `YETI-DRIVE-debug-apk`.

## Конфигурация

- JDK 17
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- Kotlin Android plugin 2.0.21
- compileSdk 35, minSdk 26, targetSdk 32

## Текущее состояние

Это только проверочный каркас. Область карты пока заглушка, нажатие запускает
установленный Яндекс Навигатор; кнопка музыки запускает Яндекс Музыку.
Интеграция MediaSession, обложки, нижняя панель ярлыков и полноценные экраны
ещё не реализованы. Совместимость с конкретной прошивкой HarmonyOS 3 требуется
проверить на устройстве. Это приложение пока не назначается системным Home launcher.

## Структура

- `settings.gradle` — репозитории плагинов и модуль app
- `build.gradle` — версии Gradle-плагинов
- `app/build.gradle` — настройки Android-приложения
- `app/src/main/` — Manifest и Kotlin-код
- `.github/workflows/android.yml` — CI-сборка APK
