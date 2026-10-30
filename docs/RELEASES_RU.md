> СТАТУС: это проект будущей автоматизации. Файл сохранён как docs/android-release.yml.draft и не запускается GitHub Actions. Требуется завершить основной Supabase-вариант, параметры Gradle и настройку ключа подписи. Выпуски пока не опубликованы.

# Автоматические APK в GitHub Releases

После каждого push в `main` workflow `.github/workflows/android-release.yml` запускает unit tests и собирает основное приложение с настоящей авторизацией. Ручной запуск доступен в **Actions → Android APK Release → Run workflow** для ветки `main`.

Постоянная ссылка после первой успешной публикации: [скачать UniPlan.apk](https://github.com/Ibrahim200546/UniPlan/releases/latest/download/UniPlan.apk). [Все выпуски](https://github.com/Ibrahim200546/UniPlan/releases) и [результаты workflow](https://github.com/Ibrahim200546/UniPlan/actions/workflows/android-release.yml) доступны отдельно. Пока настройка и первая публикация не завершены, ссылка на APK может возвращать 404.

## Однократная настройка репозитория

В **Settings → Secrets and variables → Actions** добавьте:

| Тип | Имя | Значение |
|---|---|---|
| Secret | `ANDROID_DEBUG_KEYSTORE_BASE64` | Base64 **существующего** ключа подписи UniPlan; alias `androiddebugkey`, пароль хранилища `android` |
| Variable | `SUPABASE_URL` | HTTPS URL пользовательского проекта Supabase |
| Variable | `SUPABASE_PUBLISHABLE_KEY` | Публичный клиентский ключ с префиксом `sb_publishable_` |

Приватный `service_role` или secret API key нельзя помещать в Android-приложение. Публичный publishable key включается в APK и не считается серверным секретом. Ограничения доступа должны обеспечиваться настройками авторизации и политиками сервиса. [Типы ключей Supabase](https://supabase.com/docs/guides/api/api-keys).

Ключ подписи не заменяется автоматически. Ожидаемый SHA-256 сертификата:

```text
1b5edf62b174211a13ccc32883cd74006cca47cb0fb368ae839b9ed6e0651a42
```

Отсутствующий secret, неверный сертификат или недопустимая конфигурация приводят к явной ошибке. Не создавайте новый ключ вместо прежнего: для обновления установленного приложения нужны тот же applicationId и совместимая подпись. Ключ нельзя коммитить в репозиторий или добавлять к Release. Он восстанавливается только на временном runner и удаляется в завершающем шаге.

## Что делает workflow

1. Read-only job `build` получает точный commit, использует Ubuntu 24.04, Temurin JDK 17, Android SDK 35 / Build-Tools 35.0.0 и Gradle Wrapper проекта.
2. Проверяет публичную конфигурацию, восстанавливает прежний ключ и сверяет сертификат до сборки.
3. Запускает `:app:testDebugUnitTest` и `:app:assembleDebug`. Demo-вариант не публикуется. Параметр `ciBuildNumber` задаёт `versionCode = 1000 + номер запуска`; versionName получает суффикс сборки.
4. Проверяет подпись готового APK через `apksigner`, applicationId и versionCode. Формирует `UniPlan.apk` и `SHA256SUMS`, передаёт их отдельному job как артефакт.
5. Job `publish` с `contents: write` создаёт **черновик** `build-N` на SHA собранного commit. Сначала загружает оба файла, затем публикует выпуск и помечает его Latest. Токен записи доступен только этому job, исходники приложения в нём не выполняются.

Новый push отменяет устаревший выполняющийся workflow. Перед созданием и перед публикацией проверяется текущая вершина `main`; устаревшая сборка не должна заменять Latest. При отмене во время загрузки может остаться черновик, но не опубликованный выпуск без нужных файлов. Повторный запуск уже опубликованного `build-N` оставляет его неизменным; файлы разрешено заменять только в черновике с тем же целевым SHA. [Concurrency GitHub Actions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#concurrency), [создание выпуска](https://cli.github.com/manual/gh_release_create), [публикация черновика](https://cli.github.com/manual/gh_release_edit).

APK подписан существующим debug-ключом и предназначен для прямой установки учебного приложения. Это не публикация в Google Play. Успех workflow означает сборку, unit tests и проверку подписи; он не доказывает запуск UI на физическом устройстве или сквозную проверку настоящего Supabase.

Для проверки загруженного файла сравните SHA-256 с `SHA256SUMS`, приложенным к тому же выпуску. На Windows можно использовать `Get-FileHash .\UniPlan.apk -Algorithm SHA256`. Сведения о ручных проверках приложения находятся в `TEST_REPORT.md`.
