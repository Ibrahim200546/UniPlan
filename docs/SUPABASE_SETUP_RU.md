# Настройка UniPlan в Supabase

Проект: **rrhuqwaytpeerirbnhlc**. Публичный URL и publishable key уже заданы в config/supabase.properties; их можно переопределить переменными SUPABASE_URL / SUPABASE_PUBLISHABLE_KEY или Gradle-параметрами supabaseUrl / supabasePublishableKey. Привилегированные ключи сборка отклоняет.

## Применение схемы

Миграция [20261030065209_uniplan_cloud_state.sql](../supabase/migrations/20261030065209_uniplan_cloud_state.sql) создана командой Supabase CLI migration new. Она создаёт только таблицу UniPlan и RPC, не переносит и не удаляет существующих пользователей. Если объект с таким именем уже существует, сначала изучите его схему и историю миграций; не удаляйте данные для обхода ошибки.

Вариант через GitHub: добавьте в **Settings → Secrets and variables → Actions** секреты **SUPABASE_ACCESS_TOKEN** и **SUPABASE_DB_PASSWORD**, затем запустите **Actions → Supabase Migration** на main. Workflow проверит SQL, покажет историю и dry-run, применит миграцию, выполнит security advisors, добавит Android callback и проверит закрытый анонимный доступ к таблице и RPC.

Вариант с компьютера владельца:

    npm ci
    npx supabase --help
    npx supabase login
    npx supabase link --project-ref rrhuqwaytpeerirbnhlc
    npx supabase migration list --linked
    npx supabase db push --linked --dry-run
    npx supabase db push --linked
    npx supabase migration list --linked
    npx supabase db advisors --linked --type security --fail-on error

CLI закреплён в package-lock.json (2.99.0). Не добавляйте access token или пароль базы в git, APK, логи или описание PR.

## Письма и возврат в Android

В [Authentication → URL Configuration](https://supabase.com/dashboard/project/rrhuqwaytpeerirbnhlc/auth/url-configuration) добавьте **точный** Redirect URL:

    kz.uniplan.app://auth/callback

Workflow миграции делает это автоматически через scripts/configure-auth.mjs, сохраняя существующие разрешённые URL и настройки безопасности. Для ручного запуска скрипту нужны SUPABASE_PROJECT_REF и SUPABASE_ACCESS_TOKEN в окружении. Включённое подтверждение почты отключать не нужно.

Стандартные шаблоны Confirmation / Recovery должны содержать {{ .ConfirmationURL }}. Указанный при запросе redirect_to и сохранённый на устройстве PKCE verifier привязывают ссылку к этой установке приложения. Открывайте письмо на том же Android-устройстве. При просроченной ссылке или переустановке запросите письмо заново. После обычного подтверждения почты вход по паролю также доступен.

У Supabase есть ограничения доставки встроенной почты. Для регистрации произвольных адресов настройте свой SMTP в Dashboard; иначе проверьте разрешённые адреса команды и лимиты. Ошибки отправки и ограничения частоты приложение показывает пользователю. [Документация SMTP](https://supabase.com/docs/guides/auth/auth-smtp), [парольная авторизация](https://supabase.com/docs/guides/auth/passwords), [PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow).

## Проверка

**npm test** выполняет миграцию в локальном PostgreSQL (PGlite): две роли пользователей, RLS, запрет anon, ограничение payload и compare-and-swap. **node scripts/check-cloud-config.mjs** проверяет реальный публичный API, наличие таблицы/RPC и запрет анонимного доступа. Эта команда должна завершиться успешно перед выпуском APK.

После настройки сервера проверьте своими тестовыми аккаунтами: регистрацию → письмо → вход; восстановление → письмо → новый пароль; повторный запуск → сохранённую сессию; синхронизацию на двух устройствах; конфликт одновременных изменений; отсутствие данных аккаунта A в аккаунте B. Публичного ключа недостаточно для применения SQL или настройки Auth. Серверные действия и полный email-сценарий не считаются проверенными без соответствующего доступа.

Firebase-аккаунты и локальные demo-профили автоматически не присваиваются новому Supabase-аккаунту: подтверждённого соответствия владельцев нет. Ранее сохранённые Room-данные остаются на устройстве. Текущий настроенный Supabase-проект не получает чужие локальные записи при первом входе.
