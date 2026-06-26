# ===== Build stage =====
# Compila o JAR dentro do proprio container (nao precisa de Maven na maquina).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache de dependencias: copia so o pom primeiro
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copia o codigo e empacota (sem rodar testes na imagem; isso fica na CI)
COPY src ./src
RUN mvn -B clean package -DskipTests

# ===== Runtime stage =====
# Imagem final enxuta, so com o JRE para rodar o JAR.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/connectong-api-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# As credenciais do banco vem por variavel de ambiente (NUNCA na imagem):
#   DB_URL, DB_USERNAME, DB_PASSWORD
ENTRYPOINT ["java", "-jar", "app.jar"]
