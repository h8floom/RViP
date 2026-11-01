# Полный проект лабораторной работы №4 — MinIO + Eureka + ELK

Проект собран по коду Lab 3, который пользователь присылал в чате: car-center (JPA/CRUD), report-service (JdbcTemplate), Gateway, PostgreSQL + Liquibase, ELK и Micrometer Tracing. Добавлены Eureka Service Discovery и файловый сервис, сохраняющий CSV в MinIO.

## Архитектура

- Postman -> Gateway (`localhost:8088`).
- Gateway маршрутизирует `/api/cars` и `/reports/**` только в `car-center` через `lb://car-center`.
- `car-center` и `report-service` автоматически регистрируются в Eureka.
- `car-center` использует `@LoadBalanced RestTemplate` и service ID `http://report-service`, чтобы получить список автомобилей.
- `car-center` формирует CSV UTF-8 и вторым вызовом `RestTemplate` отправляет его на `http://file-storage-service/api/files`.
- `file-storage-service` регистрируется в Eureka, принимает байты CSV и сохраняет объект в MinIO bucket `car-reports` через MinIO Java SDK.
- CSV-файлы сохраняются в Docker volume `minio_data`.
- Java-сервисы отправляют JSON-логи в Logstash; Logstash записывает их в Elasticsearch; Kibana позволяет фильтровать логи по общему `traceId`.

## Требования

- Docker Desktop с Docker Compose.
- JDK 22 для локальной Gradle-сборки.
- Доступ в интернет при первой сборке Gradle и при первом скачивании Docker images.

Версии в проекте сохранены по существующему Lab 3: Spring Boot 4.1.1, Spring Cloud 2025.1.3, Gradle 9.7.1, Java 22. Официальная матрица Spring Cloud указывает, что 2025.1.x совместима с Boot 4.1.x начиная с 2025.1.2.

## MinIO Community Edition

MinIO Community Edition теперь распространяется как source-only; официальный репозиторий указывает, что исторические бинарники больше не обслуживаются. Для учебного Compose используется зафиксированный multi-arch образ `ghcr.io/golithus/minio:RELEASE.2025-10-15T17-29-55Z`, собранный сторонним проектом из upstream-релиза. Это не официальный поддерживаемый MinIO image и не production-рекомендация. Для лабораторного стенда используйте только локально.

## Установка / запуск

1. Распакуйте папку проекта. Создайте `.env` на основе `env.lab4.example`:

   ```bash
   cp env.lab4.example .env
   ```

2. **Если используете старый PostgreSQL volume из Lab 1-3:** откройте существующий `.env` и оставьте прежние `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`. Не меняйте пароль наугад: переменная `POSTGRES_PASSWORD` применяется только при первичной инициализации каталога данных PostgreSQL.

3. Проверьте Java:

   ```bash
   java -version
   ```

   Требуется JDK 22. Для запуска `./gradlew` на macOS/Linux используйте:

   ```bash
   chmod +x gradlew
   ```

4. Соберите все пять Java-приложений отдельно (у каждого вложенного сервиса собственный Gradle build):

   ```bash
   ./gradlew clean bootJar
   (cd report-service && ../gradlew clean bootJar)
   (cd gateway-service && ../gradlew clean bootJar)
   (cd discovery-server && ../gradlew clean bootJar)
   (cd file-storage-service && ../gradlew clean bootJar)
   ```

   Все пять команд должны завершиться `BUILD SUCCESSFUL`.

5. Проверьте Compose и соберите образы Java-сервисов:

   ```bash
   docker compose config --quiet
   docker compose build --no-cache app report-service gateway discovery-server file-storage-service
   ```

6. Запустите стенд с двумя экземплярами основного и отчётного сервисов:

   ```bash
   docker compose up -d --scale app=2 --scale report-service=2
   ```

7. Посмотрите состояние:

   ```bash
   docker compose ps
   ```

   Подождите 20-40 секунд, пока Java-сервисы зарегистрируются в Eureka. Откройте Eureka Dashboard и убедитесь, что видны `CAR-CENTER` (2 экземпляра), `REPORT-SERVICE` (2), `GATEWAY-SERVICE` (1), `FILE-STORAGE-SERVICE` (1).

## Интерфейсы

- Gateway API: http://localhost:8088
- Eureka Dashboard: http://localhost:8761
- MinIO Console: http://localhost:9001
- MinIO S3 API: http://localhost:9000
- Elasticsearch: http://localhost:9200
- Kibana: http://localhost:5601

Вход MinIO берёт значения из `.env`: `MINIO_ROOT_USER` и `MINIO_ROOT_PASSWORD`. Bucket `car-reports` создаётся файловым сервисом автоматически.

## Проверка Lab 3

Список автомобилей через основной сервис:

```bash
curl -i http://localhost:8088/api/cars
```

JSON-список для отчёта: Gateway -> car-center -> RestTemplate -> report-service -> PostgreSQL:

```bash
curl -i http://localhost:8088/reports/cars
```

Внешний порт `8081` report-service не опубликован, и Gateway не имеет маршрута непосредственно к `report-service`.

## Проверка Lab 4

1. Создать CSV и сохранить его через MinIO:

   ```bash
   curl -i -X POST http://localhost:8088/reports/cars/export
   ```

   Тело запроса не требуется. Успешный ответ содержит `filename`, `bucket`, `sizeBytes`, `status=SAVED`, `savedAt`.

2. Получить список объектов:

   ```bash
   curl -s http://localhost:8088/reports/files
   ```

3. Скачать объект (замените имя на то, что вернулось в ответе):

   ```bash
   curl -o cars-report.csv http://localhost:8088/reports/files/cars-report-....csv
   ```

4. Откройте MinIO Console, выберите bucket `car-reports` и убедитесь, что объект CSV там присутствует.

5. Посмотрите логи цепочки:

   ```bash
   docker compose logs -f gateway app report-service file-storage-service
   ```

6. В Kibana Discover используйте Data View `car-center-logs-*`, поле времени `@timestamp`. Выполните экспорт и найдите записи по одному `traceId`; цепочка должна включать Gateway, car-center, report-service и file-storage-service.

## Полезные диагностические команды

```bash
# Все контейнеры
docker compose ps

# Логи отдельных сервисов
docker compose logs --tail=100 discovery-server
docker compose logs --tail=100 file-storage-service
docker compose logs --tail=100 minio

# Проверка Elasticsearch индексов
curl -s "http://localhost:9200/_cat/indices?v"

# Eureka application registry (JSON)
curl -s http://localhost:8761/eureka/apps
```

## Важное про сохранность данных

Не запускайте `docker compose down -v`, если хотите сохранить PostgreSQL данные и CSV-объекты MinIO. Именованные тома `postgres_data`, `elasticsearch_data` и `minio_data` хранят данные независимо от жизненного цикла контейнеров.

## Различие ролей

- `RestTemplate` №1: `car-center` -> `report-service`, получает JSON со списком автомобилей.
- CSV генератор: в `car-center` преобразует список в CSV UTF-8 с корректным экранированием.
- `RestTemplate` №2: `car-center` -> `file-storage-service`, отправляет CSV как raw bytes в HTTP POST.
- MinIO SDK: используется внутри file-storage-service для загрузки объекта в S3 bucket.
- Eureka: регистрирует экземпляры и помогает `lb://car-center`, `http://report-service`, `http://file-storage-service` находить живые экземпляры.
- ELK: Logstash принимает логи, Elasticsearch хранит, Kibana помогает искать; `traceId` коррелирует логи одного запроса.

## Ограничения учебной реализации

- MinIO console/S3 API и Elasticsearch/Kibana открыты только на localhost; встроенная авторизация Elasticsearch отключена для локальной демонстрации.
- File Storage принимает CSV до 10 MiB, имена должны соответствовать шаблону `*.csv`.
- Eureka и LoadBalancer обеспечивают динамическое обнаружение и клиентскую балансировку, но это учебная конфигурация без auth/TLS, production health policy и circuit breaker.
- Gateway автоматически регистрируется в Eureka. Внешние HTTP-маршруты явно направлены только на `car-center`; сервисы `report-service` и `file-storage-service` остаются внутренними.

## Дополнение: Saga для экспорта CSV (ЛР4)

В `car-center` процесс `POST /reports/cars/export` управляется оркестратором `ReportExportSagaService`.
Состояние каждой операции сохраняется в PostgreSQL в таблице `report_exports` через JPA; таблица создаётся Liquibase changeSet `003-create-report-exports-table`.

### Шаги процесса

1. Создать запись `PENDING` с UUID операции и заранее сгенерированным уникальным именем файла.
2. Получить список автомобилей от `report-service` (`REPORT_RECEIVED`).
3. Сформировать CSV (`UPLOADING`).
4. Передать CSV в `file-storage-service` и сохранить объект в MinIO bucket `car-reports`.
5. Зафиксировать `COMPLETED` и вернуть `exportId` вместе с данными сохранённого файла.

### Компенсация

Если ошибка произошла до обращения к файловому сервису, состояние становится `FAILED`.
Если POST-загрузка могла достичь файлового сервиса (в том числе при тайм-ауте, когда ответ неизвестен), оркестратор вызывает `DELETE /api/files/{filename}`. При успехе операция получает `COMPENSATED`; если удалить объект не удалось — `COMPENSATION_REQUIRED`.

Повторить компенсацию можно запросом:

```bash
curl -X POST http://localhost:8088/reports/exports/EXPORT_UUID/compensate
```

Проверить состояние:

```bash
curl http://localhost:8088/reports/exports/EXPORT_UUID
```

Saga не является общей ACID-транзакцией между PostgreSQL и MinIO. Каждая запись статуса — отдельная короткая транзакция PostgreSQL; удаление объекта — компенсирующая операция. Если PostgreSQL полностью недоступен, сохранить итоговый статус невозможно; это ограничение распределённой схемы.

### Сборка

Собирать нужно каждый независимый Gradle-проект из его каталога:

```bash
./gradlew clean bootJar
(cd report-service && ../gradlew clean bootJar)
(cd gateway-service && ../gradlew clean bootJar)
(cd discovery-server && ../gradlew clean bootJar)
(cd file-storage-service && ../gradlew clean bootJar)
```

Для Docker:

```bash
docker compose config --quiet
docker compose up -d --build --scale app=2 --scale report-service=2
```

Не используйте `docker compose down -v`, если нужно сохранить данные PostgreSQL и MinIO.

