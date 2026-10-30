# GitHub Actions и APK Releases

В .github/workflows/ находятся три определения workflow:

| Workflow | Запуск | Результат |
|---|---|---|
| Android CI | PR, push в main/codex, вручную | SQL/RLS-тест, Android unit tests, lint и тестовый APK |
| Supabase Migration | Вручную на main | Версионные миграции, security advisors, Android callback, проверка API |
| Android APK Release | Push в main или вручную на main | Проверенный APK и SHA256SUMS в GitHub Releases |

## Что настроить один раз

Публичный Supabase URL и publishable key уже заданы для UniPlan. При необходимости их переопределяют переменные репозитория SUPABASE_URL и SUPABASE_PUBLISHABLE_KEY. Перед выпуском примените [миграцию и настройку Auth](SUPABASE_SETUP_RU.md).

Для подписи добавьте secret **ANDROID_DEBUG_KEYSTORE_BASE64** — Base64 прежнего ключа UniPlan, alias androiddebugkey, пароль android. Он не добавляется в git или Release, восстанавливается только на runner и удаляется после сборки. Это учебный APK для прямой установки, подписанный постоянным debug-ключом; публикация в Google Play не настраивается.

Ожидаемый SHA-256 сертификата прежних выпусков:

    1b5edf62b174211a13ccc32883cd74006cca47cb0fb368ae839b9ed6e0651a42

Если владелец **намеренно** переходит на другую подпись, задайте переменную ANDROID_SIGNING_CERT_SHA256 (нижний регистр, без двоеточий). Другой сертификат не позволит обновить прежнюю установку Android; автоматически генерировать замену или скрывать несовпадение workflow не будет. Секрет и ожидаемый отпечаток должны относиться к одному ключу.

Команда просмотра отпечатка:

    keytool -list -v -keystore /path/to/existing/debug.keystore -alias androiddebugkey

Не публикуйте содержимое ключа или Base64 в issue, PR либо чате. Используйте форму GitHub Actions Secrets или gh secret set с передачей значения через stdin.

## Порядок выпуска

1. Настроить сервер и прежний ключ подписи.
2. Дождаться успешного Android CI.
3. Запустить **Android APK Release** на main или отправить изменения в main.
4. Проверить опубликованные UniPlan.apk и SHA256SUMS на [странице Releases](https://github.com/Ibrahim200546/UniPlan/releases).

Сборка проверяет публичную конфигурацию и реальное наличие закрытой серверной схемы, выполняет SQL-тесты, unit tests обоих вариантов и lint основного варианта. Затем проверяет подпись APK, applicationId kz.uniplan.app и versionCode 1000 + github.run_number.

Отдельный job с contents: write получает только готовые артефакты. Он создаёт черновик build-N, загружает оба файла и только затем публикует выпуск. Устаревшая сборка не заменяет Latest; уже опубликованный build-N повторный запуск не изменяет. Actions закреплены по проверенным commit SHA. [Разрешения workflow](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#permissions), [создание Release](https://cli.github.com/manual/gh_release_create).

Постоянная ссылка появится после первой успешной публикации: [UniPlan.apk](https://github.com/Ibrahim200546/UniPlan/releases/latest/download/UniPlan.apk). До этого она может возвращать 404. Артефакт **Android CI** имеет временную подпись runner и предназначен для тестирования, а не обновления прежних выпусков.

Успешная сборка не доказывает получение реальных email, доставку SMTP или работу на физическом устройстве. Эти проверки отдельно отражаются в [TEST_REPORT.md](TEST_REPORT.md).
