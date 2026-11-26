# Сервер-посредник для МедАнализ

Ваш ключ Gemini хранится только на этом сервере (Cloudflare Worker, бесплатно).
Приложение обращается к серверу, сервер добавляет ключ и пересылает запрос в Google.
Пользователи ничего не вводят и ключ не видят.

## Разместить сервер (один раз, около 10 минут)

1. Зарегистрируйтесь на [dash.cloudflare.com](https://dash.cloudflare.com/sign-up) (бесплатно).
2. Слева откройте **Workers & Pages** → **Create** → **Create Worker** (шаблон «Hello World»).
   Назовите его `medanaliz-proxy` и нажмите **Deploy**.
3. Нажмите **Edit code**, удалите весь код и вставьте содержимое файла [`worker.js`](worker.js).
   Нажмите **Deploy**.
4. Вернитесь к воркеру → **Settings** → **Variables and Secrets** → **Add**:
   - Type **Secret**, Name `GEMINI_API_KEY`, Value: ваш ключ Gemini (AIza…).
   - Type **Secret**, Name `APP_TOKEN`, Value: придумайте длинный пароль (латиница и цифры,
     20+ символов). Он нужен, чтобы вашим сервером не пользовались чужие программы.
   Нажмите **Deploy** (или Save).
5. Скопируйте адрес воркера, он выглядит так: `https://medanaliz-proxy.ВАШЕ-ИМЯ.workers.dev`.
   Откройте его в браузере: должно появиться `MedAnaliz proxy is running`.

## Подключить сервер к сборке APK

В репозитории на GitHub: **Settings** → **Secrets and variables** → **Actions**.

- Вкладка **Variables** → **New repository variable**: Name `PROXY_URL`, Value: адрес воркера из шага 5.
- Вкладка **Secrets** → **New repository secret**: Name `APP_TOKEN`, Value: тот же пароль, что в шаге 4.

Затем **Actions** → **Build APK** → **Run workflow**. Через 5–10 минут в Releases появится APK,
в котором ключ вводить не нужно.

## Если нужно сменить ключ

Поменяйте секрет `GEMINI_API_KEY` в Cloudflare. Приложение пересобирать не нужно.
