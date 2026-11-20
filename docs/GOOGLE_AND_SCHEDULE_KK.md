# UniPlan: Google және сабақ кестесі

## Google арқылы кіруді қосу

1. [Google Cloud → Clients](https://console.cloud.google.com/auth/clients) бөлімінде UniPlan жобасының **Web application** OAuth клиентін ашыңыз немесе жасаңыз.
2. **Authorized redirect URIs** тізіміне мынаны қосып, сақтаңыз:

   `https://rrhuqwaytpeerirbnhlc.supabase.co/auth/v1/callback`

3. [Supabase → Authentication → Sign In / Providers → Google](https://supabase.com/dashboard/project/rrhuqwaytpeerirbnhlc/auth/providers?method=google) бөлімінде **Enable Sign in with Google** қосыңыз. Google Cloud клиентінің **Client ID** және **Client Secret** мәндерін тиісті өрістерге енгізіп, **Save** басыңыз. Secret тек Supabase-та сақталады; оны APK-ға, GitHub-қа немесе чатқа салмаңыз.
4. [Supabase → URL Configuration](https://supabase.com/dashboard/project/rrhuqwaytpeerirbnhlc/auth/url-configuration) → **Redirect URLs → Add URL** арқылы `kz.uniplan.app://auth/callback` қосып, сақтаңыз.
5. UniPlan кіру не тіркелу экранында **Google арқылы жалғастыру** басыңыз. Браузерде Google аккаунтын таңдаған соң қосымшаға ораласыз. Бірінші кіру аккаунт жасайды, келесі кіру сол аккаунтты ашады.

Ресми дереккөздер: [Google provider](https://supabase.com/docs/guides/auth/social-sign-in/auth-google), [Redirect URLs](https://supabase.com/docs/guides/auth/redirect-urls).

## Кестені оңай қосу

**Кесте → Оңай қосу** ашыңыз. **Буферден қою** арқылы Markdown, Excel/TSV немесе HTML кестесін қойыңыз. **Сайттан** түймесі ашық HTTPS бетінің кестесін жүктейді. Жеке кабинетке кіруді талап ететін сайтта кестені браузерден көшіріп қойыңыз.

Алдын ала қарауда барлық сабақтарды тексеріп, **Кестеге қосу** басыңыз. Жоқ пәндер автоматты жасалады; дәл қайталанған сабақтар өткізіледі. Қате жолдар түзетілмейінше сақтау қолжетімсіз. ВКС логині/құпиясөзі импортталмайды. Бір ретте 500 сабаққа дейін.

## Сабақ ескертулері

**Кесте → Ескерту**: 5, 10, 15, 30 минут бұрын немесе өшіру. Әдепкі мән — 10 минут. Android сұрағанда хабарлама рұқсатын беріңіз. Дәл уақыт үшін UniPlan-ға «Дабылдар мен еске салғыштар» рұқсатын қосыңыз; онсыз Android хабарламаны кешіктіруі мүмкін.

Ескерту интернетсіз жұмыс істейді, пән атауын, уақытын және аудиторияны көрсетеді. Сабақ өзгерсе, жойылса, уақыт белдеуі өзгерсе немесе құрылғы қайта қосылса, келесі ескерту қайта жоспарланады. Аккаунттан шыққанда оның ескертулері тоқтайды.
