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

# ===== Memoria (o plano gratuito do Render da 512 MB no total) =====
# Sem estes limites o container estourava e o Render matava a aplicacao com
# "Ran out of memory (used over 512MB) while running your code" — foi o que
# derrubou a API em 04/08/2026 e fez o deploy de 05/08 falhar.
#
# O problema: "java -jar" sem parametros deixa a JVM decidir sozinha, e a soma
# (heap + metaspace + cache de codigo + pilha de cada thread) passa dos 512 MB.
#
#   MaxRAMPercentage=50  heap no maximo ~256 MB, sobrando espaco para o resto
#                        (o padrao da JVM so considera o heap e ignora o resto)
#   MaxMetaspaceSize     teto para as classes carregadas (Spring carrega MUITAS)
#   UseSerialGC          coletor de lixo simples; o G1 (padrao) mantem estruturas
#                        proprias que nao se pagam num heap pequeno
#   Xss512k              pilha por thread: o Tomcat cria dezenas delas
#   TieredStopAtLevel=1  compilacao JIT so no nivel 1 => menos cache de codigo
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=50", \
    "-XX:MaxMetaspaceSize=128m", \
    "-XX:+UseSerialGC", \
    "-Xss512k", \
    "-XX:TieredStopAtLevel=1", \
    "-jar", "app.jar"]
