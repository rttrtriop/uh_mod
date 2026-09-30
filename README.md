# UH Mod Assistant Backend & Dynamic Function Core

Production-grade бэкенд и удаленное хранилище динамических функций для клиентской модификации «Учусь в Кузбассе».

---

## ✦ Архитектура решения: APK как тонкий приёмник ✦

Мобильный APK выступает **исключительно в роли клиентского приёмника/загрузчика (bootstrap loader & runtime runner)**. Вся бизнес-логика, алгоритмы, парсеры и функции хранятся на GitHub и бэкенде:

```
[ GitHub Repo (Этот репозиторий) ] ───► [ GitHub Raw / Pages / Render ]
                                                    │
                                                    ▼ (GET /bootstrap/payload.json)
                                       [ APK Приёмник в приложении ]
                                                    │
                                     ┌──────────────┴──────────────┐
                                     ▼                             ▼
                          [ Локальный кэш ]              [ JS Runtime / WebView ]
                          (mod_payload.json)            (uh_assistant_core.js)
```

---

## Структура репозитория

```text
backend/
├── assets/
│   ├── uh_assistant_core.js     # Автономный JS-скрипт (решение тестов, подсветка ответов, UI бейджи)
│   ├── ic_tile_mod.png          # Нативная иконка плитки настроек (34x34 dp)
│   └── ic_chevron_right.xml     # Векторный шеврон перехода
├── payload.json                 # Полный статический payload для хостинга на GitHub Raw / Pages
├── main.py                      # FastAPI сервер (Gemini 3.5 Flash Lite пул, авто-пинг, ETag 304)
├── requirements.txt             # Минимальные зависимости
├── render.yaml                  # Конфигурация для мгновенного деплоя на Render
├── Dockerfile                   # Docker-контейнер
├── GradeEditorManager.java      # Исходный код синхронизации оценок (1..5, ОП, Н, У, Б) в GetStorage.gs
└── README.md                    # Это руководство
```

---

## 1. Загрузка на GitHub

Чтобы APK получал функции прямо с вашего GitHub:

```bash
git init
git add .
git commit -m "feat: initial commit with mod functions and payload"
git branch -M main
git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO>.git
git push -u origin main
```

После пуша ваш `payload.json` будет доступен по прямому адресу GitHub Raw:
```text
https://raw.githubusercontent.com/<YOUR_USERNAME>/<YOUR_REPO>/main/backend/payload.json
```

---

## 2. Динамическое изменение функций без пересборки APK

Вам больше **НЕ нужно пересобирать APK**, чтобы изменить функционал:
1. Отредактируйте `assets/uh_assistant_core.js` или параметры в `payload.json`.
2. Сделайте `git push origin main`.
3. При следующем запуске приложения `LoaderActivity` автоматически скачает свежий скрипт, обновит локальный кэш и активирует новый функционал!

---

## 3. Деплой AI-бэкенда на Render.com

Для решения тестов через Gemini API (`gemini-3.5-flash-lite` с ротацией 5 ключей) бэкенд развертывается на Render:

1. Создайте Web Service на [dashboard.render.com](https://dashboard.render.com).
2. Подключите ваш GitHub репозиторий.
3. Укажите:
   - **Root Directory**: `backend`
   - **Build Command**: `pip install -r requirements.txt`
   - **Start Command**: `uvicorn main:app --host 0.0.0.0 --port $PORT`
4. В разделе **Environment Variables** можно переопределить ключи:
   - `GEMINI_KEY_1` ... `GEMINI_KEY_5`
   - `RENDER_EXTERNAL_URL`: URL вашего сервиса (для встроенного механизма self-ping каждые 10 минут, предотвращающего сон на бесплатном тарифе).

---

## 4. Поддерживаемые типы отметок в системе

Полная матрица базы данных Ruobr API, настроенная в `payload.json`:

| Обозначение | Тип | Статус / Значение | Название | Цвет |
|:---:|:---:|:---:|:---:|:---:|
| **5** | mark | 5 | Отлично | `#00E676` (Зеленый) |
| **4** | mark | 4 | Хорошо | `#1E88E5` (Синий) |
| **3** | mark | 3 | Удовлетворительно | `#FB8C00` (Оранжевый) |
| **2** | mark | 2 | Неудовлетворительно | `#E53935` (Красный) |
| **1** | mark | 1 | Кол | `#8E0000` (Темно-бордовый) |
| **ОП** | attendance | 30 | Опоздание | `#FFA726` (Янтарный) |
| **Н** | attendance | 90 | Не был на уроке | `#EF5350` (Коралловый) |
| **У** | attendance | 10 | Уважительная причина | `#42A5F5` (Голубой) |
| **Б** | attendance | 20 | Болел | `#AB47BC` (Пурпурный) |
