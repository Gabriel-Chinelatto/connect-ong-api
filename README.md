# ⚙️ Connect ONG - API

Backend robusto desenvolvido em **Spring Boot** para gerenciar o ecossistema da plataforma Connect ONG. Esta API fornece todos os endpoints necessários para autenticação, gestão de usuários, ONGs e doações.

## 🛠️ Tecnologias Utilizadas

- **Java 17**
- **Spring Boot 3.5.6**
- **Spring Data JPA** (Persistência)
- **Spring Security** (Criptografia de senhas com BCrypt)
- **MySQL** (Banco de dados relacional)
- **Lombok** (Produtividade e redução de boilerplate)
- **Maven** (Gestão de dependências)

## 🏗️ Arquitetura

A API segue o padrão MVC (Model-View-Controller), estruturada da seguinte forma:

- `controller/`: Endpoints REST e validações de entrada.
- `model/`: Entidades JPA que representam as tabelas do banco.
- `repository/`: Interfaces de abstração para acesso aos dados via Spring Data.
- `config/`: Configurações de segurança e beans do sistema.

## 🚀 Como Executar

### Pré-requisitos
- JDK 17+
- MySQL Server rodando

### Passos
1. Configure as credenciais do seu banco de dados em `src/main/resources/application.properties`.
2. Compile o projeto:
   ```bash
   ./mvnw clean install
   ```
3. Execute a aplicação:
   ```bash
   ./mvnw spring-boot:run
   ```

## 📡 Endpoints Principais

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/usuarios` | Cadastro de novo usuário |
| POST | `/usuarios/login` | Autenticação de usuário |
| GET | `/ongs` | Listagem de todas as ONGs |
| POST | `/ongs` | Cadastro de nova ONG |
| DELETE | `/ongs/{id}` | Remoção de uma ONG |

## 🛡️ Segurança
Senhas são armazenadas utilizando o algoritmo de hashing **BCrypt**, garantindo que dados sensíveis não fiquem vulneráveis em caso de acesso indevido ao banco de dados.
