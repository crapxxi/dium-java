# DIUM — Быстрый предзаказ еды в университетах

REST API бэкенд для платформы DIUM: студенты оформляют заказы в университетских кафе заранее, минуя очереди. Владелец заведения получает уведомление в WhatsApp, управляет статусом заказа и принимает оплату через Kaspi.

---

## Стек технологий

| Слой | Технология |
|---|---|
| Язык / платформа | Java 17, Spring Boot 4.0.x |
| БД | PostgreSQL + Spring Data JPA |
| Безопасность | Spring Security, JWT (jjwt 0.11), 2FA: WhatsApp OTP + TOTP (dev.samstevens.totp) |
| Уведомления | WhatsApp (Green API) |
| Хранение изображений | Cloudinary |
| Кэш | Caffeine (in-memory JCache) |
| Rate limiting | Bucket4j |
| Маппинг DTO | MapStruct |
| Документация API | SpringDoc OpenAPI (Swagger UI) |
| Контейнеризация | Docker (multi-stage build) |

---

## Архитектура

```
┌─────────────────────────────────────────────────────────────────┐
│                         HTTP Clients                            │
│                  (Mobile App / Web Frontend)                    │
└────────────────────────────┬────────────────────────────────────┘
                             │  HTTPS
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot App                            │
│                                                                 │
│  ┌──────────────┐   ┌─────────────────────────────────────────┐ │
│  │  Bucket4j    │   │         Security Layer                  │ │
│  │ Rate Limiter │   │  JwtAuthenticationFilter + SecurityConfig│ │
│  └──────┬───────┘   └───────────────────┬─────────────────────┘ │
│         │                               │                       │
│         ▼                               ▼                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                     Controllers                          │   │
│  │  AuthController  OrderController  VenueController        │   │
│  │  ProductController               ModifierController      │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │ DTO                               │
│                             ▼                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                      Services                            │   │
│  │  AuthService    OrderService    VenueService             │   │
│  │  ProductService ModifierService JwtService               │   │
│  │  FileService    OrderExpirationService                   │   │
│  └──────────┬───────────────────────────┬───────────────────┘   │
│             │                           │                       │
│             │ AOP (AfterReturning)      │                       │
│             ▼                           ▼                       │
│  ┌──────────────────┐   ┌──────────────────────────────────┐    │
│  │MessageSenderAspect│  │        Repositories              │    │
│  │ (WhatsApp notify) │  │  UserRepo  OrderRepo  VenueRepo  │    │
│  └────────┬─────────┘  │  ProductRepo  OtpRepo  ...       │    │
│           │             └──────────────┬───────────────────┘    │
│           │                            │                        │
└───────────┼────────────────────────────┼────────────────────────┘
            │                            │
            ▼                            ▼
   ┌─────────────────┐          ┌─────────────────┐
   │  WhatsApp API   │          │   PostgreSQL DB  │
   │  (Green API)    │          │                 │
   └─────────────────┘          └─────────────────┘
            │
            ▼
   ┌─────────────────┐
   │   Cloudinary    │
   │ (Image Storage) │
   └─────────────────┘
```

### Слои приложения

**Controllers** — принимают HTTP-запросы, валидируют входные данные (`@Valid`), делегируют бизнес-логику сервисам. Разграничение доступа через `@PreAuthorize`.

**Services** — вся бизнес-логика. Каждый сервис отвечает за одну доменную область.

**Repositories** — доступ к БД через Spring Data JPA. `BaseRepository` расширяет `JpaRepository` и добавляет метод `findByIdOrThrow`.

**DTOs + Mappers** — запросы и ответы изолированы от моделей. MapStruct генерирует маппинг на этапе компиляции.

**AOP Aspect** — `MessageSenderAspect` перехватывает успешный возврат из `OrderService.createOrder` и асинхронно шлёт WhatsApp-уведомление владельцу заведения.

---

## Доменная модель

```
User (CLIENT / VENUE_OWNER / ADMIN)
 │
 ├── owns ──► Venue
 │             ├── has ──► Product
 │             │             └── has ──► ModifierGroup
 │             │                          └── has ──► Modifier
 │             └── receives ──► Order
 │                               └── contains ──► OrderItem
 │                                                  └── uses ──► Modifier[]
 └── places ──► Order
```

### Сущности

| Сущность | Описание |
|---|---|
| `User` | Пользователь системы; реализует `UserDetails`; идентифицируется по номеру телефона; хранит настройки 2FA |
| `Venue` | Заведение (кафе/буфет) в университете; у каждого заведения один `VENUE_OWNER` |
| `Product` | Позиция меню с категорией (`ProductCategory`), ценой и флагом наличия |
| `ModifierGroup` | Группа опций к продукту (например «Соусы», «Размер») |
| `Modifier` | Конкретная опция с дельтой цены (`priceDelta`) |
| `Order` | Заказ клиента; содержит список `OrderItem`, адрес доставки, статус, `pickupCode` |
| `OrderItem` | Строка заказа: продукт × количество × цена на момент покупки + выбранные модификаторы |
| `OtpCode` | Одноразовый код (активация / сброс пароля / вход с 2FA); хранится в Caffeine-кэше, ключ = телефон + назначение, TTL 5 мин |

---

## Жизненный цикл заказа

```
CLIENT создаёт заказ
        │
        ▼
    PENDING  ──── VENUE_OWNER отклоняет ──► CANCELLED
        │                                    (WhatsApp → CLIENT)
        │ VENUE_OWNER принимает
        ▼
   PREPARING  ◄─ WhatsApp уведомление → CLIENT ("Заказ готовится")
        │
        │ VENUE_OWNER готов выдать
        ▼
     READY    ◄─ WhatsApp уведомление → CLIENT ("Заказ готов, код: XXXX")
        │
        │ VENUE_OWNER завершает
        ▼
   COMPLETED
```

Генерация `pickupCode`: детерминированная формула `(id × 48271) % 9000 + 1000` — всегда 4-значное число.

---

## Аутентификация

Все защищённые эндпоинты требуют заголовок `Authorization: Bearer <JWT>`.

```
Регистрация → OTP в WhatsApp → Активация → JWT
Вход: телефон + пароль → (если включена 2FA) временный токен → код → JWT
```

### Регистрация и подтверждение телефона

1. `POST /api/v1/auth/register` — создаёт пользователя (`isConfirmed = false`) и отправляет 6-значный код в WhatsApp.
   Если код отправить не удалось, пользователь **не создаётся**, клиент получает `502` с понятным сообщением.
2. `POST /api/v1/auth/activate?phone=&code=` — подтверждает телефон, возвращает JWT.
3. `POST /api/v1/auth/resend-code?phone=` — повторная отправка кода (не чаще раза в 60 секунд).
4. `POST /api/v1/auth/forgot-password?phone=` → `POST /api/v1/auth/reset-password` — сброс пароля по коду.

Аккаунт заблокирован (`isAccountNonLocked = false`), пока `isConfirmed = false`.

**Номера телефонов** нормализуются на бэкенде к виду `7XXXXXXXXXX`: принимаются `+7 (707) 123-45-67`,
`8 707 123 45 67`, `77071234567`, `7071234567`. Некорректный номер → `422`.

### Одноразовые коды (OTP)

| Свойство | Значение |
|---|---|
| Длина / срок жизни | 6 цифр / 5 минут |
| Попытки ввода | 5, затем код аннулируется |
| Повторная отправка | не чаще 1 раза в 60 секунд (`429` + `Retry-After`) |
| Назначения | `ACTIVATION`, `PASSWORD_RESET`, `LOGIN_2FA` — ключ кэша = телефон + назначение, код одного назначения не подходит для другого |
| Сравнение | за постоянное время (`MessageDigest.isEqual`) |

Отправка считается успешной только при HTTP 2xx **и** наличии `idMessage` в ответе Green API.

### Двухфакторная аутентификация (2FA)

Способы: **WhatsApp** (одноразовый код) и **TOTP** (Google Authenticator и совместимые приложения).
Дополнительно выдаются 10 одноразовых **резервных кодов**.

Вход при включённой 2FA:

1. `POST /api/v1/auth/login` — пароль верный, но JWT **не выдаётся**. Ответ:
   ```json
   { "token": null, "twoFactorRequired": true, "tempToken": "...", "twoFactorMethod": "TOTP",
     "availableMethods": ["TOTP", "WHATSAPP"], "codeSent": null, "backupCodesAvailable": true }
   ```
   `tempToken` — JWT с пометкой `2fa_pending`, живёт 5 минут. `JwtAuthenticationFilter` отклоняет его (`401`)
   на всех эндпоинтах, кроме `/api/v1/auth/2fa/verify`.
2. `POST /api/v1/auth/2fa/verify` — `{ "tempToken", "code", "method": "WHATSAPP" | "TOTP" | "BACKUP_CODE" }`.
   Только после верного кода возвращается настоящий JWT.

| Метод | Путь | Доступ | Описание |
|---|---|---|---|
| POST | `/api/v1/auth/2fa/verify` | tempToken | Завершить вход кодом |
| POST | `/api/v1/auth/2fa/verify/send-code` | tempToken | Отправить (повторно) WhatsApp-код для входа |
| GET | `/api/v1/auth/2fa/status` | JWT | Включена ли 2FA, способ, обязательность, остаток резервных кодов |
| POST | `/api/v1/auth/2fa/setup` | JWT | Начать подключение: TOTP → секрет + `otpauth://` URI + QR; WhatsApp → проверочный код |
| POST | `/api/v1/auth/2fa/enable` | JWT | Подтвердить кодом и включить; возвращает резервные коды (один раз) |
| POST | `/api/v1/auth/2fa/send-code` | JWT | WhatsApp-код текущему пользователю (для отключения) |
| POST | `/api/v1/auth/2fa/disable` | JWT | Отключить: нужны пароль и код |
| POST | `/api/v1/auth/2fa/backup-codes` | JWT | Перевыпустить 10 резервных кодов (нужен пароль) |

Хранение: TOTP-секрет шифруется AES-256-GCM (ключ `TWO_FACTOR_ENCRYPTION_KEY`), резервные коды — только BCrypt-хэши.
После 5 неверных кодов подряд проверка блокируется на 5 минут.

**Обязательная 2FA для `ADMIN` и `VENUE_OWNER`** — флаг `TWO_FACTOR_REQUIRED_FOR_STAFF` (по умолчанию `true`).
Если такой пользователь сам 2FA не настраивал, при входе код приходит в WhatsApp; подключив TOTP, он входит по коду из приложения.

---

## Роли и права

| Роль | Возможности |
|---|---|
| `CLIENT` | Регистрация, просмотр меню/заведений, создание и просмотр своих заказов |
| `VENUE_OWNER` | Управление меню и продуктами своего заведения, обработка заказов (статус / отмена), просмотр кухонной очереди и истории |
| `ADMIN` | Регистрация владельцев заведений (`/api/v1/auth/venue-register`), полное управление заведениями |

---

## Rate Limiting (Bucket4j + Caffeine)

`RateLimitFilter` ограничивает `POST`-запросы на каждую пару «IP + эндпоинт»:

| Эндпоинты | Лимит |
|---|---|
| `/api/v1/auth/register`, `/login`, `/activate`, `/resend-code`, `/forgot-password`, `/reset-password` | 10 запросов / минута |
| `/api/v1/auth/2fa/**` | 10 запросов / минута |

Лимит настраивается (`RATE_LIMIT_CAPACITY`, `RATE_LIMIT_ENABLED`). При превышении: `HTTP 429 Too Many Requests` + `Retry-After`.
Перебор конкретного кода дополнительно ограничен самим OTP (5 попыток) и блокировкой 2FA.

---

## API Endpoints (краткий обзор)

| Метод | Путь | Роль | Описание |
|---|---|---|---|
| POST | `/api/v1/auth/register` | — | Регистрация клиента |
| POST | `/api/v1/auth/login` | — | Вход |
| POST | `/api/v1/auth/activate` | — | Подтверждение телефона |
| POST | `/api/v1/auth/2fa/verify` | — | Завершение входа кодом второго фактора |
| POST | `/api/v1/auth/2fa/setup` · `/enable` · `/disable` · `/backup-codes` | AUTH | Управление 2FA |
| GET | `/api/v1/auth/me` | AUTH | Профиль текущего пользователя |
| GET | `/api/v1/venues` | — | Список заведений |
| POST | `/api/v1/venues` | ADMIN | Создать заведение |
| GET | `/api/v1/venues/{id}/products` | — | Меню заведения |
| POST | `/api/v1/products` | VENUE_OWNER | Добавить продукт |
| POST | `/api/v1/orders` | AUTH | Создать заказ |
| GET | `/api/v1/orders/user` | AUTH | Мои заказы |
| GET | `/api/v1/orders/kitchen` | VENUE_OWNER | Активные заказы кухни |
| PATCH | `/api/v1/orders/{id}/status` | VENUE_OWNER | Обновить статус заказа |
| PATCH | `/api/v1/orders/{id}/cancel` | VENUE_OWNER | Отменить заказ |
| GET | `/api/v1/orders/{id}/kaspi-url` | AUTH | Ссылка для оплаты Kaspi |

Полная интерактивная документация: `GET /swagger-ui.html`

---

## Переменные окружения

| Переменная | Описание | Dev-дефолт |
|---|---|---|
| `JWT_KEY` | Секрет для подписи JWT (Base64, ≥ 32 байт). **В prod обязателен** | dev-ключ |
| `WHATSAPP_API_URL` | Хост Green API без пути (`apiUrl` из личного кабинета). **В prod обязателен** | `https://api.green-api.com` |
| `WHATSAPP_INSTANCE_ID` | ID инстанса Green API. **В prod обязателен** | — |
| `WHATSAPP_API_TOKEN` | Токен Green API. **В prod обязателен** | — |
| `TWO_FACTOR_ENCRYPTION_KEY` | Ключ AES-256 для TOTP-секретов: Base64 от 32 байт (`openssl rand -base64 32`). **В prod обязателен**; при смене ключа настроенные TOTP перестают работать | dev-ключ |
| `TWO_FACTOR_REQUIRED_FOR_STAFF` | Обязательная 2FA для `ADMIN` и `VENUE_OWNER` | `true` |
| `RATE_LIMIT_ENABLED` / `RATE_LIMIT_CAPACITY` | Лимит запросов к auth-эндпоинтам (запросов в минуту с IP) | `true` / `10` |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary cloud name | `temp` |
| `CLOUDINARY_API_KEY` | Cloudinary API key | `12345` |
| `CLOUDINARY_API_SECRET` | Cloudinary API secret | `secret` |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL (prod-профиль) | — |
| `PORT` | Порт сервера | `8080` |

Шаблон — в файле [`.env.example`](.env.example): скопируйте его в `.env`, он подхватывается автоматически и не попадает в git.

---

## Запуск

### Локально (dev-профиль)

```bash
cp .env.example .env   # и заполните значения
./mvnw spring-boot:run  # Windows: mvnw.cmd spring-boot:run
```

### Docker

```bash
docker build -t dium-backend .
docker run -p 8080:8080 \
  -e JWT_KEY=... \
  -e TWO_FACTOR_ENCRYPTION_KEY=... \
  -e WHATSAPP_API_URL=... \
  -e WHATSAPP_INSTANCE_ID=... \
  -e WHATSAPP_API_TOKEN=... \
  -e CLOUDINARY_CLOUD_NAME=... \
  -e CLOUDINARY_API_KEY=... \
  -e CLOUDINARY_API_SECRET=... \
  dium-backend
```

Prod-профиль активируется автоматически через `ENTRYPOINT` в Dockerfile.

### Тесты

```bash
./mvnw test
```

---

## Структура проекта

```
src/main/java/com/dium/demo/
├── aspects/          # AOP: WhatsApp-уведомления после создания заказа
├── config/           # Spring-конфигурация (Security, Cache, WhatsApp)
├── controllers/      # REST-контроллеры
├── dto/
│   ├── requests/     # Входящие DTO
│   └── responses/    # Исходящие DTO
├── enums/            # OrderStatus, UserRole, ProductCategory
├── exceptions/       # BusinessLogicException, AccessDeniedException, GlobalExceptionHandler
├── mappers/          # MapStruct-маппинг сущность ↔ DTO
├── models/           # JPA-сущности
├── repositories/     # Spring Data репозитории
├── security/         # JWT-фильтр, SecurityConfig, RateLimitFilter, шифрование TOTP-секретов
├── services/         # Бизнес-логика
├── utils/            # PhoneNormalizer
└── whatsapp/         # Клиент WhatsApp API
```

---

## Команда

| Участник | Роль | Зона ответственности |
|---|---|---|
| **Туругельдиев Алихан** | Co-founder & CTO | Архитектура системы, разработка и все технические интеграции платформы |
| **Умбетов Диас** | Co-founder & CEO | Продукт, стратегия, партнёрства и развитие бизнеса |
| **Мурат Мансур** | Разработчик, ответственный за безопасность (Developer, Security Lead) | Реализовал двухфакторную аутентификацию и отвечает за защиту входа и данных пользователей |
