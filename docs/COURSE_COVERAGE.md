# Соответствие девяти лекциям

Основание — все девять DOCX из предоставленного архива `Дәрістер.zip`. Документы распакованы, их текст прочитан; примеры сопоставлены с реализованными сценариями. Эта таблица описывает реализацию и способ её демонстрации. Она **не заменяет результаты тестирования**: фактически выполненные проверки и ограничения перечислены в `TEST_REPORT.md`.

В столбце Java указаны пути относительно `app/src/main/java/kz/uniplan/app/`, в столбце ресурсов — относительно `app/src/main/res/`. Пути source set `demo` и `firebase`, а также Gradle-файлы указаны от корня проекта.

| Лекция | Реализованная функция | Конкретный Java/XML-файл | Как проверить |
|---|---|---|---|
| **1. Введение, архитектура и история Android, среда разработки** | Нативное Android-приложение Java/XML, SDK, Gradle Wrapper, два варианта приложения, debug APK, журналы жизненного цикла | `settings.gradle`, `build.gradle`, `app/build.gradle`, `gradle/wrapper/gradle-wrapper.properties`; `app/src/main/AndroidManifest.xml`; Java: `ui/common/BaseActivity.java`, `ui/common/BaseFragment.java`; теория в `docs/DEFENSE_KK.md` | Открыть проект целиком в Android Studio; выбрать `demoDebug`; выполнить команды из README, запустить APK. В Logcat отфильтровать `UniPlanLifecycle`. Уметь объяснить уровни Linux → HAL → ART/библиотеки → Framework → приложения |
| **2. Java, ООП и структура проекта** | Инкапсуляция и проверка состояния доменной модели; абстрактный тип учебной работы, наследование задания/экзамена, полиморфный тип; интерфейс авторизации и две реализации; разделение исходников и ресурсов | Java: `domain/model/StudyItem.java`, `domain/model/AssignmentItem.java`, `domain/model/ExamItem.java`, `util/Validation.java`, `ui/tasks/CoreText.java`, `data/auth/AuthRepository.java`; `app/src/demo/java/kz/uniplan/app/data/auth/DemoAuthRepository.java`; `app/src/firebase/java/kz/uniplan/app/data/auth/FirebaseAuthRepository.java` | Создать обычное задание и экзамен, сравнить подписи карточек. В коде показать закрытые поля и getters, `type()`/`typeKey()` с `@Override`, перегруженные `isOverdue()` и `isOverdue(long)`. Показать, что общие экраны зависят от интерфейса авторизации |
| **3. Activity, жизненный цикл, Intent и Bundle** | Отдельные AuthActivity и MainActivity; переход после входа через explicit Intent с приветственным extra, вход в закрытую часть только при действующей сессии; полный жизненный цикл с `onRestart`; implicit Intent по учебной ссылке | Java: `ui/auth/AuthActivity.java`, `ui/MainActivity.java`, `ui/common/BaseActivity.java`, `ui/profile/ProfileFragment.java`; XML: `layout/activity_auth.xml`, `layout/activity_main.xml`; `app/src/main/AndroidManifest.xml` | Войти, открыть другое приложение и вернуться, повернуть устройство; сравнить события в Logcat. Выйти и нажать Back: закрытый экран не должен восстановиться. В профиле открыть учебную ссылку. Extra отвечает за приветствие, а не за UID владельца |
| **4. XML Layout, View и ViewGroup** | LinearLayout для форм и вертикального содержимого; RelativeLayout для значка и двух текстов предмета; ConstraintLayout для основного контейнера экрана | XML: `layout/fragment_task_edit.xml`, `layout/fragment_lesson_edit.xml`, `layout/fragment_note_edit.xml`, `layout/item_core_subject.xml`, `layout/activity_main.xml` | Открыть соответствующие layouts в Code/Design. Показать `orientation`, `layout_below`/`layout_toEndOf`, constraints. На устройстве открыть длинную форму, прокрутить и увеличить шрифт. Объяснить, что TextView/Button являются View, а layout — ViewGroup |
| **5. Основные UI-компоненты** | TextView и EditText в формах; MaterialButton для действий; ImageView предмета; CheckBox выполнения; RadioGroup/RadioButton для единственного вида/приоритета; Toast результата и Snackbar с рабочей отменой удаления | Java: `ui/tasks/TaskEditFragment.java`, `ui/tasks/TaskDetailFragment.java`, `ui/tasks/TasksFragment.java`, `ui/subjects/SubjectAdapter.java`, `ui/notes/NoteEditFragment.java`; XML: `layout/fragment_task_edit.xml`, `layout/fragment_task_detail.xml`, `layout/item_core_subject.xml` | Заполнить форму, выбрать один приоритет, сохранить, отметить выполнение. Удалить задание и нажать «Болдырмау / Отменить» в Snackbar: запись возвращается с исходным ID без копии. При отмене TXT-экспорта показывается нейтральный Toast |
| **6. Списки, Adapter, ViewHolder и CardView** | ListView предметов с собственным BaseAdapter, включая реальный выбор предмета; RecyclerView заданий, занятий и заметок; MaterialCardView; LinearLayoutManager и GridLayoutManager | Java: `ui/subjects/SubjectAdapter.java`, `ui/subjects/SubjectListFragment.java`, `ui/subjects/SubjectPickerFragment.java`, `ui/tasks/TaskAdapter.java`, `ui/schedule/LessonAdapter.java`, `ui/notes/NoteAdapter.java`, `ui/notes/NotesFragment.java`; XML: `layout/fragment_subject_list.xml`, `layout/item_core_task.xml`, `layout/row_note.xml` | Выбрать предмет в форме: это ListView. В коде TaskAdapter/NoteAdapter показать `onCreateViewHolder`, `onBindViewHolder`, `getItemCount` и Holder. В «Жазбалар / Заметки» переключить список/сетку; при крупном шрифте применяется один столбец для читаемости |
| **7. Fragments, FragmentManager и коммуникация** | Debug-журнал жизненного цикла; arguments по ID; дочерние редактор и предпросмотр с `replace`/back stack; SharedViewModel/LiveData одного родителя; одноразовый результат выбора предмета; callback дочерних быстрых действий | Java: `ui/common/BaseFragment.java`, `ui/notes/NoteEditFragment.java`, `ui/notes/NoteEditorFragment.java`, `ui/notes/NotePreviewFragment.java`, `ui/notes/NoteEditViewModel.java`, `ui/subjects/SubjectPickerFragment.java`, `ui/home/QuickActionsFragment.java`, `ui/home/HomeFragment.java`; XML: `layout/fragment_note_edit.xml`, `layout/fragment_note_editor.xml`, `layout/fragment_note_preview.xml`, `layout/fragment_quick_actions.xml` | В заметке ввести текст, открыть предпросмотр и нажать Back: возврат сначала к редактору. Повернуть экран без дублирования дочерних фрагментов. Показать `new ViewModelProvider(requireParentFragment())` в обеих дочерних частях; `getViewLifecycleOwner()` и очистку binding. Выбрать предмет и показать одинаковый result key/FragmentManager. На главной «Создать заметку» вызывает callback реального дочернего фрагмента |
| **8. Navigation Component, нижняя навигация, вкладки** | NavHostFragment, graph, destinations/actions и NavController; пять пунктов BottomNavigationView, связанных через NavigationUI; вкладки заданий на TabLayout, ViewPager2, FragmentStateAdapter, TabLayoutMediator | Java: `ui/MainActivity.java`, `ui/tasks/TasksFragment.java`, `ui/tasks/TaskPageFragment.java`, `ui/common/Ui.java`; XML: `navigation/nav_graph.xml`, `menu/bottom_navigation.xml`, `layout/activity_main.xml`, `layout/fragment_tasks.xml` | Перейти по всем пяти разделам. Открыть подробности задания и форму изменения, вернуться назад. Переключать вкладки нажатием и свайпом. Проверить, что ID нижнего меню совпадают с destinations, а mediator отсоединяется при уничтожении View |
| **9. SharedPreferences, внутренние и внешние файлы** | Разделённые глобальные/аккаунтные настройки; UTF-8 копии заметок в Internal Storage и app-specific External Storage; явное чтение копии; отдельный публичный TXT-экспорт через SAF; Device Explorer | Java: `util/SettingsStore.java`, `ui/profile/ProfileFragment.java`, `data/files/NoteFiles.java`, `ui/notes/NoteEditViewModel.java`, `ui/notes/NoteEditFragment.java`; XML: `layout/fragment_profile.xml`, `layout/fragment_note_edit.xml`; тест: `app/src/androidTest/java/kz/uniplan/app/data/DataIsolationTest.java` | Сменить тему/язык, перезапустить приложение. Сохранить заметку с `Ә Ғ Қ Ң Ө Ұ Ү Һ І`, записать и открыть внутреннюю/внешнюю копии. Изменения из копии остаются в редакторе до «Сохранить». Через Device Explorer проверить `files/note_copies/<SHA-256 UID>/note_<ID>.txt`. Отменить SAF, затем выполнить реальный экспорт |

## Точные исходные документы

| № | Файл из архива | Основное содержание |
|---|---|---|
| 1 | `1789439917732_Дәріс 1.docx` | Мобильные приложения, история и архитектура Android, Android Studio/SDK/AVD/Gradle |
| 2 | `1789439922000_Дәріс 2.docx` | Java, класс/объект, инкапсуляция, наследование, полиморфизм, абстракция, структура проекта |
| 3 | `1789440261351_Дәріс 3.docx` | Жизненный цикл Activity, explicit/implicit Intent, extras/Bundle |
| 4 | `1789439926697_Дәріс 4.docx` | View/ViewGroup, LinearLayout, RelativeLayout, ConstraintLayout |
| 5 | `1790838108427_Дәріс 5.docx` | TextView, EditText, Button, ImageView, CheckBox, RadioButton, Toast, Snackbar |
| 6 | `1791374035595_Дәріс 6.docx` | ListView, RecyclerView, Adapter/ViewHolder, LayoutManager, CardView |
| 7 | `1792048641562_Дәріс 7.docx` | Жизненный цикл Fragment, FragmentManager, транзакции/back stack, arguments, callback, SharedViewModel и Fragment Result |
| 8 | `1792580823729_Дәріс 8.docx` | Navigation Component, BottomNavigationView, TabLayout/ViewPager2 |
| 9 | `1793257537672_Дәріс 9.docx` | SharedPreferences, внутренние/внешние файлы и Device Explorer |

## Как адаптированы практические задания

- **Book/EBook из лекции 2:** в UniPlan используются `StudyItem → AssignmentItem/ExamItem`. Это адаптация наследования, инкапсуляции и полиморфизма к предметной области, а не выполненное упражнение про книги.
- **Приветствие и две Activity из лекции 3:** реализованы переход из входа в основную часть, имя через extra и журнал жизненного цикла. Доступ к данным определяется сессией, а не extra.
- **Простые кнопки, фрукты и выбор пола из лекции 5:** те же компоненты участвуют в сохранении, выполнении задач и выборе приоритета. Отдельных неиспользуемых учебных экранов нет.
- **Десять стран и флаги из лекции 6:** вместо стран отображаются реальные предметы, задания, занятия и заметки. Список/сетка и карточки используются в пользовательских сценариях.
- **List/Detail и передача текста из лекции 7:** в проекте есть ID-аргументы, редактор/предпросмотр одной заметки, callback быстрых действий и результат выбора предмета. Альтернативные небезопасные способы фиксации транзакций ради демонстрации не вызываются.
- **Три основных пункта из лекции 8:** схема расширена до пяти разделов UniPlan; принцип связи menu IDs и graph destinations сохранён.
- **Блокнот и тема из лекции 9:** тема сохраняется в SharedPreferences, текстовые копии читаются и записываются в двух местах. Room добавлен по отдельному требованию задания как основной источник заметок.

## Уточнения к учебным упрощениям

1. `onDestroy()` не гарантирован при завершении процесса. В проекте нет зависимости сохранения записей от этого callback.
2. `getExternalFilesDir()` является собственной внешней папкой приложения; она не называется общедоступной папкой «Загрузки». Для выбранного пользователем места используется SAF без широкого разрешения на файловую систему.
3. Room и Firebase Authentication — расширения задания сверх девяти лекций. Room управляет локальными структурированными данными, Firebase подтверждает аккаунт. Они не добавляют облачную синхронизацию автоматически.
4. Пароли не сохраняются в SharedPreferences. Технические имена классов и таблиц находятся в коде и материалах защиты, а не в обычных пользовательских формах.
5. Точные версии библиотек закреплены в Gradle и README; старые номера из примеров лекций не копировались как обязательные.

## Где искать файлы на устройстве

Для debug-варианта откройте Android Studio → **View → Tool Windows → Device Explorer**. Для demo пакет — `kz.uniplan.app.demo`, для основного варианта — `kz.uniplan.app`.

- Внутренние копии: `/data/data/<package>/files/note_copies/<SHA-256 UID>/note_<ID>.txt` (на современных устройствах также отображается `/data/user/0/<package>/...`).
- Внешние копии: `/storage/emulated/0/Android/data/<package>/files/note_copies/<SHA-256 UID>/note_<ID>.txt`.
- Настройки: `/data/data/<package>/shared_prefs/`.
- Room: `/data/data/<package>/databases/`; редактировать файлы базы вручную не следует. Для просмотра удобно использовать **App Inspection → Database Inspector** на совместимом эмуляторе или устройстве.
- Экспортированный TXT расположен там, куда пользователь сохранил его системным диалогом. Он не обязан совпадать с внутренним или собственным внешним каталогом.

Хеш UID в пути обеспечивает безопасное имя каталога, а не шифрование. Доступность Device Explorer зависит от устройства, отладочной сборки и разрешений системы.

