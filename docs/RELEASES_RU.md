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

Ожидаемый SHA-256 сертификата выпуска 1.3.0:

    51b7a4ef033c2dec5ff6daa752e80f34a0dc443742fbf242b1af8259560cebe5

Если владелец **намеренно** переходит на другую подпись, задайте переменную ANDROID_SIGNING_CERT_SHA256 (нижний регистр, без двоеточий). Другой сертификат не позволит обновить прежнюю установку Android; автоматически генерировать замену или скрывать несовпадение workflow не будет. Секрет и ожидаемый отпечаток должны относиться к одному ключу.

Команда просмотра отпечатка:

    keytool -list -v -keystore /path/to/existing/debug.keystore -alias androiddebugkey

Не публикуйте содержимое ключа или Base64 в issue, PR либо чате. Используйте форму GitHub Actions Secrets или gh secret set с передачей значения через stdin.

## Порядок выпуска

1. Настроить сервер и прежний ключ подписи.
2. Дождаться успешного Android CI.
3. Запустить **Android APK Release** на main или отправить изменения в main.
4. Проверить опубликованные UniPlan.apk, SHA256SUMS и update.json на [странице Releases](https://github.com/Ibrahim200546/UniPlan/releases).

Сборка проверяет публичную конфигурацию и реальное наличие закрытой серверной схемы, выполняет SQL-тесты, unit tests обоих вариантов и lint основного варианта. Затем проверяет подпись APK, applicationId kz.uniplan.app и versionCode 1000 + github.run_number.

Номер версии в APK, на экране входа, в разделе «О приложении» и в update.json совпадает. Workflow увеличивает последний компонент версии из манифеста Latest на единицу. Для перехода на новую ветку версий задайте минимальную версию в config/version.txt. При повторном запуске уже опубликованной сборки номер сохраняется. Заголовок UniPlan X.Y.Z и тег vX.Y.Z формируются из versionName собранного APK. Внутренний versionCode по-прежнему растёт с номером запуска GitHub Actions.

После проверки контрольных сумм отдельный job публикует UniPlan.apk, SHA256SUMS и update.json из черновика как Latest.

Постоянная ссылка появится после первой успешной публикации: [UniPlan.apk](https://github.com/Ibrahim200546/UniPlan/releases/latest/download/UniPlan.apk). До этого она может возвращать 404. Артефакт **Android CI** имеет временную подпись runner и предназначен для тестирования, а не обновления прежних выпусков.

Успешная сборка не доказывает получение реальных email, доставку SMTP или работу на физическом устройстве. Эти проверки отдельно отражаются в [TEST_REPORT.md](TEST_REPORT.md).

## Обновления из приложения (1.3.0)

В опубликованном выпуске обязательны UniPlan.apk, SHA256SUMS и update.json. Последний генерируется scripts/update-manifest.py из output-metadata.json фактической сборки. APK подписан постоянным ключом этого компьютера; резервная копия ключа хранится вне репозитория в %USERPROFILE%/.android/uniplan-signing-backup.keystore. Ключ нельзя менять между обновлениями. Для CI по-прежнему нужен ANDROID_DEBUG_KEYSTORE_BASE64 с этим же ключом; GitHub credentials текущей сессии не дают доступа к Actions Secrets.

Приложение проверяет Latest при запуске раз в 6 часов и вручную из профиля, предлагает загрузку, проверяет SHA-256, applicationId, versionCode и совпадение сертификата. Затем открывает системный установщик. При необходимости предлагает разрешить установку из UniPlan и продолжает после возврата. Демо-вариант не публикуется. Старая установка с другим applicationId или ключом не может обновиться этим APK.
