# Imagen de producción del backend · US-070
#
# Multi-stage: la primera etapa compila, la segunda sólo ejecuta. La imagen final no lleva
# Maven, ni el código fuente, ni las dependencias de test: sólo un JRE 21 y el JAR.
#
# Decisiones que no son obvias:
#
#   - La etapa de build usa `maven:3.10.0-eclipse-temurin-21`, no `eclipse-temurin:21-jdk`
#     con el wrapper. Es la misma imagen que ya usa `infra/local/docker-compose.yml` y fija
#     Maven 3.10.0, que es la versión de ADR-0010. El wrapper dentro de este contenedor
#     necesitaría `unzip` para validar el SHA-256; si falta, cambia la URL a .tar.gz y la
#     validación falla (está documentado en `ci.yml`). Con la imagen de Maven no hay trampa.
#
#   - Las pruebas NO se ejecutan aquí. Las corre CI con `./mvnw verify` (incluidas las de
#     integración con Testcontainers, que no pueden correr dentro de un `docker build`).
#     Repetirlas aquí sólo alargaría el build y daría una señal duplicada.
#
#   - `target/` está en `.dockerignore`. Por eso el build es multi-stage y compila dentro:
#     copiar un JAR del host requeriría sacarlo del ignore y atar la imagen al `target/` de
#     la máquina que construye.
#
#   - El contenedor corre como usuario sin privilegios. El proceso no necesita escribir en
#     el sistema de archivos, así que no hay motivo para que sea root.
#
#   - No se fija `SPRING_PROFILES_ACTIVE`. El perfil `local` enciende Swagger y el detalle
#     del health, y US-005 prohíbe publicarlos fuera de local. La configuración de
#     producción llega por variables de entorno (12-factor):
#         SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD,
#         JWT_SECRET
#
# Construir:
#     docker build -t pizzeria-backend:local backend/
#
# Ejecutar (ejemplo contra el PostgreSQL del laboratorio, publicado en 5435):
#     docker run --rm -p 8080:8080 \
#       -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5435/pizzeria \
#       -e SPRING_DATASOURCE_USERNAME=pizzeria_dev \
#       -e SPRING_DATASOURCE_PASSWORD=pizzeria_dev \
#       -e JWT_SECRET=cambia-esto-por-un-secreto-de-32-caracteres \
#       pizzeria-backend:local

# ============================================================================
# Etapa 1 · build
# ============================================================================
FROM maven:3.10.0-eclipse-temurin-21 AS build

WORKDIR /build

# Primero sólo el POM: mientras no cambie, Docker reutiliza la capa con las dependencias.
COPY pom.xml .
RUN mvn -B -ntp -DskipTests dependency:go-offline

# Después el código. Un cambio en `src/` no invalida la capa anterior.
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ============================================================================
# Etapa 2 · runtime
# ============================================================================
FROM eclipse-temurin:21-jre

# Usuario del sistema, sin shell y sin home propio más allá de /app.
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

# El JAR conserva el nombre fijo que declara el pom (`<finalName>pizzeria-backend</finalName>`).
COPY --from=build --chown=app:app /build/target/pizzeria-backend.jar /app/app.jar

# Límites de memoria relativos al contenedor (no a la máquina) y salida inmediata si la JVM
# se queda sin memoria: un proceso zombi que no responde es peor que uno que reinicia.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

USER app

EXPOSE 8080

# La imagen de Temurin no trae curl ni wget. Bash sí, y /dev/tcp alcanza para preguntar por
# el health real: no basta con que el puerto escuche, la base de datos también tiene que estar.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q "\"status\":\"UP\"" <&3'

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
