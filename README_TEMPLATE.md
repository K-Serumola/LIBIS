# Web App Template: Servlet + JSP + JDBC + MySQL + Tomcat FORM Security

A project-neutral starter. Every project-specific name is a **`{{TOKEN}}`**. Fill the tokens in (section 0), search-and-replace, then write the parts marked **REFACTOR**.

Stack: Maven, Tomcat 10.1/11 (Jakarta), JSP + JSTL/EL (no scriptlets), JDBC DAOs, MySQL, DataSourceRealm FORM login, JSTL `fmt` i18n.

## Contents

0. [Token dictionary (fill this in first)](#0-token-dictionary-fill-this-in-first)
1. [What changes vs what stays](#1-what-changes-vs-what-stays)
2. [Build order](#2-build-order)
3. [Project structure + pom.xml](#3-project-structure--pomxml)
4. [Tomcat setup + scripts](#4-tomcat-setup--scripts)
5. [Database](#5-database)
6. [context.xml (DataSource, Realm, Valve)](#6-contextxml-datasource-realm-valve)
7. [Config: JNDI helper](#7-config-jndi-helper)
8. [Models](#8-models)
9. [Exceptions](#9-exceptions)
10. [DAOs](#10-daos)
11. [Services](#11-services)
12. [Controllers](#12-controllers)
13. [Views (JSP + JSTL)](#13-views-jsp--jstl)
14. [Auth](#14-auth)
15. [Filters](#15-filters)
16. [i18n](#16-i18n)
17. [Rules checklist + evidence](#17-rules-checklist--evidence)
18. [Troubleshooting](#18-troubleshooting)
19. [Refactor playbook](#19-refactor-playbook)

---

## 0. Token dictionary (fill this in first)

Decide these before copying any code. The "Example" column uses a made-up clinic app so you can see the pattern.

| Token | Meaning | Example |
|---|---|---|
| `{{PKG}}` | Root Java package | `za.co.clinic` |
| `{{APP}}` | App/WAR/context name | `clinic` |
| `{{DB}}` | MySQL schema name | `clinicdb` |
| `{{JNDI}}` | DataSource JNDI name | `jdbc/clinicdb` |
| `{{User}}` / `{{user}}` / `{{users}}` | **Users** (people who log in): class / variable / table | `Patient` / `patient` / `patients` |
| `{{Entity}}` / `{{entity}}` / `{{entities}}` | **Main entity** managed by the admin role: class / variable+column prefix / table | `Medicine` / `medicine` / `medicines` |
| `{{Link}}` / `{{link}}` / `{{links}}` | **Link table** between a user and an entity with a start and end date | `Prescription` / `prescription` / `prescriptions` |
| `{{ROLE_USER}}` | Normal role name (exact text stored in DB) | `patient` |
| `{{ROLE_ADMIN}}` | Privileged role name | `pharmacist` |
| `{{HOME}}` | Page both roles can open after login | `/home` |
| `{{ADMIN}}` | URL prefix for the privileged role | `/staff` |
| `{{MAXN}}` | Business limit (max active links per user) | `3` |
| `{{LANG2}}` | Second language code for the bundle | `tn`, `zu`, `fr` ... |

**Replace plurals before singulars**, and use match-case replace (IntelliJ Ctrl+Shift+R, tick Match case + Preview): `{{entities}}` first, then `{{entity}}`, otherwise you get `medicinees`.

After replacing, search the project for `{{` and `REFACTOR`. Anything left is unfinished.

Pattern used in code: a **property** is camelCase (`{{entity}}Id`), a **column** is snake_case (`{{entity}}_id`). If your token contains an underscore or capital, adjust the column/property pair by hand.

---

## 1. What changes vs what stays

| Part | Rename only | Rewrite (**REFACTOR**) | Never touch |
|---|---|---|---|
| Package, artifact, app name | yes | | |
| Schema, table names | yes | columns | |
| `context.xml` | values | | structure |
| `Database.java` | JNDI string | | the rest |
| Models | | fields | pattern |
| DAOs | | SQL, `bind`, `map` | `query`, `fail`, try-with-resources shape |
| Services | | validation, rules | structure, constructor injection |
| Controllers | URLs, param names | | GET shows / POST changes + redirect |
| JSPs | labels | field names | `c:out`, flash, error display |
| `web.xml` | roles, URLs | | skeleton |
| Auth/logout/login pages | titles | | everything else |
| Filters | URL list | | |
| Exceptions | | | all |
| Deploy scripts | `APP` | | |

---

## 2. Build order

1. Fill section 0, copy skeleton, `pom.xml`, Tomcat copy + connector jar in `lib`
2. SQL script, run it, `SELECT` to check
3. `context.xml` + `Database.java`
4. Models, exceptions
5. DAOs
6. Services
7. `web.xml` + login pages + logout: **test login with a seeded user before building features**
8. Controllers + JSPs one entity at a time
9. No-cache filter
10. i18n
11. Screenshots as you go

Flow: `Browser -> Controller -> Service -> DAO -> MySQL`, then `Controller -> forward -> JSP`.
Controller = read input + choose view. Service = rules. DAO = SQL only. JSP = display only.

---

## 3. Project structure + pom.xml

```
src/main
├── java/{{PKG}}
│   ├── config/       Database
│   ├── controller/   *Controller
│   ├── dao/          *Dao, *DaoImpl
│   ├── exception/    DataAccessException, ValidationException, RuleException
│   ├── filter/       NoCacheFilter
│   ├── model/        {{User}}, {{Entity}}, {{Link}}
│   └── service/      {{User}}Service, {{Entity}}Service, {{Link}}Service
├── resources/        messages.properties, messages_{{LANG2}}.properties
└── webapp
    ├── META-INF/context.xml
    ├── WEB-INF/
    │   ├── web.xml
    │   ├── error.jsp
    │   ├── jspf/i18n.jspf
    │   └── (every other JSP, including admin/ subfolder)
    ├── login.jsp            <- only these two outside WEB-INF
    └── login-error.jsp
```

**pom.xml** (Tomcat 10.1 = servlet-api `6.0.0`; Tomcat 11 = `6.1.0`; Java 17+):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>{{PKG}}</groupId>
    <artifactId>{{APP}}</artifactId>            <!-- REFACTOR -->
    <version>1.0-SNAPSHOT</version>
    <packaging>war</packaging>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.release>17</maven.compiler.release>
        <lombok.version>1.18.42</lombok.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <version>6.1.0</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>jakarta.servlet.jsp.jstl</groupId>
            <artifactId>jakarta.servlet.jsp.jstl-api</artifactId>
            <version>3.0.2</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.web</groupId>
            <artifactId>jakarta.servlet.jsp.jstl</artifactId>
            <version>3.0.1</version>
        </dependency>
        <!-- also copy this jar into TOMCAT/lib: the Resource and the Realm load it from there -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>9.7.0</version>
            <scope>provided</scope>
        </dependency>
        <!-- optional: delete this + annotationProcessorPaths below if you don't use Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>{{APP}}</finalName>              <!-- REFACTOR -->
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-war-plugin</artifactId>
                <version>3.4.0</version>
                <configuration>
                    <failOnMissingWebXml>false</failOnMissingWebXml>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

Notes:
- Delete IDE-template leftovers: `Hello*` classes, `index.jsp`, `beans.xml`, Jersey/Weld dependencies.
- `maven.compiler.source/target` of 8 breaks the Servlet 6 API; use `release` 17+.
- IntelliJ: Settings -> Compiler -> Annotation Processors -> **Enable annotation processing** (Lombok).
- Check Maven Central for newer versions of the dependencies if you can.

---

## 4. Tomcat setup + scripts

**Why not IntelliJ's Run button:** it deploys with its own descriptor under a private `CATALINA_BASE`, so Tomcat **ignores `META-INF/context.xml`**, and it regenerates its `conf` on every run. Your Realm never loads, Tomcat falls back to `tomcat-users.xml`, and every login fails. A WAR dropped into `webapps` uses `META-INF/context.xml` correctly.

**One time (PowerShell):**

```powershell
New-Item -ItemType Directory -Force C:\dev | Out-Null
Copy-Item "C:\Program Files\Apache Software Foundation\Tomcat 11.0" C:\dev\tomcat -Recurse
Copy-Item "$env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j\9.7.0\mysql-connector-j-9.7.0.jar" C:\dev\tomcat\lib
```

On a shared/school PC, put the jar into whichever Tomcat `lib` the lab uses (it may already be there).

**`start-tomcat.bat`** (project root; REFACTOR the JDK path):

```bat
@echo off
set JAVA_HOME=C:\path\to\your\jdk
set CATALINA_HOME=C:\dev\tomcat

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JAVA_HOME is wrong: %JAVA_HOME%
    pause
    exit /b 1
)
if not exist "%CATALINA_HOME%\bin\catalina.bat" (
    echo Tomcat not found at %CATALINA_HOME%
    pause
    exit /b 1
)

call "%CATALINA_HOME%\bin\catalina.bat" run
pause
```

**`deploy.bat`**:

```bat
@echo off
set APP={{APP}}
set TOMCAT=C:\dev\tomcat

call mvn clean package -q
if errorlevel 1 (
    echo BUILD FAILED
    exit /b 1
)

for %%f in (target\*.war) do copy /Y "%%f" "%TOMCAT%\webapps\%APP%.war"
echo Deployed as /%APP%
```

**Loop:** `.\start-tomcat.bat` once (leave it open, it is your log) -> `.\deploy.bat` after each change -> `http://localhost:8080/{{APP}}/...`

Good log: `Deploying web application archive [...{{APP}}.war]` then `Deployment ... has finished`, no `SEVERE`.

Gotchas:
- In PowerShell scripts need `.\name.bat`.
- `zip END header not found` = corrupt WAR: delete `webapps\{{APP}}.war` and `webapps\{{APP}}`, rebuild, start Tomcat first, then deploy. OneDrive folders can lock `target\`.
- Redeploy not picking up changes: stop Tomcat, delete `webapps\{{APP}}` folder, start again.
- Port 8080 busy: another Tomcat is running.

---

## 5. Database

Rules: PK on every table, `UNIQUE` on natural keys, FKs with `ON DELETE CASCADE`, re-runnable script, availability **derived** (no stored status column).

```sql
DROP SCHEMA IF EXISTS {{DB}};                                  -- REFACTOR name
CREATE SCHEMA {{DB}} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE {{DB}};

-- Users: needs a unique username, a password, and a role
CREATE TABLE {{users}} (
    {{user}}_id    INT AUTO_INCREMENT PRIMARY KEY,
    user_number    VARCHAR(20)  NOT NULL UNIQUE,               -- REFACTOR: your natural key
    full_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(100) NOT NULL UNIQUE,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password       VARCHAR(100) NOT NULL,
    role           VARCHAR(30)  NOT NULL DEFAULT '{{ROLE_USER}}'
);

-- Main entity: REFACTOR the columns
CREATE TABLE {{entities}} (
    {{entity}}_id  INT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(20)  NOT NULL UNIQUE,
    name           VARCHAR(150) NOT NULL,
    description    TEXT,
    quantity       INT                                         -- nullable number example
);

-- Link table: a user uses an entity from started_on until ended_on (NULL = still active)
CREATE TABLE {{links}} (
    {{link}}_id    INT AUTO_INCREMENT PRIMARY KEY,
    {{user}}_id    INT  NOT NULL,
    {{entity}}_id  INT  NOT NULL,
    started_on     DATE NOT NULL,
    ended_on       DATE NULL,
    CONSTRAINT fk_{{links}}_{{user}}   FOREIGN KEY ({{user}}_id)   REFERENCES {{users}}({{user}}_id)       ON DELETE CASCADE,
    CONSTRAINT fk_{{links}}_{{entity}} FOREIGN KEY ({{entity}}_id) REFERENCES {{entities}}({{entity}}_id) ON DELETE CASCADE,
    INDEX idx_{{links}}_active ({{entity}}_id, ended_on)
);

-- DataSourceRealm needs a (username, role) table. A view avoids a second table to keep in sync.
-- The username column MUST have the same name as in the users table
-- (the realm's userNameCol is used for BOTH tables).
CREATE VIEW user_roles AS
    SELECT username, role AS role_name FROM {{users}};
```

Seed (enough to demo every rule):

```sql
INSERT INTO {{users}} (user_number, full_name, email, username, password, role) VALUES
('U-001','Test One','one@example.com','one','password1','{{ROLE_USER}}'),
('U-002','Test Two','two@example.com','two','password2','{{ROLE_USER}}'),
('U-009','Admin User','admin@example.com','admin','admin123','{{ROLE_ADMIN}}');

INSERT INTO {{entities}} (code, name, description, quantity) VALUES
('E-001','First item','Example',10),
('E-002','Second item','Example',NULL);

-- one user at the limit (demo the max rule), one with finished + active rows (demo cascade),
-- at least ~8 link rows in total; ended_on NULL = active
INSERT INTO {{links}} ({{user}}_id, {{entity}}_id, started_on, ended_on) VALUES
(1, 1, '2026-01-10', NULL);
```

Derived availability (no status column):

```sql
SELECT e.*,
       NOT EXISTS (SELECT 1 FROM {{links}} x WHERE x.{{entity}}_id = e.{{entity}}_id AND x.ended_on IS NULL) AS available
FROM {{entities}} e;
```

Cascade demo for evidence: `SELECT * FROM {{links}};` before, delete a user through the app, same `SELECT` after.
Typo trap: a misspelled schema name gives `Unknown database`. Run the whole script and keep the `USE` line.

---

## 6. context.xml (DataSource, Realm, Valve)

`src/main/webapp/META-INF/context.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Context>

    <!-- REFACTOR: name, url (schema), username, password -->
    <Resource name="{{JNDI}}"
              auth="Container"
              type="javax.sql.DataSource"
              driverClassName="com.mysql.cj.jdbc.Driver"
              url="jdbc:mysql://localhost:3306/{{DB}}?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC"
              username="root"
              password="YOUR_MYSQL_PASSWORD"
              maxTotal="20"
              maxIdle="10"/>

    <!-- REFACTOR: table and column names must match section 5 -->
    <Realm className="org.apache.catalina.realm.DataSourceRealm"
           dataSourceName="{{JNDI}}"
           localDataSource="true"
           userTable="{{users}}"
           userNameCol="username"
           userCredCol="password"
           userRoleTable="user_roles"
           roleNameCol="role_name"/>

    <!-- lets someone who opens login.jsp directly land somewhere after login (otherwise HTTP 400) -->
    <Valve className="org.apache.catalina.authenticator.FormAuthenticator"
           landingPage="{{HOME}}"/>

</Context>
```

| Attribute | Must equal |
|---|---|
| Resource `name` = Realm `dataSourceName` = string in `Database.java` | the same `{{JNDI}}` (3 places) |
| Realm `userTable` | users table |
| Realm `userNameCol` | username column in users table **and** in the roles view |
| Realm `userCredCol` | password column |
| Realm `userRoleTable` / `roleNameCol` | the roles view and its role column |
| URL `{{DB}}` | the schema from section 5 |

Notes:
- `&` in XML must be `&amp;`.
- Plain-text passwords need no extra config. For hashed passwords add `<CredentialHandler className="org.apache.catalina.realm.MessageDigestCredentialHandler" algorithm="SHA-256"/>` inside the Realm.
- Tomcat only reads this file when the WAR is deployed from `webapps` (section 4).
- **LockOutRealm:** 5 failed logins lock a username for 5 minutes, even with the right password. The log says `attempt was made to authenticate the locked user`. Restart Tomcat or wait.
- Realm test: temporarily set `userTable="x_missing"`. A `SEVERE DataSourceRealm` error in the log proves the realm is active; silence means it is not being loaded.

---

## 7. Config: JNDI helper

`config/Database.java` (only the JNDI string changes):

```java
package {{PKG}}.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Gives DAOs a pooled connection defined in context.xml. */
public final class Database {

    private static DataSource dataSource;

    private Database() {}

    /** close() returns the connection to the pool, so always use try-with-resources. */
    public static Connection getConnection() throws SQLException {
        return lookup().getConnection();
    }

    private static synchronized DataSource lookup() throws SQLException {
        if (dataSource == null) {
            try {
                Context env = (Context) new InitialContext().lookup("java:comp/env");
                dataSource = (DataSource) env.lookup("{{JNDI}}");        // REFACTOR: same as <Resource name>
            } catch (NamingException e) {
                throw new SQLException("DataSource {{JNDI}} not found", e);
            }
        }
        return dataSource;
    }
}
```

`Name [...] is not bound` in the log = `context.xml` was not applied (section 4) or the names differ.

---

## 8. Models

One row as a Java object: private fields, getters/setters, empty constructor. EL (`${x.name}`) calls `getName()`, so a missing getter means a blank page.

**Main entity** (REFACTOR the fields):

```java
package {{PKG}}.model;

public class {{Entity}} {
    private int {{entity}}Id;
    private String code;
    private String name;
    private String description;
    private Integer quantity;        // Integer (not int) because the column can be NULL
    private Boolean available;       // filled by the query, not stored

    public {{Entity}}() {}

    public int get{{Entity}}Id() { return {{entity}}Id; }
    public void set{{Entity}}Id(int {{entity}}Id) { this.{{entity}}Id = {{entity}}Id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }
}
```

**Lombok version** (same methods generated at compile time):

```java
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @NoArgsConstructor @ToString
public class {{Entity}} {
    private int {{entity}}Id;
    private String code;
    private String name;
    private String description;
    private Integer quantity;
    private Boolean available;
}
```

Lombok notes: primitive `boolean x` gets `isX()`, wrapper `Boolean x` gets `getX()`. Exclude secrets: `@ToString(exclude = "password")`.

**Users** model fields: `{{user}}Id, userNumber, fullName, email, username, password, role` (REFACTOR to your columns). If old code needs a first name:

```java
public String getFirstName() {
    if (fullName == null || fullName.trim().isEmpty()) return "";   // isBlank() needs Java 11+
    return fullName.trim().split("\\s+")[0];
}
```

**Link** model: `{{link}}Id, {{user}}Id, {{entity}}Id, entityName (joined, for display), LocalDate startedOn, LocalDate endedOn`.

---

## 9. Exceptions

All three are generic: copy as is (only the package changes).

```java
package {{PKG}}.exception;

/** Unchecked. The database failed. Travels to error.jsp via web.xml, never to the user as a stack trace. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
```

```java
package {{PKG}}.exception;

import java.util.Collections;
import java.util.Map;

/** Checked. User input broke a rule. Carries field -> message so the form can show every error. */
public class ValidationException extends Exception {
    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super("Validation failed: " + errors);
        this.errors = Collections.unmodifiableMap(errors);
    }

    public Map<String, String> getErrors() { return errors; }
}
```

```java
package {{PKG}}.exception;

/** Checked. A business rule was broken. The message is shown to the user. */
public class RuleException extends Exception {
    public RuleException(String reason) { super(reason); }
}
```

Checked = expected, the caller must handle it. Unchecked = unexpected, a controller cannot fix it. Map the unchecked one in `web.xml` (section 14).

---

## 10. DAOs

Pattern: interface + implementation. SQL only. **`PreparedStatement` for every query**, `?` placeholders, `%` wildcards added to the bound value, try-with-resources on Connection/PreparedStatement/ResultSet. Log the `SQLException`, throw `DataAccessException`.

**Interface** (add a `findBy...` for every UNIQUE column):

```java
package {{PKG}}.dao;

import java.util.List;
import {{PKG}}.model.{{Entity}};

public interface {{Entity}}Dao {
    List<{{Entity}}> findAll();
    {{Entity}} findById(int id);               // null if not found
    {{Entity}} findByCode(String code);        // REFACTOR: your unique column; used for duplicate checks
    List<{{Entity}}> search(String term);
    void create({{Entity}} e);                 // sets the generated id on e
    void update({{Entity}} e);
    void delete(int id);
}
```

**Implementation** (REFACTOR: the SELECT, the INSERT/UPDATE, `bind`, `map`):

```java
package {{PKG}}.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import {{PKG}}.config.Database;
import {{PKG}}.exception.DataAccessException;
import {{PKG}}.model.{{Entity}};

public class {{Entity}}DaoImpl implements {{Entity}}Dao {

    private static final Logger LOG = Logger.getLogger({{Entity}}DaoImpl.class.getName());

    // REFACTOR: column list. "available" is derived from the link table.
    private static final String SELECT =
            "SELECT e.{{entity}}_id, e.code, e.name, e.description, e.quantity, "
          + "NOT EXISTS (SELECT 1 FROM {{links}} x WHERE x.{{entity}}_id = e.{{entity}}_id AND x.ended_on IS NULL) AS available "
          + "FROM {{entities}} e";

    @Override public List<{{Entity}}> findAll() { return query(SELECT + " ORDER BY e.{{entity}}_id"); }

    @Override public {{Entity}} findById(int id) { return first(query(SELECT + " WHERE e.{{entity}}_id = ?", id)); }

    @Override public {{Entity}} findByCode(String code) { return first(query(SELECT + " WHERE e.code = ?", code)); }

    @Override
    public List<{{Entity}}> search(String term) {
        String like = "%" + term.trim() + "%";            // wildcards on the VALUE, never in the SQL text
        return query(SELECT + " WHERE e.name LIKE ? OR e.code LIKE ? ORDER BY e.{{entity}}_id", like, like);
    }

    @Override
    public void create({{Entity}} e) {
        String sql = "INSERT INTO {{entities}} (code, name, description, quantity) VALUES (?, ?, ?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, e);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) e.set{{Entity}}Id(keys.getInt(1));
            }
        } catch (SQLException ex) { throw fail("Could not create {{entity}}", ex); }
    }

    @Override
    public void update({{Entity}} e) {
        String sql = "UPDATE {{entities}} SET code=?, name=?, description=?, quantity=? WHERE {{entity}}_id=?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, e);
            ps.setInt(5, e.get{{Entity}}Id());            // REFACTOR: index = number of columns bound + 1
            ps.executeUpdate();
        } catch (SQLException ex) { throw fail("Could not update {{entity}}", ex); }
    }

    @Override
    public void delete(int id) {
        // link rows go with it via ON DELETE CASCADE (do not delete them in Java)
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM {{entities}} WHERE {{entity}}_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) { throw fail("Could not delete {{entity}}", ex); }
    }

    /** Parameter numbers start at 1 and must match the ? order in the SQL. */
    private void bind(PreparedStatement ps, {{Entity}} e) throws SQLException {
        ps.setString(1, e.getCode());
        ps.setString(2, e.getName());
        ps.setString(3, e.getDescription());
        ps.setObject(4, e.getQuantity(), Types.INTEGER);   // writes NULL when null
    }

    /** One helper for every SELECT: binds ? in order, maps each row to an object. */
    private List<{{Entity}}> query(String sql, Object... params) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<{{Entity}}> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException ex) { throw fail("Could not read {{entities}}", ex); }
    }

    private {{Entity}} first(List<{{Entity}}> list) { return list.isEmpty() ? null : list.get(0); }

    private {{Entity}} map(ResultSet rs) throws SQLException {
        {{Entity}} e = new {{Entity}}();
        e.set{{Entity}}Id(rs.getInt("{{entity}}_id"));
        e.setCode(rs.getString("code"));
        e.setName(rs.getString("name"));
        e.setDescription(rs.getString("description"));
        e.setQuantity(rs.getObject("quantity", Integer.class));   // keeps NULL as null
        e.setAvailable(rs.getBoolean("available"));
        return e;
    }

    private DataAccessException fail(String message, SQLException ex) {
        LOG.log(Level.SEVERE, message, ex);
        return new DataAccessException(message, ex);
    }
}
```

**`{{User}}Dao`**: same pattern. Methods: `findAll, findById, findByUsername, findByUserNumber, findByEmail, search, create, update, delete` (a `findBy...` for each UNIQUE column plus `findByUsername` for login identity).

**`{{Link}}Dao`**:

```java
public interface {{Link}}Dao {
    void create(int {{user}}Id, int {{entity}}Id);              // started_on = CURDATE()
    boolean finish(int {{user}}Id, int {{entity}}Id);           // true if an active row was ended
    int countActiveBy{{User}}(int {{user}}Id);
    List<{{Link}}> findActiveBy{{User}}(int {{user}}Id);
    List<{{Link}}> findHistoryBy{{User}}(int {{user}}Id);
    boolean hasActive{{Link}}(int {{entity}}Id);
}
```

Key SQL (REFACTOR names):

```java
// start
"INSERT INTO {{links}} ({{user}}_id, {{entity}}_id, started_on) VALUES (?, ?, CURDATE())"
// finish = set a date, never delete the row (it becomes history)
"UPDATE {{links}} SET ended_on = CURDATE() WHERE {{user}}_id = ? AND {{entity}}_id = ? AND ended_on IS NULL"   // executeUpdate() > 0
// count active
"SELECT COUNT(*) FROM {{links}} WHERE {{user}}_id = ? AND ended_on IS NULL"
// active rows with a display name from the joined table
"SELECT x.{{link}}_id, x.{{user}}_id, x.{{entity}}_id, e.name, x.started_on, x.ended_on "
+ "FROM {{links}} x JOIN {{entities}} e ON e.{{entity}}_id = x.{{entity}}_id "
+ "WHERE x.{{user}}_id = ? AND x.ended_on IS NULL ORDER BY x.started_on"
// history: same query with  AND x.ended_on IS NOT NULL ORDER BY x.ended_on DESC
// dates, null-safe
l.setStartedOn(rs.getObject("started_on", LocalDate.class));
l.setEndedOn(rs.getObject("ended_on", LocalDate.class));
// is it taken?
"SELECT 1 FROM {{links}} WHERE {{entity}}_id = ? AND ended_on IS NULL LIMIT 1"      // rs.next()
```

Never concatenate request values into SQL. `executeQuery` returns rows; `executeUpdate` returns the number of rows changed.

---

## 11. Services

Services own rules and call DAOs. Controllers never decide rules.

**`{{Entity}}Service`** (REFACTOR the validation rules):

```java
package {{PKG}}.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import {{PKG}}.dao.*;
import {{PKG}}.exception.ValidationException;
import {{PKG}}.model.{{Entity}};

public class {{Entity}}Service {

    private final {{Entity}}Dao dao;
    private final {{Link}}Dao {{link}}Dao;

    public {{Entity}}Service() { this(new {{Entity}}DaoImpl(), new {{Link}}DaoImpl()); }

    // constructor injection: a test can pass fake DAOs (no database needed)
    public {{Entity}}Service({{Entity}}Dao dao, {{Link}}Dao {{link}}Dao) {
        this.dao = dao;
        this.{{link}}Dao = {{link}}Dao;
    }

    public List<{{Entity}}> list(String search) {
        if (search == null || search.trim().isEmpty()) return dao.findAll();
        return dao.search(search.trim());
    }

    public {{Entity}} get(int id) { return dao.findById(id); }

    public void add({{Entity}} e) throws ValidationException {
        tidy(e);
        validate(e);
        dao.create(e);
    }

    public void edit({{Entity}} e) throws ValidationException {
        tidy(e);
        validate(e);
        dao.update(e);
    }

    /** Example delete rule: cannot delete something that is currently in use. Old link rows go via cascade. */
    public void remove(int id) throws ValidationException {
        {{Entity}} e = dao.findById(id);
        if (e == null) return;
        if ({{link}}Dao.hasActive{{Link}}(id)) {
            Map<String, String> errors = new LinkedHashMap<>();
            errors.put("general", e.getName() + " cannot be deleted because it is currently in use.");
            throw new ValidationException(errors);
        }
        dao.delete(id);
    }

    private void tidy({{Entity}} e) {                              // trim before validating and saving
        if (e.getCode() != null) e.setCode(e.getCode().trim());
        if (e.getName() != null) e.setName(e.getName().trim());
    }

    /** Collects ALL errors, not just the first, so the form shows them together. */
    private void validate({{Entity}} e) throws ValidationException {
        Map<String, String> errors = new LinkedHashMap<>();

        if (blank(e.getCode())) errors.put("code", "Code is required.");
        else if (e.getCode().length() > 20) errors.put("code", "Code must be 20 characters or fewer.");
        else {
            {{Entity}} other = dao.findByCode(e.getCode());
            // a DIFFERENT row with the same value (editing must not clash with itself)
            if (other != null && other.get{{Entity}}Id() != e.get{{Entity}}Id())
                errors.put("code", "This code already exists.");
        }

        if (blank(e.getName())) errors.put("name", "Name is required.");

        Integer q = e.getQuantity();                               // optional number: null is fine
        if (q != null && (q < 0 || q > 100000)) errors.put("quantity", "Quantity must be between 0 and 100000.");

        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
```

**`{{User}}Service`**: same shape, plus (REFACTOR to your fields):

```java
private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

// required: userNumber, fullName, email, username (+ password on create)
// format:   if (!EMAIL.matcher(u.getEmail()).matches()) errors.put("email", "Enter a valid email address.");
// unique:   userNumber, email, username each via findByX; clash only if other.get{{User}}Id() != u.get{{User}}Id()
// role:     on create, force the default role: u.setRole("{{ROLE_USER}}") unless an admin explicitly sets another
// edit:     blank password means keep the stored one:
//           if (blank(u.getPassword())) u.setPassword(userDao.findById(u.get{{User}}Id()).getPassword());
// delete:   the controller passes the signed-in user's id; reject if it equals the id being deleted
```

**`{{Link}}Service`** (your business rules live here):

```java
package {{PKG}}.service;

import java.util.List;

import {{PKG}}.dao.*;
import {{PKG}}.exception.RuleException;
import {{PKG}}.model.{{Entity}};
import {{PKG}}.model.{{Link}};

public class {{Link}}Service {

    public static final int MAX_ACTIVE = {{MAXN}};            // REFACTOR: defined ONCE; controllers and JSPs read it from here

    private final {{Entity}}Dao entityDao = new {{Entity}}DaoImpl();
    private final {{Link}}Dao dao = new {{Link}}DaoImpl();

    public void start(int {{user}}Id, int {{entity}}Id) throws RuleException {
        {{Entity}} e = entityDao.findById({{entity}}Id);
        if (e == null)
            throw new RuleException("That item does not exist.");
        if (!Boolean.TRUE.equals(e.getAvailable()))
            throw new RuleException(e.getName() + " is not available because it is already in use.");
        if (dao.countActiveBy{{User}}({{user}}Id) >= MAX_ACTIVE)
            throw new RuleException("You already have " + MAX_ACTIVE + " active items, the maximum allowed. "
                    + "Finish one before starting another.");
        dao.create({{user}}Id, {{entity}}Id);
    }

    public void finish(int {{user}}Id, int {{entity}}Id) throws RuleException {
        if (!dao.finish({{user}}Id, {{entity}}Id))             // only succeeds for THIS user's active row
            throw new RuleException("That item is not currently active for you.");
    }

    public List<{{Link}}> active(int {{user}}Id)  { return dao.findActiveBy{{User}}({{user}}Id); }
    public List<{{Link}}> history(int {{user}}Id) { return dao.findHistoryBy{{User}}({{user}}Id); }
    public int activeCount(int {{user}}Id)        { return dao.countActiveBy{{User}}({{user}}Id); }
}
```

Rule-order tip: check "does it exist" -> "is it free" -> "is the user under their limit" -> write. Return a specific message for each.

---

## 12. Controllers

Rules: GET reads/displays, **POST changes data**, every successful POST **redirects** (Post/Redirect/Get). Views live in `WEB-INF` and are reached only by `forward`. Identify the user with `request.getRemoteUser()`, never from a form field or URL.

**Who is signed in** (any controller):

```java
{{User}} me = userDao.findByUsername(request.getRemoteUser());   // container-verified username
```

**Shared home page** (both roles; only the data source swaps):

```java
@WebServlet(name = "HomeController", urlPatterns = "{{HOME}}")
public class HomeController extends HttpServlet {

    private final {{Entity}}Service service = new {{Entity}}Service();
    private final {{User}}Dao userDao = new {{User}}DaoImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String search = request.getParameter("search");
        {{User}} me = userDao.findByUsername(request.getRemoteUser());

        request.setAttribute("userName", me != null ? me.getFullName() : request.getRemoteUser());
        request.setAttribute("{{entities}}", service.list(search));
        request.setAttribute("search", search);
        request.getRequestDispatcher("/WEB-INF/home.jsp").forward(request, response);
    }
}
```

**List + search (admin, GET)**:

```java
@WebServlet(name = "{{Entity}}ListController", urlPatterns = "{{ADMIN}}/{{entities}}")
public class {{Entity}}ListController extends HttpServlet {

    private final {{Entity}}Service service = new {{Entity}}Service();   // stateless, safe to share across threads

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String search = request.getParameter("search");
        request.setAttribute("{{entities}}", service.list(search));
        request.setAttribute("search", search);
        request.getRequestDispatcher("/WEB-INF/admin/{{entities}}.jsp").forward(request, response);
    }
}
```

**Add/edit form** (GET shows, POST saves). REFACTOR the parameter names:

```java
@WebServlet(name = "{{Entity}}FormController", urlPatterns = "{{ADMIN}}/{{entities}}/form")
public class {{Entity}}FormController extends HttpServlet {

    private final {{Entity}}Service service = new {{Entity}}Service();

    /** No id = empty add form. ?id=5 = edit form pre-filled with the current values. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        {{Entity}} item = new {{Entity}}();
        String id = request.getParameter("id");
        if (id != null && !id.trim().isEmpty()) {
            try {
                item = service.get(Integer.parseInt(id.trim()));
            } catch (NumberFormatException e) {
                item = null;
            }
            if (item == null) {
                response.sendRedirect(request.getContextPath() + "{{ADMIN}}/{{entities}}");
                return;
            }
        }
        request.setAttribute("item", item);
        request.getRequestDispatcher("/WEB-INF/admin/{{entity}}-form.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        {{Entity}} item = new {{Entity}}();
        Map<String, String> errors = new LinkedHashMap<>();

        item.set{{Entity}}Id(parseInt(request.getParameter("{{entity}}Id"), 0));   // hidden field; 0 = new
        item.setCode(request.getParameter("code"));
        item.setName(request.getParameter("name"));
        item.setDescription(request.getParameter("description"));

        String q = request.getParameter("quantity");
        if (q != null && !q.trim().isEmpty()) {
            try {
                item.setQuantity(Integer.parseInt(q.trim()));
            } catch (NumberFormatException e) {                                   // "20x4" becomes a form error, not a 500
                errors.put("quantity", "Quantity must be a whole number.");
            }
        }

        if (errors.isEmpty()) {
            try {
                if (item.get{{Entity}}Id() == 0) service.add(item);
                else service.edit(item);

                request.getSession().setAttribute("flash", "Saved.");           // shown once on the list page
                response.sendRedirect(request.getContextPath() + "{{ADMIN}}/{{entities}}");   // Post/Redirect/Get
                return;
            } catch (ValidationException e) {
                errors.putAll(e.getErrors());
            }
        }

        request.setAttribute("errors", errors);     // the JSP shows each message
        request.setAttribute("item", item);         // the JSP re-fills the typed values
        request.getRequestDispatcher("/WEB-INF/admin/{{entity}}-form.jsp").forward(request, response);
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }
}
```

**Delete with confirmation** (GET = confirm page, POST = do it; never delete from a GET link):

```java
@WebServlet(name = "{{Entity}}DeleteController", urlPatterns = "{{ADMIN}}/{{entities}}/delete")
public class {{Entity}}DeleteController extends HttpServlet {

    private final {{Entity}}Service service = new {{Entity}}Service();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            {{Entity}} item = service.get(Integer.parseInt(request.getParameter("id")));
            if (item != null) {
                request.setAttribute("item", item);
                request.getRequestDispatcher("/WEB-INF/admin/{{entity}}-delete.jsp").forward(request, response);
                return;
            }
        } catch (NumberFormatException ignored) { }
        response.sendRedirect(request.getContextPath() + "{{ADMIN}}/{{entities}}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            int id = Integer.parseInt(request.getParameter("id"));
            {{Entity}} item = service.get(id);
            service.remove(id);
            request.getSession().setAttribute("flash", item == null ? "Deleted." : item.getName() + " has been deleted.");
        } catch (ValidationException e) {
            request.getSession().setAttribute("flashError", e.getErrors().get("general"));
        } catch (NumberFormatException ignored) { }
        response.sendRedirect(request.getContextPath() + "{{ADMIN}}/{{entities}}");
    }
}
```

**Deleting a user** (extra rules: no self-delete, cascade message):

```java
{{User}} me = userDao.findByUsername(request.getRemoteUser());
{{User}} target = userService.get(id);
if (target != null && target.get{{User}}Id() == me.get{{User}}Id()) {
    session.setAttribute("flashError", "You cannot delete the account you are currently signed in with.");
} else if (target != null) {
    userService.remove(id);                       // the FK cascade deletes their link rows
    session.setAttribute("flash", target.getFullName() + " and their history have been deleted.");
}
response.sendRedirect(request.getContextPath() + "{{ADMIN}}/{{users}}");
```

**User action: start / finish** (normal role). `/start`, `/finish`, `/mine`:

```java
@WebServlet(name = "StartController", urlPatterns = "/start")
public class StartController extends HttpServlet {

    private final {{Link}}Service service = new {{Link}}Service();
    private final {{User}}Dao userDao = new {{User}}DaoImpl();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        {{User}} me = userDao.findByUsername(request.getRemoteUser());          // identity from the container
        try {
            int id = Integer.parseInt(request.getParameter("{{entity}}Id"));
            service.start(me.get{{User}}Id(), id);
            request.getSession().setAttribute("flash", "Done.");
        } catch (RuleException e) {
            request.getSession().setAttribute("flashError", e.getMessage());    // survives the redirect
        } catch (NumberFormatException e) {
            request.getSession().setAttribute("flashError", "Invalid item.");
        }
        response.sendRedirect(request.getContextPath() + "{{HOME}}");
    }
}
```

`/finish` is identical with `service.finish(...)`, redirecting to `/mine`. `/mine` (GET) sets `active`, `history`, `activeCount`, and `maxActive` (`{{Link}}Service.MAX_ACTIVE`) on the request, then forwards to `/WEB-INF/mine.jsp`.

---

## 13. Views (JSP + JSTL)

Rules: JSTL/EL only (**no `<% %>`**), every user-controlled value through `<c:out>`, every page except the login pages under `WEB-INF`, UTF-8 page directive.

**Page header**:

```jsp
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
```

**Flash messages** (set in a controller before a redirect, shown once):

```jsp
<c:if test="${not empty sessionScope.flash}">
    <div class="alert alert-success"><c:out value="${sessionScope.flash}"/></div>
    <c:remove var="flash" scope="session"/>
</c:if>
<c:if test="${not empty sessionScope.flashError}">
    <div class="alert alert-danger"><c:out value="${sessionScope.flashError}"/></div>
    <c:remove var="flashError" scope="session"/>
</c:if>
```

**Search + table + empty state** (REFACTOR the columns):

```jsp
<form method="get" action="<c:url value='{{HOME}}'/>" class="d-flex gap-2 mb-3">
    <input type="text" name="search" class="form-control" value="<c:out value='${search}'/>">
    <button type="submit" class="btn btn-primary">Search</button>
</form>

<c:choose>
    <c:when test="${empty {{entities}}}"><div class="alert alert-info">Nothing found.</div></c:when>
    <c:otherwise>
        <table class="table table-striped">
            <thead><tr><th>Code</th><th>Name</th><th>Status</th><th></th></tr></thead>
            <tbody>
            <c:forEach var="e" items="${{{entities}}}">
                <tr>
                    <td><c:out value="${e.code}"/></td>
                    <td><c:out value="${e.name}"/></td>
                    <td><c:out value="${e.available ? 'Available' : 'In use'}"/></td>
                    <td>
                        <c:if test="${e.available}">
                            <%-- an action that changes data is a POST form, never a GET link --%>
                            <form method="post" action="<c:url value='/start'/>" class="d-inline">
                                <input type="hidden" name="{{entity}}Id" value="${e.{{entity}}Id}">
                                <button class="btn btn-sm btn-success">Start</button>
                            </form>
                        </c:if>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </c:otherwise>
</c:choose>
```

**Add/edit form with errors + sticky values** (REFACTOR the fields):

```jsp
<c:set var="editing" value="${item.{{entity}}Id != 0}"/>
<h1 class="h4">${editing ? 'Edit' : 'Add'}</h1>

<c:if test="${not empty errors}">
    <div class="alert alert-danger">Please fix the errors below.</div>
</c:if>

<form method="post" action="<c:url value='{{ADMIN}}/{{entities}}/form'/>">
    <input type="hidden" name="{{entity}}Id" value="${item.{{entity}}Id}">

    <div class="mb-3">
        <label class="form-label" for="code">Code</label>
        <input class="form-control ${not empty errors.code ? 'is-invalid' : ''}" id="code" name="code"
               value="<c:out value='${item.code}'/>">
        <div class="invalid-feedback"><c:out value="${errors.code}"/></div>
    </div>

    <div class="mb-3">
        <label class="form-label" for="quantity">Quantity</label>
        <input class="form-control ${not empty errors.quantity ? 'is-invalid' : ''}" id="quantity" name="quantity"
               value="<c:out value='${item.quantity}'/>">
        <div class="invalid-feedback"><c:out value="${errors.quantity}"/></div>
    </div>

    <button type="submit" class="btn btn-primary">Save</button>
    <a class="btn btn-secondary" href="<c:url value='{{ADMIN}}/{{entities}}'/>">Cancel</a>
</form>
```

**Delete confirmation**:

```jsp
<div class="alert alert-warning">
    Delete <strong><c:out value="${item.name}"/></strong>? Its history will also be deleted.
</div>
<form method="post" action="<c:url value='{{ADMIN}}/{{entities}}/delete'/>">
    <input type="hidden" name="id" value="${item.{{entity}}Id}">
    <button class="btn btn-danger">Yes, delete</button>
    <a class="btn btn-secondary" href="<c:url value='{{ADMIN}}/{{entities}}'/>">Cancel</a>
</form>
```

**"Mine" page** (limit shown without hard-coding the number):

```jsp
<p>In use: ${activeCount} of ${maxActive}</p>
<c:forEach var="l" items="${active}">
    <tr>
        <td><c:out value="${l.entityName}"/></td>
        <td>${l.startedOn}</td>
        <td>
            <form method="post" action="<c:url value='/finish'/>">
                <input type="hidden" name="{{entity}}Id" value="${l.{{entity}}Id}">
                <button class="btn btn-sm btn-warning">Finish</button>
            </form>
        </td>
    </tr>
</c:forEach>
```

**Show links only for the privileged role** (convenience only; the security constraint is the real protection):

```jsp
<c:if test="${pageContext.request.isUserInRole('{{ROLE_ADMIN}}')}">
    <a href="<c:url value='{{ADMIN}}/{{entities}}'/>">Manage</a>
</c:if>
```

Notes:
- `${empty x}` is true for null, empty string, or empty list.
- Use `<c:url>` so the context path is added to links.
- `<c:out>` escapes HTML; a bare `${...}` does not. Always `c:out` text a user can control (names, search text, messages).
- A `.jspf` fragment is pulled in with `<%@ include file="..." %>`; that directive is allowed (it is not a scriptlet).

---

## 14. Auth

Flow: protected URL -> Tomcat checks the constraint -> not signed in -> `login.jsp` -> form posts to `j_security_check` -> Realm checks the users table -> redirect to the original URL. Wrong role = **403**. Wrong password = `login-error.jsp`.

**`WEB-INF/web.xml`** (REFACTOR: roles, URL patterns, realm name, exception class):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">

    <welcome-file-list>
        <welcome-file>login.jsp</welcome-file>
    </welcome-file-list>

    <!-- role names must match the role column values EXACTLY -->
    <security-role><role-name>{{ROLE_USER}}</role-name></security-role>
    <security-role><role-name>{{ROLE_ADMIN}}</role-name></security-role>

    <!-- shared page: both roles -->
    <security-constraint>
        <web-resource-collection>
            <web-resource-name>Home</web-resource-name>
            <url-pattern>{{HOME}}</url-pattern>
        </web-resource-collection>
        <auth-constraint>
            <role-name>{{ROLE_USER}}</role-name>
            <role-name>{{ROLE_ADMIN}}</role-name>
        </auth-constraint>
    </security-constraint>

    <!-- user actions: normal role only -->
    <security-constraint>
        <web-resource-collection>
            <web-resource-name>User Actions</web-resource-name>
            <url-pattern>/mine</url-pattern>
            <url-pattern>/start</url-pattern>
            <url-pattern>/finish</url-pattern>
        </web-resource-collection>
        <auth-constraint>
            <role-name>{{ROLE_USER}}</role-name>
        </auth-constraint>
    </security-constraint>

    <!-- management area: privileged role only -->
    <security-constraint>
        <web-resource-collection>
            <web-resource-name>Admin Area</web-resource-name>
            <url-pattern>{{ADMIN}}/*</url-pattern>
        </web-resource-collection>
        <auth-constraint>
            <role-name>{{ROLE_ADMIN}}</role-name>
        </auth-constraint>
    </security-constraint>

    <login-config>
        <auth-method>FORM</auth-method>
        <realm-name>{{APP}}</realm-name>
        <form-login-config>
            <form-login-page>/login.jsp</form-login-page>
            <form-error-page>/login-error.jsp</form-error-page>
        </form-login-config>
    </login-config>

    <!-- database failures show a friendly page, never a stack trace -->
    <error-page>
        <exception-type>{{PKG}}.exception.DataAccessException</exception-type>
        <location>/WEB-INF/error.jsp</location>
    </error-page>

</web-app>
```

Notes:
- Do **not** constrain `login.jsp`, `login-error.jsp`, `/css/*`, `/js/*`, `/images/*`, `/logout`.
- A constraint with no HTTP methods listed covers **all** methods.
- Constraints run before servlet mapping, so a wrong-role user gets 403 even on a URL that doesn't exist.
- Roles come from the roles view (section 5) through the Realm (section 6).

**`login.jsp`** (outside WEB-INF; the field names are fixed by the spec):

```jsp
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Sign in</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
<div class="container" style="max-width:420px;margin-top:10vh;">
    <form method="post" action="<c:url value='/j_security_check'/>" class="card card-body shadow-sm">
        <h1 class="h4 mb-3">Sign in</h1>
        <input class="form-control mb-3" type="text" name="j_username" placeholder="Username" required autofocus>
        <input class="form-control mb-3" type="password" name="j_password" placeholder="Password" required>
        <button class="btn btn-primary w-100">Sign in</button>
    </form>
</div>
</body>
</html>
```

`login-error.jsp` = the same page plus `<div class="alert alert-danger">Incorrect username or password.</div>` above the form.

**Logout**:

```java
@WebServlet(name = "LogoutController", value = "/logout")
public class LogoutController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.logout();                                          // container drops the principal
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        // redirect to a PROTECTED url: Tomcat shows the login form and remembers where you were going
        response.sendRedirect(request.getContextPath() + "{{HOME}}");
    }
}
```

**Whoami:** `request.getRemoteUser()` (null if anonymous), `request.isUserInRole("{{ROLE_ADMIN}}")`. Never take a user id from a form or URL for actions that belong to the signed-in user.

**Tests to screenshot:** login ok (both roles); wrong password -> `login-error.jsp`; normal user opens `{{ADMIN}}/...` -> 403; admin opens a user-only URL -> 403; logout then Back -> protected page not shown.

---

## 15. Filters

**NoCacheFilter**: stops the browser caching protected pages (Back after logout must not reveal them), and sets UTF-8 on requests. REFACTOR the URL list to match `web.xml`:

```java
package {{PKG}}.filter;

import java.io.IOException;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

@WebFilter(filterName = "NoCacheFilter",
           urlPatterns = {"{{HOME}}", "/mine", "/start", "/finish", "{{ADMIN}}/*"})
public class NoCacheFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");                     // before any getParameter()
        HttpServletResponse res = (HttpServletResponse) response;
        res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        res.setHeader("Pragma", "no-cache");
        res.setDateHeader("Expires", 0);
        chain.doFilter(request, response);                         // continue to the servlet
    }
}
```

**Hand-written session-guard filter** (the older approach; use it only when a task asks for a filter instead of container security):

```java
@WebFilter(filterName = "AuthenticationFilter", urlPatterns = "/*")
public class AuthenticationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI().substring(req.getContextPath().length());
        boolean openPath = path.equals("/") || path.equals("/login") || path.equals("/login.jsp")
                || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/");

        HttpSession session = req.getSession(false);                    // false = do not create one
        boolean signedIn = session != null && session.getAttribute("user") != null;   // REFACTOR attribute name

        if (openPath || signedIn) chain.doFilter(request, response);   // let it through
        else res.sendRedirect(req.getContextPath() + "/login");          // bounce to login
    }
}
```

Filter facts: `urlPatterns` picks the URLs; `chain.doFilter` continues; not calling it stops the request. Container security constraints run **before** filters. Do not run the hand-written guard and container security together.

---

## 16. i18n

**Files** in `src/main/resources`, saved as UTF-8. Same keys in both files.

`messages.properties` (default language; REFACTOR the keys to your screens):

```properties
nav.home=Home
nav.mine=My items
nav.logout=Logout
lang.label=Language
lang.en=English
lang.second=Second language name
home.heading=Items
home.search=Search
home.search.placeholder=Search by name or code
col.code=Code
col.name=Name
col.status=Status
status.available=Available
status.inuse=In use
btn.start=Start
btn.finish=Finish
mine.heading=My items
mine.used=Items in use: {0} of {1}
mine.active=Active
mine.history=History
```

`messages_{{LANG2}}.properties`: the same keys with real translations (have a fluent speaker or your lecturer check them; do not leave English values in it).

**Shared include** `WEB-INF/jspf/i18n.jspf` (top of every translated page, after the `c` taglib line):

```jsp
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%-- ?lang=xx stores the choice in the SESSION so it follows the user between pages --%>
<c:if test="${not empty param.lang}">
    <fmt:setLocale value="${param.lang}" scope="session"/>
</c:if>
<fmt:setBundle basename="messages"/>
```

In each page: `<%@ include file="/WEB-INF/jspf/i18n.jspf" %>`

**Use it**:

```jsp
<h1><fmt:message key="home.heading"/></h1>
<input type="text" name="search" placeholder="<fmt:message key='home.search.placeholder'/>">
<th><fmt:message key="col.name"/></th>

<%-- message with parameters --%>
<fmt:message key="mine.used">
    <fmt:param value="${activeCount}"/>
    <fmt:param value="${maxActive}"/>
</fmt:message>
```

**Language switch**:

```jsp
<a href="?lang=en"><fmt:message key="lang.en"/></a> |
<a href="?lang={{LANG2}}"><fmt:message key="lang.second"/></a>
```

Notes:
- Data that comes from the database is not translated; everything else visible comes from the bundle.
- Each translated JSP needs `pageEncoding="UTF-8"`.
- The bundles must be in `src/main/resources` so they end up in `WEB-INF/classes`.
- A missing key in the second file falls back silently to the default, which can hide a typo; compare the two files.
- `?lang=` works on controller-forwarded pages because request parameters survive a `forward`.

---

## 17. Rules checklist + evidence

**Tick before submitting:**
- [ ] Every query is a `PreparedStatement`; no request value is concatenated into SQL
- [ ] `%` wildcards added to the bound value
- [ ] try-with-resources on every Connection, PreparedStatement, ResultSet
- [ ] No stack trace reaches a page (DAO -> `DataAccessException` -> `web.xml` error-page)
- [ ] No scriptlets (search for `<%`; only `<%@` and `<%--` allowed)
- [ ] Every user-controlled value printed through `<c:out>`
- [ ] Every JSP except the login pages is under `WEB-INF`
- [ ] Create/edit/delete/start/finish use POST then redirect
- [ ] Form errors re-display with the typed values
- [ ] Status/availability is computed, not a column
- [ ] Cascade does the clean-up (no Java loop deleting link rows)
- [ ] Identity from `getRemoteUser()`, not from a request field
- [ ] The business limit is defined once
- [ ] "Finish" sets an end date; the row stays as history
- [ ] Hiding a link is not security: the admin URLs return 403 to the normal role
- [ ] The `.sql` script re-runs cleanly on an empty MySQL
- [ ] Both language files have the same keys, UTF-8, real translations
- [ ] `{{` and `REFACTOR` no longer appear anywhere in the project

**Evidence screenshots:**
1. Seed data in Workbench
2. Login as each role; wrong password; a table of test logins
3. Normal role opens an admin URL: 403 (and the reverse)
4. Logout then Back: protected page not shown
5. Search; input like `<b>x</b>` shown as text (escaping works)
6. Start: success; start something already in use (message); exceed the limit (message)
7. "Mine" page with active items, "x of N", history; finish one
8. Add/edit/delete for each managed entity with validation errors (blank, bad format, duplicate, non-numeric, out of range)
9. Delete blocked while in use; delete of own account blocked
10. Link table in MySQL before and after deleting a user (cascade)
11. Language switch on two pages, persisting between pages
12. A short note of anything you changed in provided classes

---

## 18. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Correct login always fails, nothing in the log | Realm not loaded (IDE Tomcat ignores `META-INF/context.xml`) | Deploy the WAR into your own Tomcat `webapps` (section 4) |
| `attempt was made to authenticate the locked user` | LockOutRealm after 5 failures | Restart Tomcat or wait 5 minutes |
| Login fails, `SEVERE` mentioning DataSourceRealm or an unknown column | Roles view column names wrong | `SELECT username, role AS role_name FROM {{users}}` |
| `Cannot load JDBC driver class` | Connector jar not in Tomcat `lib` | Copy `mysql-connector-j-x.jar` to `TOMCAT/lib`, restart |
| `Name [...] is not bound` | `context.xml` not applied, or names differ | Check `{{JNDI}}` in all 3 places; redeploy as a WAR |
| `Access denied for user` | Wrong DB password | Fix `context.xml`, redeploy |
| `Unknown database` / table missing | Script not run or misspelled schema | Run the whole `.sql`; check the name |
| HTTP 400 after logging in from `login.jsp` directly | No saved request | Open a protected URL first, or rely on the Valve (WAR deployment) |
| `zip END header not found` | Corrupt or half-copied WAR | Delete the WAR and folder, rebuild, start Tomcat first, then deploy |
| `class file has wrong version` | Compiler target 8 | `maven.compiler.release` 17+ |
| `cannot find symbol` on getters | Lombok annotation processing off | Enable it in IDE settings; reload Maven |
| `isBlank()` cannot find symbol | Java 8 language level | Release 17+, or use `trim().isEmpty()` |
| `${x.name}` prints blank | Missing getter or wrong property name | Check the getter and the spelling |
| JSP prints `${...}` literally | Wrong `web.xml` version or taglib URI | `version="6.0"`; taglib `jakarta.tags.core` |
| Garbled characters | Not UTF-8 | Save `.properties` as UTF-8; `pageEncoding="UTF-8"` |
| Refresh repeats an action | Missing redirect | POST then `sendRedirect` |
| 404 on a JSP | It is in `WEB-INF` (correct) | Reach it through a controller `forward` |
| Back shows a protected page after logout | Cached | `NoCacheFilter` on the protected URLs |
| `?` count does not match, `Parameter index out of range` | `bind()` order/number differs from the SQL | Count the `?` marks and the `ps.setX(n, ...)` calls |

---

## 19. Refactor playbook

### A. Setup (10 min)

1. Copy this template; delete `.idea`, `target`, `.git`.
2. Fill the token dictionary (section 0) on paper first.
3. Replace tokens: plurals first, match case, preview on.
4. Rename the root package with the IDE refactor tool, not text replace.
5. Search for `{{` and `REFACTOR`; fix every hit.

### B. Per-entity changes (the real work)

For **each** entity (users, main entity, link), these change together:

| # | File | Rewrite |
|---|---|---|
| 1 | SQL table | columns |
| 2 | Model | fields + getters/setters |
| 3 | DAO interface | `findBy...` for each UNIQUE column |
| 4 | DAO impl | `SELECT`, `INSERT`, `UPDATE`, `bind()`, `map()` |
| 5 | Service | `validate()`, `tidy()`, delete rules |
| 6 | Controllers | `@WebServlet` URL, parameter names, JSP paths, flash text |
| 7 | JSPs | field names, labels, `${...}` properties |
| 8 | Bundles | keys and both languages |

### C. Adding or renaming one column touches 6 places

1. the `CREATE TABLE`
2. the model field + getter/setter
3. DAO: `SELECT`, `INSERT`, `UPDATE`, `bind()` numbers, `map()`
4. the service `validate()`
5. the controller `request.getParameter("...")`
6. the form JSP and the list JSP

Most common bug: `bind()` numbers no longer match the `?` order in the SQL.

### D. Adding a whole new entity

1. Table (+ FK + cascade if it links to others)
2. Model, DAO pair, service
3. List / form / delete controllers (copy section 12, replace the tokens)
4. JSPs (copy section 13)
5. `web.xml` constraint for its URLs, `NoCacheFilter` URL list
6. Menu link (wrapped in the role check), bundle keys

### E. Changing the roles

1. Role values in the DB (`DEFAULT`, seed rows)
2. `<security-role>` and every `<role-name>` in `web.xml`
3. `isUserInRole('...')` calls in JSPs
4. Nothing in the Realm: it just reads the role column

### F. Changing the business rule

Only the link service changes (the checks and the limit constant) plus the messages. Controllers and JSPs read the limit from the service, so they update by themselves.

### G. Rename vs rewrite summary

| Rename only | Rewrite |
|---|---|
| package, artifact, app, schema, JNDI | columns, SQL strings, `bind`/`map` |
| roles, URL patterns, realm column names | validation rules, business rules |
| JSP labels, bundle values | JSP field layout |
| titles, flash messages | domain logic |

### H. Never touch (copy as is)

`Database.java` (except the JNDI string), all exceptions, `query()/fail()` helpers, the controller structure (GET shows, POST changes + redirect), flash and error display in JSPs, `LogoutController`, `login.jsp`, `login-error.jsp`, `error.jsp`, deploy scripts.

### I. 30-minute drill for exam/practical day

1. Copy template, replace tokens, rename package (5 min)
2. New schema + seed + roles view, run it (5 min)
3. `context.xml` values, `web.xml` roles and URLs (5 min)
4. Deploy and prove login works with the seeded users (5 min)
5. Entity 1: model, DAO, service, controller, JSPs, screenshot (rest)
