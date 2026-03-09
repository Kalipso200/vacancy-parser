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
- Сбор метрик через Micrometer и Prometheus
- Профилирование с VisualVM и JMH бенчмарки
## Технологический стек

| Компонент | Технология | Назначение |
|-----------|------------|------------|
| **Язык** | Java 17 | Основной язык разработки |
| **Фреймворк** | Spring Boot 3.2.5 | Базовый фреймворк |
| **База данных** | H2 + JPA | Хранение данных |
| **Многопоточность** | ExecutorService, ForkJoinPool | Параллельная обработка |
| **Реактивное программирование** | Project Reactor, WebClient | Асинхронные HTTP запросы |
| **Метрики** | Micrometer, Prometheus | Сбор метрик |
| **Мониторинг** | Grafana | Визуализация метрик |
| **Профилирование** | VisualVM, JFR, JMH | Анализ производительности |
| **Документация** | SpringDoc OpenAPI (Swagger) | Документация API |
| **Сборка** | Maven | Управление зависимостями |
| **Логирование** | SLF4J + Logback | Логирование событий |
| **Тестирование** | JUnit, Mockito, Spring Test | Модульное тестирование |

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
Документация и метрики 
```
GET	/swagger-ui.html	Интерактивная документация Swagger
GET	/api-docs	OpenAPI JSON спецификация
GET	/actuator/prometheus	Метрики в формате Prometheus
GET	/actuator/metrics	Список всех метрик
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
При запуске приложения автоматически стартуют демон-потоки.

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


# Сбор метрик с Micrometer и Prometheus
## Настройка Micrometer
Зависимости в pom.xml

```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```
Конфигурация в application.properties
```
# Actuator endpoints
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoints.web.base-path=/actuator

# Prometheus
management.endpoint.prometheus.enabled=true
management.metrics.export.prometheus.enabled=true

# Метрики для HTTP запросов
management.metrics.web.server.request.autotime.enabled=true
management.metrics.web.server.request.percentiles=0.5,0.95,0.99

# Добавляем теги
management.metrics.tags.application=vacancy-parser
```
Кастомные метрики в MetricsService.java
```
@Service
public class MetricsService {
    private final Counter successfulParsingCounter;
    private final Counter failedParsingCounter;
    private final Timer parsingTimer;
    private final Counter databaseRecordsCounter;
    
    // Методы для обновления метрик
    public void incrementSuccessfulParsing(String source) { ... }
    public void incrementFailedParsing(String source, String errorType) { ... }
    public Timer.Sample startParsingTimer() { ... }
    public void stopParsingTimer(Timer.Sample sample, String source, boolean success) { ... }
    public void incrementDatabaseRecords(long count) { ... }
}
```
Метрики в Prometheus
После запуска приложения метрики доступны по адресу:
```
http://localhost:8080/actuator/prometheus
Основные метрики:
Метрика	Описание
vacancy_parsing_success_total	Количество успешных парсингов
vacancy_parsing_failed_total	Количество ошибочных парсингов
vacancy_parsing_duration_seconds	Гистограмма времени парсинга
vacancy_database_records_total	Количество записей в БД
vacancy_parsing_active	Количество активных задач
```
Настройка Prometheus

```
yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'vacancy-parser'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
        labels:
          application: 'vacancy-parser'
```







