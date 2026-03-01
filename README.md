# Vacancy Parser API

Многопоточное веб-приложение для парсинга вакансий с сайта hh.ru с использованием Spring Boot, JPA и реактивного программирования.


## Описание проекта

**Vacancy Parser API** — это полнофункциональное многопоточное приложение для сбора и анализа вакансий с сайта hh.ru. Сервис демонстрирует современные практики конкурентного программирования в контексте реальной задачи обработки данных.

### Основные возможности:
- Асинхронный парсинг вакансий по URL
- Многопоточная обработка с использованием ExecutorService и ForkJoinPool
- Планировщик задач для регулярного парсинга
- Постоянное хранение данных в H2 с JPA
- REST API с пагинацией, сортировкой и поиском
- Реактивный (потоковый) парсинг
- Мониторинг через демон-потоки
- Интерактивная документация Swagger UI
## Технологический стек

| Компонент | Технология | Назначение |
|-----------|------------|------------|
| **Язык** | Java 17 | Основной язык разработки |
| **Фреймворк** | Spring Boot 4.0.3 | Базовый фреймворк |
| **База данных** | H2 + JPA | Хранение данных |
| **Многопоточность** | ExecutorService, ForkJoinPool | Параллельная обработка |
| **Реактивное программирование** | Project Reactor, WebClient | Асинхронные HTTP запросы |
| **Документация** | SpringDoc OpenAPI (Swagger) | Документация API |
| **Сборка** | Maven | Управление зависимостями |
| **Логирование** | SLF4J + Logback | Логирование событий |

##  Требования

- **Java 17** или выше
- **Maven 3.6+** (или использование встроенного ./mvnw)
- **Подключение к интернету** (для доступа к API hh.ru)
- **Порт 8080** (должен быть свободен)

##  Быстрый старт

### 1. Клонирование и сборка

```bash
# Клонируйте репозиторий (или распакуйте архив)
cd vacancy-parser

# Соберите проект

mvn clean package

# запуск 
mvn spring-boot:run

```

3. Проверка работоспособности

# Проверьте health endpoint

```
curl http://localhost:8080/health

```

Ожидаемый ответ:

```
{"status":"UP","timestamp":"1740838532000"}

```
## Ключевые компоненты
### Многопоточность
ExecutorService: кастомный ThreadPoolTaskExecutor с 4-10 потоками

ForkJoinPool: для параллельной обработки данных

CompletableFuture: асинхронные операции с таймаутами

ConcurrentHashMap: потокобезопасное хранение задач

### Хранение данных
JPA + H2: in-memory база данных с автоматическим созданием схемы

VacancyJpaRepository: Spring Data JPA репозиторий

Индексы: оптимизированный поиск по компании, городу, датам

### Планировщик
@Scheduled(cron): парсинг каждый час

@Scheduled(fixedDelay): проверка статуса каждые 5 минут

@Scheduled(cron): очистка в 3 часа ночи

### Демон-потоки
stats-daemon: вывод статистики каждые 10 секунд

monitor-daemon: мониторинг задач каждые 5 секунд

log-processor-daemon: асинхронная обработка логов

scheduled-daemons: сбор метрик и очистка

## Детальное описание API
Базовые эндпоинты
```
GET	/	Информация о приложении	
GET	/health	Проверка работоспособности
```
Работа с вакансиями
```
GET	/api/vacancies - Все вакансии (пагинация)
GET	/api/vacancies/{id}	Вакансия по ID
GET	/api/vacancies/external/{externalId}	Вакансия по внешнему ID
GET	/api/vacancies/stats	Статистика
DELETE	/api/vacancies/clear	Очистить все вакансии
```
Поиск вакансий
```
GET	/api/vacancies/search	Поиск по критериям	company, city, title, source, from, to
GET	/api/vacancies/search/advanced	Расширенный поиск	company, city, title
GET	/api/vacancies/search/skill/{skill}	Поиск по ключевому навыку
```
Парсинг вакансий
```
POST	/api/vacancies/parse	Парсинг одной вакансии	{"url": "https://api.hh.ru/vacancy/130807895"}
POST	/api/vacancies/parse-multiple	Парсинг нескольких	["url1", "url2", "url3"]
POST	/api/vacancies/parse-reactive	Реактивный парсинг	["url1", "url2", "url3"]
```
Управление задачами
```
GET	/api/vacancies/tasks	Все активные задачи
GET	/api/vacancies/tasks/{taskId}	Статус задачи
DELETE	/api/vacancies/tasks/{taskId}	Удалить задачу
```
Документация
```
GET	/swagger-ui.html	Интерактивная документация Swagger
GET	/api-docs	OpenAPI JSON спецификация
```
## Демонстрация многопоточности
1. Последовательность действий для проверки
   Шаг 1: Парсинг одной вакансии

```
curl -X POST http://localhost:8080/api/vacancies/parse \
  -H "Content-Type: application/json" \
  -d '{"url": "https://api.hh.ru/vacancy/130807895"}'
```
Ответ:

```
{
  "taskId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "url": "https://api.hh.ru/vacancy/130807895",
  "source": "hh.ru",
  "startTime": "2026-03-01T10:15:30.123",
  "status": "PENDING",
  "parsedCount": 0
}
```
Шаг 2: Проверка статуса задачи

```
curl http://localhost:8080/api/vacancies/tasks/a1b2c3d4-e5f6-7890-1234-567890abcdef
```
Ответ (в процессе):

```
{
  "taskId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "status": "RUNNING",
  "parsedCount": 0
}
```
Ответ (завершено):

```
{
  "taskId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "status": "COMPLETED",
  "parsedCount": 1
}
```
Шаг 3: Просмотр результатов

```
curl "http://localhost:8080/api/vacancies?page=0&size=10"
```
2. Демонстрация параллельного парсинга

Парсинг 3 вакансий одновременно
```
curl -X POST http://localhost:8080/api/vacancies/parse-multiple \
  -H "Content-Type: application/json" \
  -d '[
    "https://hh.ru/vacancy/130504644",
    "https://hh.ru/vacancy/130504645",
    "https://hh.ru/vacancy/130504646"
  ]'
 ```
Ответ: массив из 3 задач со статусами PENDING

3. Демонстрация реактивного (потокового) парсинга
```
curl -X POST http://localhost:8080/api/vacancies/parse-reactive \
  -H "Content-Type: application/json" \
  -d '[
    "https://api.hh.ru/vacancy/130807895",
    "https://api.hh.ru/vacancy/130807896"
  ]'
  ```
Ответ приходит по частям:
```
{"id":130504644,"title":"Кладовщик","company":"Компания","status":"fresh"}
{"id":130504645,"title":"Java Developer","company":"Яндекс","status":"fresh"}
```
## Мониторинг и логирование
Демон-потоки в действии
При запуске приложения автоматически стартуют демон-потоки:

Демон-поток обработки логов запущен
Запуск демон-потоков для мониторинга и логирования
Демон-поток статистики запущен: stats-daemon
Демон-поток мониторинга запущен: monitor-daemon
Примеры логов
```
[10:15:30]  Статистика: вакансий: 5, активных задач: 0, всего спарсено: 10
[10:15:35]  Мониторинг задач: всего: 2, выполняется: 1, завершено: 1, ошибок: 0
[10:15:40]  Память - используемая: 156 MB (15.2%), свободная: 845 MB, максимум: 1024 MB
[10:15:42]  [HTTP] GET /api/vacancies - 200 (45 ms)
[10:15:45]  Задача a1b2c3d4: успешно спарсена вакансия 'Кладовщик' (время: 1234 мс)
```
Сценарии использования
Сценарий 1: Анализ рынка Java-разработчиков
```
# 1. Найти и спарсить Java вакансии
curl -X POST http://localhost:8080/api/vacancies/parse-multiple \
-H "Content-Type: application/json" \
-d '[
"https://api.hh.ru/vacancy/java1",
"https://api.hh.ru/vacancy/java2",
"https://api.hh.ru/vacancy/java3"
]'

# 2. Подождать завершения (30 секунд)
# 3. Найти все Java вакансии в Москве

curl "http://localhost:8080/api/vacancies/search?title=Java&city=Москва"
# 4. Посмотреть статистику по зарплатам (через API статистики)

curl http://localhost:8080/api/vacancies/stats
```
Сценарий 2: Мониторинг новых вакансий
```
# Проверка вакансий за последний час
$from = (Get-Date).AddHours(-1).ToString("yyyy-MM-ddTHH:mm:ss")
$to = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")

curl "http://localhost:8080/api/vacancies/search?from=$from&to=$to"
```
Сценарий 3: Сравнение компаний
# Статистика по компаниям
```
curl http://localhost:8080/api/vacancies/stats
```
# Поиск вакансий конкретной компании
```
curl "http://localhost:8080/api/vacancies/search?company=Яндекс"
```
## Диагностика и отладка
Проверка состояния приложения
```
# 1. Health check
curl http://localhost:8080/health

# 2. Статистика
curl http://localhost:8080/api/vacancies/stats

# 3. Активные задачи
curl http://localhost:8080/api/vacancies/tasks
```
## Возможные проблемы и решения:
Connection refused - Приложение не запущено- Запустите  mvn clean package

404 Not Found - Неверный URL - Проверьте эндпоинт в документации Swagger

Task status FAILED - Проблема с hh.ru API - Проверьте интернет-соединение

H2 консоль не работает - Используйте API для просмотра данных





