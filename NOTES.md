# Jakarta Web App Cheat Sheet (Servlet + JSP + JDBC + MySQL + Tomcat FORM security)

Stack: Maven, Tomcat 11 (Jakarta Servlet 6.1), JSP + JSTL/EL (no scriptlets), DAOs (plain JDBC **or** Jdbi, both shown), MySQL, DataSourceRealm, JSTL fmt for i18n.
Examples use LIBIS (Book, Member, Loan). **Rename** the domain and find/replace `your.pkg` with your root package.

## DAO style: pick one (both are written out)

| | **Style A: plain JDBC** | **Style B: Jdbi** |
|---|---|---|
| Code per entity | interface + `Impl` class (`query`, `bind`, `map`, `fail` helpers) | one interface, SQL inside annotations |
| "PreparedStatement + try-with-resources" rule | you write it, the marker can see it | done inside the library |
| Extra dependency | none | `jdbi3-core` + `jdbi3-sqlobject` |
| Failures | you catch `SQLException`, throw `DataAccessException` | Jdbi throws `JdbiException` (unchecked), mapped in `web.xml` |
| Use it for | CA3, exams, anything that says "plain JDBC" | personal projects, or when your lecturer approves it |

Sections tagged **[A]** and **[B]** show each style. Anything not tagged is the same for both. Services, controllers and JSPs have identical logic in both styles; only how they obtain a DAO changes (end of section 10).

## Contents

1. [Build order (follow this)](#1-build-order-follow-this)
2. [Project structure + pom.xml](#2-project-structure--pomxml)
3. [Tomcat setup + deploy scripts](#3-tomcat-setup--deploy-scripts)
4. [Database (schema, seed, cascade)](#4-database-schema-seed-cascade)
5. [context.xml (DataSource, Realm, Valve)](#5-contextxml-datasource-realm-valve)
6. [Config: JNDI helper](#6-config-jndi-helper)
7. [Models](#7-models)
8. [Exceptions](#8-exceptions)
9. [DAOs](#9-daos)
10. [Services (rules + validation)](#10-services-rules--validation)
11. [Controllers (list, form, delete, loans)](#11-controllers-list-form-delete-loans)
12. [Views (JSP + JSTL)](#12-views-jsp--jstl)
13. [Auth (web.xml, login pages, logout, roles)](#13-auth-webxml-login-pages-logout-roles)
14. [Filters](#14-filters)
15. [i18n (English + Setswana)](#15-i18n-english--setswana)
16. [Rules checklist + evidence screenshots](#16-rules-checklist--evidence-screenshots)
17. [Troubleshooting](#17-troubleshooting)
18. [Adapting this to a different project](#18-adapting-this-to-a-different-project)

---

## 1. Build order (follow this)

1. `pom.xml`, folders, Tomcat copy + connector jar in `lib` (sections 2, 3)
2. `schema.sql` run in Workbench, check data with `SELECT` (4)
3. `META-INF/context.xml` DataSource + Realm (5), `config/Database` (6)
4. Models (7), exceptions (8)
5. DAOs (9), test one with a throwaway `Main` if unsure
6. Services (10)
7. `web.xml` security + login pages + logout (13), **test login now**
8. Controllers + JSPs one entity at a time (11, 12)
9. Filter for no-cache (14)
10. i18n last (15)
11. Screenshot every check as you go (16)

Layer flow: `browser -> Controller -> Service -> DAO -> MySQL`, then `Controller -> forward -> JSP`.
Controller = read input, call service, choose view. Service = rules. DAO = SQL only. JSP = display only.

---

## 2. Project structure + pom.xml

```
src/main
├── java/your/pkg
│   ├── config/       Database.java
│   ├── controller/   *Controller.java
│   ├── dao/          *Dao.java, *DaoImpl.java
│   ├── exception/    DataAccessException, ValidationException, LoanException
│   ├── filter/       NoCacheFilter.java
│   ├── model/        Book, Member, Loan
│   └── service/      BookService, MemberService, LoanService
├── resources/        messages.properties, messages_tn.properties
└── webapp
    ├── META-INF/context.xml
    ├── WEB-INF/
    │   ├── web.xml
    │   ├── error.jsp
    │   ├── jspf/i18n.jspf
    │   └── (all other JSPs here)
    ├── login.jsp            <- only these two outside WEB-INF
    └── login-error.jsp
```

**pom.xml** (Tomcat 10.1: servlet-api `6.0.0`; Tomcat 11: `6.1.0`. Needs Java 17+):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>your.pkg</groupId>
    <artifactId>APPNAME</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>war</packaging>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.release>17</maven.compiler.release>
        <lombok.version>1.18.42</lombok.version>
    </properties>

    <dependencies>
        <!-- provided = compile against it, Tomcat supplies it at runtime -->
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <version>6.1.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- JSTL (core, fmt, functions) -->
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

        <!-- MySQL driver. ALSO copy this jar into TOMCAT/lib (Resource + Realm load it from there) -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>9.7.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- Optional. Delete this + the annotationProcessorPaths block if not using Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>${lombok.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>APPNAME</finalName>
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
- Delete template leftovers: `HelloServlet`, `HelloResource`, `HelloApplication`, `index.jsp`, `beans.xml`, Jersey/Weld dependencies.
- IntelliJ: Settings -> Compiler -> Annotation Processors -> **Enable annotation processing** (for Lombok).
- `maven.compiler.source/target` of 8 breaks the Servlet 6 API. Use `release` 17+.
- Using `provided` for the MySQL jar avoids a duplicate copy in `WEB-INF/lib` (it belongs in Tomcat `lib`).

**[B] Jdbi only: add these** (both Jdbi artifacts must have the same version; `3.54.0` was the newest I found, use `3.53.0` if it will not resolve; Jdbi needs Java 11+). In `<properties>`:

```xml
<jdbi.version>3.54.0</jdbi.version>
```

In `<dependencies>` (compile scope, so they are packaged into the WAR):

```xml
<dependency>
    <groupId>org.jdbi</groupId>
    <artifactId>jdbi3-core</artifactId>
    <version>${jdbi.version}</version>
</dependency>
<dependency>
    <groupId>org.jdbi</groupId>
    <artifactId>jdbi3-sqlobject</artifactId>
    <version>${jdbi.version}</version>
</dependency>
```

A `SLF4J: No SLF4J providers` line in the log is a harmless warning.

---

## 3. Tomcat setup + deploy scripts

**Why not IntelliJ's Run button:** IntelliJ deploys with its own descriptor under a private `CATALINA_BASE`, and Tomcat then **ignores `META-INF/context.xml`**. It also regenerates its `conf` on every run, wiping edits. Result: your Realm is never loaded, Tomcat falls back to `tomcat-users.xml`, and every login fails. A WAR dropped in `webapps` uses `META-INF/context.xml` correctly.

**One-time (PowerShell):**

```powershell
New-Item -ItemType Directory -Force C:\dev | Out-Null
Copy-Item "C:\Program Files\Apache Software Foundation\Tomcat 11.0" C:\dev\tomcat -Recurse
Copy-Item "$env:USERPROFILE\.m2\repository\com\mysql\mysql-connector-j\9.7.0\mysql-connector-j-9.7.0.jar" C:\dev\tomcat\lib
```

(On a school PC: put the jar into whichever Tomcat `lib` the lab uses, if it isn't already there.)

**`start-tomcat.bat`** (project root; change JAVA_HOME to your JDK):

```bat
@echo off
set JAVA_HOME=C:\Users\defaultuser0\.jdks\ms-25.0.4
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

**`deploy.bat`** (project root):

```bat
@echo off
set APP=APPNAME
set TOMCAT=C:\dev\tomcat

call mvnd clean package -q
if errorlevel 1 (
    echo BUILD FAILED
    exit /b 1
)

for %%f in (target\*.war) do copy /Y "%%f" "%TOMCAT%\webapps\%APP%.war"
echo Deployed as /%APP%
```

**Daily loop:** `.\start-tomcat.bat` once (leave it open, it is your log) -> `.\deploy.bat` after each change -> `http://localhost:8080/APPNAME/...`

**Good log lines:** `Deploying web application archive [...APPNAME.war]` then `Deployment ... has finished`, with no `SEVERE`.

Gotchas:
- In PowerShell run scripts as `.\name.bat`.
- Corrupt WAR (`zip END header not found`): delete `webapps\APPNAME.war` and `webapps\APPNAME`, rebuild, start Tomcat first, then deploy. OneDrive folders can lock `target\`; build from `C:\dev\APPNAME` if it keeps happening.
- Redeploy not picking up changes: stop Tomcat, delete `webapps\APPNAME` (folder), start again.
- `mvnd` not found: use `mvn`.
- Port 8080 busy: another Tomcat is running.

---

## 4. Database (schema, seed, cascade)

Rules: PK on every table, `UNIQUE` on natural keys (isbn, email, username, member_number), FKs with `ON DELETE CASCADE`, re-runnable script, availability derived (no stored `available` column).

```sql
DROP SCHEMA IF EXISTS appdb;
CREATE SCHEMA appdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE appdb;

CREATE TABLE members (
    member_id      INT AUTO_INCREMENT PRIMARY KEY,
    member_number  VARCHAR(20)  NOT NULL UNIQUE,
    full_name      VARCHAR(100) NOT NULL,
    email          VARCHAR(100) NOT NULL UNIQUE,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password       VARCHAR(100) NOT NULL,
    role           ENUM('member','librarian') NOT NULL DEFAULT 'member'
);

CREATE TABLE books (
    book_id         INT AUTO_INCREMENT PRIMARY KEY,
    isbn            VARCHAR(20)  NOT NULL UNIQUE,
    title           VARCHAR(150) NOT NULL,
    author          VARCHAR(100) NOT NULL,
    genre           VARCHAR(50),
    synopsis        TEXT,
    published_year  INT
);

-- many-to-many resolver; deleting a member or book deletes their loans automatically
CREATE TABLE loans (
    loan_id      INT AUTO_INCREMENT PRIMARY KEY,
    member_id    INT  NOT NULL,
    book_id      INT  NOT NULL,
    borrowed_on  DATE NOT NULL,
    returned_on  DATE NULL,
    CONSTRAINT fk_loans_member FOREIGN KEY (member_id) REFERENCES members(member_id) ON DELETE CASCADE,
    CONSTRAINT fk_loans_book   FOREIGN KEY (book_id)   REFERENCES books(book_id)     ON DELETE CASCADE,
    INDEX idx_loans_book_open (book_id, returned_on)
);

-- DataSourceRealm needs a (username, role) table. A view over members.role avoids a second table.
-- The username column MUST be named the same as members.username (realm userNameCol is used for both tables).
CREATE VIEW member_roles AS
    SELECT username, role AS role_name FROM members;
```

Seed example:

```sql
INSERT INTO members (member_number, full_name, email, username, password, role) VALUES
('LIB-001','Thobo Marema','thobot@gmail.com','thobo','password12345','member'),
('LIB-006','Lerato Molefe','librarian@libis.bw','librarian','librarian123','librarian');

INSERT INTO books (isbn, title, author, genre, synopsis, published_year) VALUES
('978-0-00-000001-1','The Hobbit','J.R.R. Tolkien','Fantasy','A hobbit joins a quest.',1937);

-- last loan row open (returned_on NULL) = book on loan
INSERT INTO loans (member_id, book_id, borrowed_on, returned_on) VALUES
(1, 1, '2026-10-10', NULL);
```

Seed so you can demo everything: one member with 3 open loans (limit), one member with returned + open loans (cascade demo), at least 8 loans total.

**Availability by query (no stored column):**

```sql
SELECT b.*,
       NOT EXISTS (SELECT 1 FROM loans l WHERE l.book_id = b.book_id AND l.returned_on IS NULL) AS available
FROM books b;
```

Cascade demo for evidence: `SELECT * FROM loans;` before, delete a member through the app, `SELECT * FROM loans;` after. Their rows are gone, their open books show Available again.

Typo trap: a misspelled schema name (`minilibs`) gives `Unknown database`. Run the whole script (lightning bolt) and keep the `USE` line.

---

## 5. context.xml (DataSource, Realm, Valve)

`src/main/webapp/META-INF/context.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Context>

    <!-- Pooled, container-managed DataSource (looked up by JNDI name jdbc/appdb) -->
    <Resource name="jdbc/appdb"
              auth="Container"
              type="javax.sql.DataSource"
              driverClassName="com.mysql.cj.jdbc.Driver"
              url="jdbc:mysql://localhost:3306/appdb?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC"
              username="root"
              password="YOUR_MYSQL_PASSWORD"
              maxTotal="20"
              maxIdle="10"/>

    <!-- Authenticates against YOUR tables, not tomcat-users.xml (JDBCRealm is removed in current Tomcat) -->
    <Realm className="org.apache.catalina.realm.DataSourceRealm"
           dataSourceName="jdbc/appdb"
           localDataSource="true"
           userTable="members"
           userNameCol="username"
           userCredCol="password"
           userRoleTable="member_roles"
           roleNameCol="role_name"/>

    <!-- Lets a user who opens login.jsp directly land somewhere after login (otherwise HTTP 400) -->
    <Valve className="org.apache.catalina.authenticator.FormAuthenticator"
           landingPage="/catalogue"/>

</Context>
```

Notes:
- `&` must be written `&amp;` in XML.
- `localDataSource="true"` makes the realm use this app's own DataSource.
- Plain-text passwords work with no CredentialHandler. Hashed passwords need `<CredentialHandler className="org.apache.catalina.realm.MessageDigestCredentialHandler" algorithm="SHA-256"/>` inside the Realm.
- Tomcat only reads this file when the WAR is deployed from `webapps` (section 3).
- **LockOutRealm:** 5 failed logins for a username locks it for 5 minutes (even with the right password). Restart Tomcat or wait. The log shows `attempt was made to authenticate the locked user`.
- If you see no log output and login always fails, the Realm is not being loaded. Test: temporarily set `userTable="members_x"`. A `SEVERE` DataSourceRealm error in the log means the realm is active.

---

## 6. Config: JNDI helper

Both styles read the same pooled DataSource from `context.xml`. Only what `Database` hands out differs.

### [A] Plain JDBC: `config/Database.java`

```java
package your.pkg.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Gives DAOs a connection from the container-managed pool defined in context.xml. */
public final class Database {

    private static DataSource dataSource;

    private Database() {}

    /** Borrows a pooled connection. close() returns it to the pool, so always use try-with-resources. */
    public static Connection getConnection() throws SQLException {
        return lookup().getConnection();
    }

    private static synchronized DataSource lookup() throws SQLException {
        if (dataSource == null) {
            try {
                Context env = (Context) new InitialContext().lookup("java:comp/env");
                dataSource = (DataSource) env.lookup("jdbc/appdb");     // same name as <Resource name=...>
            } catch (NamingException e) {
                throw new SQLException("DataSource jdbc/appdb not found", e);
            }
        }
        return dataSource;
    }
}
```

`Name [jdbc/appdb] is not bound` in the log means context.xml was not applied (section 3).

### [B] Jdbi: `config/Database.java`

Builds one `Jdbi` from the same JNDI DataSource and creates DAO objects from your interfaces.

```java
package your.pkg.config;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import your.pkg.exception.DataAccessException;

/** Builds one Jdbi from the pooled DataSource in context.xml and creates DAOs from interfaces. */
public final class Database {

    private static Jdbi jdbi;

    private Database() {}

    /**
     * Returns a ready-to-use implementation of a DAO interface.
     * "onDemand" means every method call borrows a pooled connection and gives it back afterwards,
     * so there is no connection code to write or close.
     */
    public static <T> T dao(Class<T> daoType) {
        return jdbi().onDemand(daoType);
    }

    private static synchronized Jdbi jdbi() {
        if (jdbi == null) {
            try {
                Context env = (Context) new InitialContext().lookup("java:comp/env");
                DataSource dataSource = (DataSource) env.lookup("jdbc/appdb");   // same name as <Resource name>
                jdbi = Jdbi.create(dataSource);
                jdbi.installPlugin(new SqlObjectPlugin());                        // enables annotation-based DAOs
            } catch (NamingException e) {
                throw new DataAccessException("DataSource jdbc/appdb not found", e);
            }
        }
        return jdbi;
    }
}
```

---

## 7. Models

A model is one row as a Java object: private fields, getters/setters, empty constructor. EL (`${book.title}`) calls `getTitle()`, so a missing getter means a blank page.

**Plain Java**:

```java
package your.pkg.model;

public class Book {
    private int bookId;
    private String isbn;
    private String title;
    private String auther;           // LIBIS keeps this spelling; field/property names must match your JSPs
    private String genre;
    private String synopsis;
    private Integer publishedYear;   // Integer (not int) because the column can be NULL
    private Boolean available;       // filled by the query, not stored

    public Book() {}

    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuther() { return auther; }
    public void setAuther(String auther) { this.auther = auther; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }
    public Integer getPublishedYear() { return publishedYear; }
    public void setPublishedYear(Integer publishedYear) { this.publishedYear = publishedYear; }
    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }
}
```

**Lombok version** (generates the same methods at compile time):

```java
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @NoArgsConstructor @ToString
public class Book {
    private int bookId;
    private String isbn;
    private String title;
    private Integer publishedYear;
    private Boolean available;
}
```

Lombok notes: a primitive `boolean x` gets `isX()`, a `Boolean x` gets `getX()`. Exclude passwords from `@ToString` (`@ToString(exclude = "password")`).

`Member`: `memberId, memberNumber, fullName, email, username, password, role`. If old code calls `getFirstName()`, derive it:

```java
public String getFirstName() {
    if (fullName == null || fullName.trim().isEmpty()) return "";   // isBlank() needs Java 11+
    return fullName.trim().split("\\s+")[0];
}
```

`Loan`: `loanId, memberId, bookId, bookTitle (for display), LocalDate borrowedOn, LocalDate returnedOn`.

---

## 8. Exceptions

```java
package your.pkg.exception;

/** Unchecked. The database itself failed. Travels to error.jsp via web.xml, never to the user as a stack trace. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
```

```java
package your.pkg.exception;

import java.util.Collections;
import java.util.Map;

/** Checked. User input broke a rule. Carries field -> message so the form shows every error. */
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
package your.pkg.exception;

/** Checked. A business rule (loan rules) was broken. The message is shown to the user. */
public class LoanException extends Exception {
    public LoanException(String reason) { super(reason); }
}
```

Why two styles: checked = expected, the caller must handle it (user typed rubbish). Unchecked = unexpected, nothing a controller can fix.

Map unchecked exceptions in `web.xml` (section 13).

---

## 9. DAOs

The same operations in both styles:

| Task | [A] plain JDBC | [B] Jdbi |
|---|---|---|
| Run a SELECT | `query(SELECT + " WHERE ...", params)` | `@SqlQuery(SELECT + " WHERE ...")` on an interface method |
| Pass a value | `?` + `ps.setObject(i + 1, value)` | `:name` + `@Bind("name")` |
| Pass a whole object | `bind(ps, book)` with numbered `setX` calls | `@BindBean Book book` and `:isbn`, `:title`... |
| Row to object | `map(rs)` | `@RegisterBeanMapper(Book.class)` (`book_id` matches `bookId` by itself; use `AS` if names differ) |
| Search wildcards | `"%" + term.trim() + "%"` as the bound value | `default search(...)` calling `searchLike("%" + ... + "%")` |
| Generated id | `RETURN_GENERATED_KEYS` + `getGeneratedKeys()` | `@GetGeneratedKeys("book_id")` + `default create(...)` |
| NULL `Integer` | `ps.setObject(4, v, Types.INTEGER)` | automatic for `Integer` properties |
| Close resources | try-with-resources | automatic (`onDemand`) |
| Failure | catch `SQLException`, log, throw `DataAccessException` | `JdbiException` (unchecked), mapped to `error.jsp` in `web.xml` |
| Files per entity | `XDao` + `XDaoImpl` | `XDao` only |

Services, controllers and JSPs do not change between the styles: the method signatures are the same and neither throws a checked exception.

### [A] Plain JDBC

Pattern: interface + implementation. SQL only. `PreparedStatement` for every query, `?` placeholders, `%` wildcards added to the **bound value**, try-with-resources on `Connection`, `PreparedStatement` and `ResultSet`. Log the `SQLException` and throw `DataAccessException`.

**Interface**:

```java
package your.pkg.dao;

import java.util.List;
import your.pkg.model.Book;

public interface BookDao {
    List<Book> findAll();
    Book findById(int bookId);          // null if not found
    Book findByIsbn(String isbn);       // null if not found (duplicate check)
    List<Book> search(String term);     // title OR author
    void create(Book book);             // sets the generated id on the book
    void update(Book book);
    void delete(int bookId);
}
```

**Implementation**:

```java
package your.pkg.dao;

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

import your.pkg.config.Database;
import your.pkg.exception.DataAccessException;
import your.pkg.model.Book;

public class BookDaoImpl implements BookDao {

    private static final Logger LOG = Logger.getLogger(BookDaoImpl.class.getName());

    // availability is computed here, not stored
    private static final String SELECT =
            "SELECT b.book_id, b.isbn, b.title, b.author, b.genre, b.synopsis, b.published_year, "
          + "NOT EXISTS (SELECT 1 FROM loans l WHERE l.book_id = b.book_id AND l.returned_on IS NULL) AS available "
          + "FROM books b";

    @Override public List<Book> findAll() { return query(SELECT + " ORDER BY b.book_id"); }

    @Override public Book findById(int id) { return first(query(SELECT + " WHERE b.book_id = ?", id)); }

    @Override public Book findByIsbn(String isbn) { return first(query(SELECT + " WHERE b.isbn = ?", isbn)); }

    @Override
    public List<Book> search(String term) {
        String like = "%" + term.trim() + "%";            // wildcards on the VALUE, not in the SQL string
        return query(SELECT + " WHERE b.title LIKE ? OR b.author LIKE ? ORDER BY b.book_id", like, like);
    }

    @Override
    public void create(Book b) {
        String sql = "INSERT INTO books (isbn, title, author, genre, synopsis, published_year) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, b);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) b.setBookId(keys.getInt(1));
            }
        } catch (SQLException e) { throw fail("Could not create book", e); }
    }

    @Override
    public void update(Book b) {
        String sql = "UPDATE books SET isbn=?, title=?, author=?, genre=?, synopsis=?, published_year=? WHERE book_id=?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, b);
            ps.setInt(7, b.getBookId());
            ps.executeUpdate();
        } catch (SQLException e) { throw fail("Could not update book", e); }
    }

    @Override
    public void delete(int bookId) {
        // loans for this book go with it via ON DELETE CASCADE (do not delete them in Java)
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM books WHERE book_id = ?")) {
            ps.setInt(1, bookId);
            ps.executeUpdate();
        } catch (SQLException e) { throw fail("Could not delete book", e); }
    }

    private void bind(PreparedStatement ps, Book b) throws SQLException {
        ps.setString(1, b.getIsbn());
        ps.setString(2, b.getTitle());
        ps.setString(3, b.getAuther());
        ps.setString(4, b.getGenre());
        ps.setString(5, b.getSynopsis());
        ps.setObject(6, b.getPublishedYear(), Types.INTEGER);   // writes NULL when null
    }

    /** One helper for every SELECT: binds ? in order, maps rows to objects. */
    private List<Book> query(String sql, Object... params) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);   // parameters start at 1
            try (ResultSet rs = ps.executeQuery()) {
                List<Book> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) { throw fail("Could not read books", e); }
    }

    private Book first(List<Book> list) { return list.isEmpty() ? null : list.get(0); }

    private Book map(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.setBookId(rs.getInt("book_id"));
        b.setIsbn(rs.getString("isbn"));
        b.setTitle(rs.getString("title"));
        b.setAuther(rs.getString("author"));
        b.setGenre(rs.getString("genre"));
        b.setSynopsis(rs.getString("synopsis"));
        b.setPublishedYear(rs.getObject("published_year", Integer.class));   // keeps NULL as null
        b.setAvailable(rs.getBoolean("available"));
        return b;
    }

    private DataAccessException fail(String message, SQLException e) {
        LOG.log(Level.SEVERE, message, e);
        return new DataAccessException(message, e);
    }
}
```

**MemberDao**: same pattern. Methods: `findAll, findById, findByUsername, findByMemberNumber, findByEmail, search(term) (full_name LIKE ? OR member_number LIKE ?), create, update, delete`.

**LoanDao**:

```java
public interface LoanDao {
    void create(int memberId, int bookId);                // borrowed_on = CURDATE()
    boolean markReturned(int memberId, int bookId);       // UPDATE ... SET returned_on = CURDATE() WHERE ... AND returned_on IS NULL
    int countOpenByMember(int memberId);
    List<Loan> findOpenByMember(int memberId);
    List<Loan> findHistoryByMember(int memberId);         // returned_on IS NOT NULL
    boolean hasOpenLoan(int bookId);
}
```

Key SQL:

```java
// borrow
"INSERT INTO loans (member_id, book_id, borrowed_on) VALUES (?, ?, CURDATE())"
// return = set a date, never delete the row (it becomes history)
"UPDATE loans SET returned_on = CURDATE() WHERE member_id = ? AND book_id = ? AND returned_on IS NULL"   // executeUpdate() > 0
// count open loans
"SELECT COUNT(*) FROM loans WHERE member_id = ? AND returned_on IS NULL"
// open loans with title
"SELECT l.loan_id, l.member_id, l.book_id, b.title, l.borrowed_on, l.returned_on "
+ "FROM loans l JOIN books b ON b.book_id = l.book_id WHERE l.member_id = ? AND l.returned_on IS NULL ORDER BY l.borrowed_on"
// map dates (null-safe)
l.setBorrowedOn(rs.getObject("borrowed_on", LocalDate.class));
l.setReturnedOn(rs.getObject("returned_on", LocalDate.class));
```

Never concatenate request values into SQL. `executeQuery` returns rows, `executeUpdate` returns rows changed.

### [B] Jdbi

No `Impl` classes (delete any you have). Each DAO is an interface:
- `@SqlQuery` / `@SqlUpdate` hold the SQL. `:name` placeholders become prepared-statement parameters.
- `@Bind("id")` fills `:id`. `@BindBean` fills `:isbn`, `:title`... from the object's getters.
- `@RegisterBeanMapper(Book.class)` turns each row into a `Book`.
- A `default` method is ordinary Java inside the interface (wildcards, writing the new id back).
- If your IDE shows red imports, press Alt+Enter. They live in `org.jdbi.v3.sqlobject.config`, `.customizer` and `.statement`.

**`BookDao`**:

```java
package your.pkg.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import your.pkg.model.Book;

public interface BookDao {

    // Shared column list. "author AS auther" because the Book property is spelled auther.
    // "available" is computed from open loans, not stored.
    String SELECT =
            "SELECT b.book_id, b.isbn, b.title, b.author AS auther, b.genre, b.synopsis, b.published_year, "
          + "NOT EXISTS (SELECT 1 FROM loans l WHERE l.book_id = b.book_id AND l.returned_on IS NULL) AS available "
          + "FROM books b";

    @SqlQuery(SELECT + " ORDER BY b.book_id")
    @RegisterBeanMapper(Book.class)
    List<Book> findAll();

    /** null when no row matches. */
    @SqlQuery(SELECT + " WHERE b.book_id = :id")
    @RegisterBeanMapper(Book.class)
    Book findById(@Bind("id") int id);

    /** null when no row matches. Used for the duplicate-ISBN check. */
    @SqlQuery(SELECT + " WHERE b.isbn = :isbn")
    @RegisterBeanMapper(Book.class)
    Book findByIsbn(@Bind("isbn") String isbn);

    @SqlQuery(SELECT + " WHERE b.title LIKE :like OR b.author LIKE :like ORDER BY b.book_id")
    @RegisterBeanMapper(Book.class)
    List<Book> searchLike(@Bind("like") String like);

    /** Wildcards are added to the VALUE, never concatenated into the SQL text. */
    default List<Book> search(String term) {
        return searchLike("%" + term.trim() + "%");
    }

    @SqlUpdate("INSERT INTO books (isbn, title, author, genre, synopsis, published_year) "
             + "VALUES (:isbn, :title, :auther, :genre, :synopsis, :publishedYear)")
    @GetGeneratedKeys("book_id")
    int insert(@BindBean Book book);

    /** Inserts and writes the new auto-increment id back into the book. */
    default void create(Book book) {
        book.setBookId(insert(book));
    }

    @SqlUpdate("UPDATE books SET isbn = :isbn, title = :title, author = :auther, genre = :genre, "
             + "synopsis = :synopsis, published_year = :publishedYear WHERE book_id = :bookId")
    void update(@BindBean Book book);

    /** Loan rows for this book are removed by ON DELETE CASCADE. */
    @SqlUpdate("DELETE FROM books WHERE book_id = :id")
    void delete(@Bind("id") int id);
}
```

**`MemberDao`**:

```java
package your.pkg.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import your.pkg.model.Member;

public interface MemberDao {

    String SELECT =
            "SELECT member_id, member_number, full_name, email, username, password, role FROM members";

    @SqlQuery(SELECT + " ORDER BY member_id")
    @RegisterBeanMapper(Member.class)
    List<Member> findAll();

    @SqlQuery(SELECT + " WHERE member_id = :id")
    @RegisterBeanMapper(Member.class)
    Member findById(@Bind("id") int id);

    @SqlQuery(SELECT + " WHERE username = :username")
    @RegisterBeanMapper(Member.class)
    Member findByUsername(@Bind("username") String username);

    @SqlQuery(SELECT + " WHERE member_number = :memberNumber")
    @RegisterBeanMapper(Member.class)
    Member findByMemberNumber(@Bind("memberNumber") String memberNumber);

    @SqlQuery(SELECT + " WHERE email = :email")
    @RegisterBeanMapper(Member.class)
    Member findByEmail(@Bind("email") String email);

    @SqlQuery(SELECT + " WHERE full_name LIKE :like OR member_number LIKE :like ORDER BY member_id")
    @RegisterBeanMapper(Member.class)
    List<Member> searchLike(@Bind("like") String like);

    default List<Member> search(String term) {
        return searchLike("%" + term.trim() + "%");
    }

    @SqlUpdate("INSERT INTO members (member_number, full_name, email, username, password, role) "
             + "VALUES (:memberNumber, :fullName, :email, :username, :password, :role)")
    @GetGeneratedKeys("member_id")
    int insert(@BindBean Member member);

    default void create(Member member) {
        member.setMemberId(insert(member));
    }

    @SqlUpdate("UPDATE members SET member_number = :memberNumber, full_name = :fullName, email = :email, "
             + "username = :username, password = :password, role = :role WHERE member_id = :memberId")
    void update(@BindBean Member member);

    /** Their loans are removed by ON DELETE CASCADE. */
    @SqlUpdate("DELETE FROM members WHERE member_id = :id")
    void delete(@Bind("id") int id);
}
```

**`LoanDao`**:

```java
package your.pkg.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import your.pkg.model.Loan;

public interface LoanDao {

    // "title AS bookTitle" fills Loan.bookTitle for display
    String SELECT_LOANS =
            "SELECT l.loan_id, l.member_id, l.book_id, b.title AS bookTitle, l.borrowed_on, l.returned_on "
          + "FROM loans l JOIN books b ON b.book_id = l.book_id ";

    @SqlUpdate("INSERT INTO loans (member_id, book_id, borrowed_on) VALUES (:memberId, :bookId, CURDATE())")
    void create(@Bind("memberId") int memberId, @Bind("bookId") int bookId);

    /** Rows changed by the return. Zero means this member had no open loan of that book. */
    @SqlUpdate("UPDATE loans SET returned_on = CURDATE() "
             + "WHERE member_id = :memberId AND book_id = :bookId AND returned_on IS NULL")
    int markReturnedRows(@Bind("memberId") int memberId, @Bind("bookId") int bookId);

    default boolean markReturned(int memberId, int bookId) {
        return markReturnedRows(memberId, bookId) > 0;
    }

    @SqlQuery("SELECT COUNT(*) FROM loans WHERE member_id = :memberId AND returned_on IS NULL")
    int countOpenByMember(@Bind("memberId") int memberId);

    @SqlQuery(SELECT_LOANS + "WHERE l.member_id = :memberId AND l.returned_on IS NULL ORDER BY l.borrowed_on")
    @RegisterBeanMapper(Loan.class)
    List<Loan> findOpenByMember(@Bind("memberId") int memberId);

    @SqlQuery(SELECT_LOANS + "WHERE l.member_id = :memberId AND l.returned_on IS NOT NULL ORDER BY l.returned_on DESC")
    @RegisterBeanMapper(Loan.class)
    List<Loan> findHistoryByMember(@Bind("memberId") int memberId);

    @SqlQuery("SELECT EXISTS (SELECT 1 FROM loans WHERE book_id = :bookId AND returned_on IS NULL)")
    boolean hasOpenLoan(@Bind("bookId") int bookId);
}
```

Jdbi gotchas:
- Column and property names must match (`author` vs `auther`): alias in the SQL (`b.author AS auther`) or the field stays `null`.
- The `:name` in `@BindBean` SQL must match a getter (`:publishedYear` for `getPublishedYear()`), or you get `Missing named parameter`.
- Dates map to `LocalDate` automatically; `Integer`/`Boolean` wrappers handle NULLs.
- The interface must be `public`, and Jdbi needs the `SqlObjectPlugin` (installed in `Database`).

---

## 10. Services (rules + validation)

Services own the rules and call DAOs. Controllers never decide rules.

**BookService**:

```java
package your.pkg.service;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import your.pkg.dao.BookDao;
import your.pkg.dao.BookDaoImpl;
import your.pkg.dao.LoanDao;
import your.pkg.dao.LoanDaoImpl;
import your.pkg.exception.ValidationException;
import your.pkg.model.Book;

public class BookService {

    private final BookDao bookDao;
    private final LoanDao loanDao;

    public BookService() { this(new BookDaoImpl(), new LoanDaoImpl()); }

    // constructor injection: a test can pass fake DAOs (no database needed)
    public BookService(BookDao bookDao, LoanDao loanDao) {
        this.bookDao = bookDao;
        this.loanDao = loanDao;
    }

    public List<Book> list(String search) {
        if (search == null || search.trim().isEmpty()) return bookDao.findAll();
        return bookDao.search(search.trim());
    }

    public Book get(int id) { return bookDao.findById(id); }

    public void add(Book b) throws ValidationException {
        tidy(b);
        validate(b);
        bookDao.create(b);
    }

    public void edit(Book b) throws ValidationException {
        tidy(b);
        validate(b);
        bookDao.update(b);
    }

    /** Rule: a book currently on loan cannot be deleted. Past loan rows go via ON DELETE CASCADE. */
    public void remove(int bookId) throws ValidationException {
        Book b = bookDao.findById(bookId);
        if (b == null) return;
        if (loanDao.hasOpenLoan(bookId)) {
            Map<String, String> e = new LinkedHashMap<>();
            e.put("general", b.getTitle() + " cannot be deleted because it is currently on loan.");
            throw new ValidationException(e);
        }
        bookDao.delete(bookId);
    }

    private void tidy(Book b) {                                  // trim before validating and saving
        if (b.getIsbn() != null)   b.setIsbn(b.getIsbn().trim());
        if (b.getTitle() != null)  b.setTitle(b.getTitle().trim());
        if (b.getAuther() != null) b.setAuther(b.getAuther().trim());
    }

    /** Collects ALL errors (not just the first) so the form can show them together. */
    private void validate(Book b) throws ValidationException {
        Map<String, String> errors = new LinkedHashMap<>();

        if (blank(b.getIsbn())) errors.put("isbn", "ISBN is required.");
        else if (b.getIsbn().length() > 20) errors.put("isbn", "ISBN must be 20 characters or fewer.");
        else {
            Book other = bookDao.findByIsbn(b.getIsbn());
            // a DIFFERENT row with the same ISBN (editing a book must not clash with itself)
            if (other != null && other.getBookId() != b.getBookId())
                errors.put("isbn", "A book with this ISBN already exists.");
        }

        if (blank(b.getTitle()))  errors.put("title", "Title is required.");
        if (blank(b.getAuther())) errors.put("auther", "Author is required.");

        Integer year = b.getPublishedYear();
        if (year != null && (year < 1000 || year > Year.now().getValue()))
            errors.put("publishedYear", "Year must be between 1000 and " + Year.now().getValue() + ".");

        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
```

**MemberService**: same shape, plus:

```java
private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

// required: memberNumber, fullName, email, username (+ password on create)
// format:   if (!EMAIL.matcher(m.getEmail()).matches()) errors.put("email", "Enter a valid email address.");
// unique:   memberNumber, email, username each via findByX; clash only if other.getMemberId() != m.getMemberId()
// edit:     blank password = keep the stored one:
//           if (blank(m.getPassword())) m.setPassword(memberDao.findById(m.getMemberId()).getPassword());
// delete:   controller passes the current member's id; reject if equal ("You cannot delete the account you are signed in with.")
```

**LoanService** (all borrowing rules in one place):

```java
package your.pkg.service;

import java.util.List;

import your.pkg.dao.*;
import your.pkg.exception.LoanException;
import your.pkg.model.Book;
import your.pkg.model.Loan;

public class LoanService {

    public static final int MAX_LOANS = 3;              // defined ONCE (controllers and JSPs read it from here)

    private final BookDao bookDao = new BookDaoImpl();
    private final LoanDao loanDao = new LoanDaoImpl();

    public void borrow(int memberId, int bookId) throws LoanException {
        Book book = bookDao.findById(bookId);
        if (book == null)
            throw new LoanException("That book does not exist.");
        if (!Boolean.TRUE.equals(book.getAvailable()))
            throw new LoanException(book.getTitle() + " cannot be borrowed because it is already on loan.");
        if (loanDao.countOpenByMember(memberId) >= MAX_LOANS)
            throw new LoanException("You already have " + MAX_LOANS
                    + " books on loan, the maximum allowed. Return a book before borrowing another.");
        loanDao.create(memberId, bookId);                // borrowed_on = today
    }

    public void giveBack(int memberId, int bookId) throws LoanException {
        if (!loanDao.markReturned(memberId, bookId))     // sets returned_on, only if on loan to THIS member
            throw new LoanException("That book is not currently on loan to you.");
    }

    public List<Loan> current(int memberId) { return loanDao.findOpenByMember(memberId); }
    public List<Loan> history(int memberId) { return loanDao.findHistoryByMember(memberId); }
    public int openCount(int memberId)      { return loanDao.countOpenByMember(memberId); }
}
```

### Getting a DAO: services and controllers ([A] vs [B])

The only difference between the styles above the DAO layer is how a DAO object is obtained.

**`BookService` constructor:**

```java
// [A] plain JDBC
public BookService() { this(new BookDaoImpl(), new LoanDaoImpl()); }

// [B] Jdbi   (import your.pkg.config.Database;  no *DaoImpl imports)
public BookService() { this(Database.dao(BookDao.class), Database.dao(LoanDao.class)); }
```

**`MemberService`:** `new MemberDaoImpl()` becomes `Database.dao(MemberDao.class)`.

**`LoanService` fields:**

```java
// [A]
private final BookDao bookDao = new BookDaoImpl();
private final LoanDao loanDao = new LoanDaoImpl();

// [B]
private final BookDao bookDao = Database.dao(BookDao.class);
private final LoanDao loanDao = Database.dao(LoanDao.class);
```

**Controllers** (anywhere you wrote `new MemberDaoImpl()`):

```java
// [A]
private final MemberDao memberDao = new MemberDaoImpl();

// [B]
private final MemberDao memberDao = Database.dao(MemberDao.class);
```

Switching styles later: replace every `new XDaoImpl()` with `Database.dao(XDao.class)` (or back), swap `Database.java` and the DAO files, and add/remove the Jdbi dependencies and the `JdbiException` error-page. Nothing else changes.

---

## 11. Controllers (list, form, delete, loans)

Rules: GET reads/displays, **POST changes data**, every successful POST **redirects** (Post/Redirect/Get). Views live in `WEB-INF` and are reached only by `forward`. Find the user with `request.getRemoteUser()`, never from a form field.

**Who is signed in** (use in any controller):

```java
Member me = memberDao.findByUsername(request.getRemoteUser());   // container-verified username
```

**List + search (GET)**:

```java
@WebServlet(name = "BookListController", urlPatterns = "/librarian/books")
public class BookListController extends HttpServlet {

    private final BookService bookService = new BookService();   // stateless, safe to share across threads

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String search = request.getParameter("search");
        request.setAttribute("books", bookService.list(search));
        request.setAttribute("search", search);
        request.getRequestDispatcher("/WEB-INF/librarian/books.jsp").forward(request, response);
    }
}
```

**Add/edit form** (GET shows the form, POST saves):

```java
@WebServlet(name = "BookFormController", urlPatterns = "/librarian/books/form")
public class BookFormController extends HttpServlet {

    private final BookService bookService = new BookService();

    /** No id = empty add form. ?id=5 = edit form pre-filled with the current values. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Book book = new Book();
        String id = request.getParameter("id");
        if (id != null && !id.trim().isEmpty()) {
            try {
                book = bookService.get(Integer.parseInt(id.trim()));
            } catch (NumberFormatException e) {
                book = null;
            }
            if (book == null) {
                response.sendRedirect(request.getContextPath() + "/librarian/books");
                return;
            }
        }
        request.setAttribute("book", book);
        request.getRequestDispatcher("/WEB-INF/librarian/book-form.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Book book = new Book();
        Map<String, String> errors = new LinkedHashMap<>();

        book.setBookId(parseInt(request.getParameter("bookId"), 0));      // hidden field; 0 = new
        book.setIsbn(request.getParameter("isbn"));
        book.setTitle(request.getParameter("title"));
        book.setAuther(request.getParameter("auther"));
        book.setGenre(request.getParameter("genre"));
        book.setSynopsis(request.getParameter("synopsis"));

        String year = request.getParameter("publishedYear");
        if (year != null && !year.trim().isEmpty()) {
            try {
                book.setPublishedYear(Integer.parseInt(year.trim()));
            } catch (NumberFormatException e) {                           // "20x4" becomes a form error, not a 500
                errors.put("publishedYear", "Year must be a whole number.");
            }
        }

        if (errors.isEmpty()) {
            try {
                if (book.getBookId() == 0) bookService.add(book);
                else bookService.edit(book);

                request.getSession().setAttribute("flash", "Book saved.");   // shown once on the list page
                response.sendRedirect(request.getContextPath() + "/librarian/books");   // Post/Redirect/Get
                return;
            } catch (ValidationException e) {
                errors.putAll(e.getErrors());
            }
        }

        request.setAttribute("errors", errors);        // JSP shows each message
        request.setAttribute("book", book);            // JSP re-fills the typed values
        request.getRequestDispatcher("/WEB-INF/librarian/book-form.jsp").forward(request, response);
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }
}
```

**Delete with confirmation** (GET = confirm page, POST = do it; never delete from a GET link):

```java
@WebServlet(name = "BookDeleteController", urlPatterns = "/librarian/books/delete")
public class BookDeleteController extends HttpServlet {

    private final BookService bookService = new BookService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            Book book = bookService.get(Integer.parseInt(request.getParameter("id")));
            if (book != null) {
                request.setAttribute("book", book);
                request.getRequestDispatcher("/WEB-INF/librarian/book-delete.jsp").forward(request, response);
                return;
            }
        } catch (NumberFormatException ignored) { }
        response.sendRedirect(request.getContextPath() + "/librarian/books");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            int id = Integer.parseInt(request.getParameter("id"));
            Book book = bookService.get(id);
            bookService.remove(id);
            request.getSession().setAttribute("flash",
                    book == null ? "Book deleted." : book.getTitle() + " has been deleted.");
        } catch (ValidationException e) {
            request.getSession().setAttribute("flashError", e.getErrors().get("general"));
        } catch (NumberFormatException ignored) { }
        response.sendRedirect(request.getContextPath() + "/librarian/books");
    }
}
```

**Member delete** (extra: block self-delete, show the cascade message):

```java
Member me = memberDao.findByUsername(request.getRemoteUser());
Member target = memberService.get(id);
if (target != null && target.getMemberId() == me.getMemberId()) {
    session.setAttribute("flashError", "You cannot delete the account you are currently signed in with.");
} else if (target != null) {
    memberService.remove(id);        // the FK cascade deletes their loans
    session.setAttribute("flash", "Member " + target.getMemberNumber() + " (" + target.getFullName()
            + ") and their borrowing history have been deleted.");
}
response.sendRedirect(request.getContextPath() + "/librarian/members");
```

**Borrow / Return / My Loans** (member only):

```java
@WebServlet(name = "BorrowController", urlPatterns = "/borrow")
public class BorrowController extends HttpServlet {

    private final LoanService loanService = new LoanService();
    private final MemberDao memberDao = new MemberDaoImpl();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Member me = memberDao.findByUsername(request.getRemoteUser());     // identity from the container
        try {
            int bookId = Integer.parseInt(request.getParameter("bookId"));
            loanService.borrow(me.getMemberId(), bookId);
            request.getSession().setAttribute("flash", "Book borrowed.");
        } catch (LoanException e) {
            request.getSession().setAttribute("flashError", e.getMessage());   // survives the redirect
        } catch (NumberFormatException e) {
            request.getSession().setAttribute("flashError", "Invalid book.");
        }
        response.sendRedirect(request.getContextPath() + "/catalogue");
    }
}
```

`/return` is the same with `loanService.giveBack(...)` redirecting to `/loans`. `/loans` (GET) loads `current`, `history`, `openCount`, and `MAX_LOANS`, then forwards to `/WEB-INF/loans.jsp`. Expose the limit without repeating the number: `request.setAttribute("maxLoans", LoanService.MAX_LOANS);`.

**Catalogue** (both roles): unchanged shape, only the data source swaps:

```java
List<Book> result = (search == null || search.trim().isEmpty()) ? bookDao.findAll() : bookDao.search(search.trim());
request.setAttribute("books", result);
request.setAttribute("search", search);
request.getRequestDispatcher("/WEB-INF/welcome.jsp").forward(request, response);
```

---

## 12. Views (JSP + JSTL)

Rules: JSTL/EL only (**no `<% %>`**), every printed value through `<c:out>` or `fn:escapeXml`, all pages except `login.jsp` and `login-error.jsp` under `WEB-INF`, UTF-8 page directive.

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

**Table with search + empty state**:

```jsp
<form method="get" action="<c:url value='/catalogue'/>" class="d-flex gap-2 mb-3">
    <input type="text" name="search" class="form-control" value="<c:out value='${search}'/>">
    <button type="submit" class="btn btn-primary">Search</button>
</form>

<c:choose>
    <c:when test="${empty books}"><div class="alert alert-info">No books found.</div></c:when>
    <c:otherwise>
        <table class="table table-striped">
            <thead><tr><th>Title</th><th>Author</th><th>Status</th><th></th></tr></thead>
            <tbody>
            <c:forEach var="b" items="${books}">
                <tr>
                    <td><c:out value="${b.title}"/></td>
                    <td><c:out value="${b.auther}"/></td>
                    <td><c:out value="${b.available ? 'Available' : 'On loan'}"/></td>
                    <td>
                        <c:if test="${b.available}">
                            <%-- Borrow is a POST form, never a GET link --%>
                            <form method="post" action="<c:url value='/borrow'/>" class="d-inline">
                                <input type="hidden" name="bookId" value="${b.bookId}">
                                <button class="btn btn-sm btn-success">Borrow</button>
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

**Add/edit form with errors and sticky values**:

```jsp
<c:set var="editing" value="${book.bookId != 0}"/>
<h1 class="h4">${editing ? 'Edit book' : 'Add book'}</h1>

<c:if test="${not empty errors}">
    <div class="alert alert-danger">Please fix the errors below.</div>
</c:if>

<form method="post" action="<c:url value='/librarian/books/form'/>">
    <input type="hidden" name="bookId" value="${book.bookId}">

    <div class="mb-3">
        <label class="form-label" for="isbn">ISBN</label>
        <input class="form-control ${not empty errors.isbn ? 'is-invalid' : ''}" id="isbn" name="isbn"
               value="<c:out value='${book.isbn}'/>">
        <div class="invalid-feedback"><c:out value="${errors.isbn}"/></div>
    </div>

    <div class="mb-3">
        <label class="form-label" for="publishedYear">Year</label>
        <input class="form-control ${not empty errors.publishedYear ? 'is-invalid' : ''}" id="publishedYear"
               name="publishedYear" value="<c:out value='${book.publishedYear}'/>">
        <div class="invalid-feedback"><c:out value="${errors.publishedYear}"/></div>
    </div>

    <button type="submit" class="btn btn-primary">Save</button>
    <a class="btn btn-secondary" href="<c:url value='/librarian/books'/>">Cancel</a>
</form>
```

**Delete confirmation page**:

```jsp
<div class="alert alert-warning">
    Delete <strong><c:out value="${book.title}"/></strong>? Its past loan records will also be deleted.
</div>
<form method="post" action="<c:url value='/librarian/books/delete'/>">
    <input type="hidden" name="id" value="${book.bookId}">
    <button class="btn btn-danger">Yes, delete</button>
    <a class="btn btn-secondary" href="<c:url value='/librarian/books'/>">Cancel</a>
</form>
```

**My Loans** (limit shown without hard-coding the 3):

```jsp
<p>Loans in use: ${openCount} of ${maxLoans}</p>
<c:forEach var="l" items="${current}">
    <tr>
        <td><c:out value="${l.bookTitle}"/></td>
        <td>${l.borrowedOn}</td>
        <td>
            <form method="post" action="<c:url value='/return'/>">
                <input type="hidden" name="bookId" value="${l.bookId}">
                <button class="btn btn-sm btn-warning">Return</button>
            </form>
        </td>
    </tr>
</c:forEach>
```

**Hide librarian links from members** (convenience only, the constraint is the real protection):

```jsp
<c:if test="${pageContext.request.isUserInRole('librarian')}">
    <a href="<c:url value='/librarian/books'/>">Manage books</a>
</c:if>
```

Notes:
- `${empty x}` is true for null or an empty list/string.
- Use `<c:url>` for links so the context path is added.
- EL is escaped by `<c:out>`, **not** by a bare `${...}`. Always `c:out` user-controlled text (titles, names, search text, messages). Numeric ids in attributes are safe.
- `.jspf` includes use `<%@ include file="..." %>`. That directive is allowed (it is not a scriptlet).

---

## 13. Auth (web.xml, login pages, logout, roles)

**What happens:** request for a protected URL -> Tomcat checks the constraint -> no login yet -> shows `login.jsp` -> form posts to `j_security_check` -> Tomcat checks `members` via the Realm -> redirects to the original URL. Wrong role gets 403. Wrong password goes to `login-error.jsp`.

**`WEB-INF/web.xml`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">

    <welcome-file-list>
        <welcome-file>login.jsp</welcome-file>
    </welcome-file-list>

    <security-role><role-name>member</role-name></security-role>
    <security-role><role-name>librarian</role-name></security-role>

    <!-- catalogue: both roles -->
    <security-constraint>
        <web-resource-collection>
            <web-resource-name>Catalogue</web-resource-name>
            <url-pattern>/catalogue</url-pattern>
        </web-resource-collection>
        <auth-constraint>
            <role-name>member</role-name>
            <role-name>librarian</role-name>
        </auth-constraint>
    </security-constraint>

    <!-- borrow, return, my loans: member only -->
    <security-constraint>
        <web-resource-collection>
            <web-resource-name>Member Loans</web-resource-name>
            <url-pattern>/loans</url-pattern>
            <url-pattern>/borrow</url-pattern>
            <url-pattern>/return</url-pattern>
        </web-resource-collection>
        <auth-constraint>
            <role-name>member</role-name>
        </auth-constraint>
    </security-constraint>

    <!-- all management URLs: librarian only -->
    <security-constraint>
        <web-resource-collection>
            <web-resource-name>Librarian Area</web-resource-name>
            <url-pattern>/librarian/*</url-pattern>
        </web-resource-collection>
        <auth-constraint>
            <role-name>librarian</role-name>
        </auth-constraint>
    </security-constraint>

    <login-config>
        <auth-method>FORM</auth-method>
        <realm-name>APPNAME</realm-name>
        <form-login-config>
            <form-login-page>/login.jsp</form-login-page>
            <form-error-page>/login-error.jsp</form-error-page>
        </form-login-config>
    </login-config>

    <!-- database failures show a friendly page, never a stack trace -->
    <error-page>
        <exception-type>your.pkg.exception.DataAccessException</exception-type>
        <location>/WEB-INF/error.jsp</location>
    </error-page>

</web-app>
```

Notes:
- Do not constrain `login.jsp`, `login-error.jsp`, `/css/*`, `/js/*`, `/images/*`, `/logout`.
- A constraint with no HTTP methods listed covers **all** methods.
- Constraints are checked before servlet mapping, so a member typing a librarian URL gets **403** even if that page doesn't exist.
- Roles come from `member_roles` (section 4) through the Realm (section 5).

**[B] Jdbi only: add a second error-page** beside the `DataAccessException` one. Jdbi reports database failures as `JdbiException`; Tomcat still logs the full stack trace server-side and the user sees only `error.jsp`:

```xml
<error-page>
    <exception-type>org.jdbi.v3.core.JdbiException</exception-type>
    <location>/WEB-INF/error.jsp</location>
</error-page>
```

**`login.jsp`** (outside WEB-INF) with exactly these field names:

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

`login-error.jsp` is the same page plus `<div class="alert alert-danger">Incorrect username or password.</div>` above the form.

**Logout** (`LogoutController`):

```java
@WebServlet(name = "LogoutController", value = "/logout")
public class LogoutController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.logout();                                        // container drops the principal
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        // redirect to a PROTECTED url: Tomcat shows the login form and remembers where you were going.
        // (redirecting to login.jsp directly and then logging in gives HTTP 400 unless the Valve is active)
        response.sendRedirect(request.getContextPath() + "/catalogue");
    }
}
```

**Whoami helpers:**
- `request.getRemoteUser()` gives the username (null if anonymous).
- `request.isUserInRole("librarian")` gives true/false.
- Never take a member id from a form or the URL for borrow/return/my loans.

**Tests (screenshot each):** log in ok; wrong password shows `login-error.jsp`; member opens a librarian URL and gets 403; librarian opens a member-only URL and gets 403; logout then Back does not show the protected page.

---

## 14. Filters

**NoCacheFilter**: stops the browser caching protected pages (Back after logout must not reveal them) and sets UTF-8 on requests:

```java
package your.pkg.filter;

import java.io.IOException;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

@WebFilter(filterName = "NoCacheFilter",
           urlPatterns = {"/catalogue", "/loans", "/borrow", "/return", "/librarian/*"})
public class NoCacheFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");                    // before any getParameter()
        HttpServletResponse res = (HttpServletResponse) response;
        res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        res.setHeader("Pragma", "no-cache");
        res.setDateHeader("Expires", 0);
        chain.doFilter(request, response);                        // continue to the servlet
    }
}
```

**Legacy session-guard filter** (CA2 style, replaced by container security in CA3; useful if a task asks for a hand-written filter):

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

        HttpSession session = req.getSession(false);                    // false = don't create one
        boolean signedIn = session != null && session.getAttribute("member") != null;

        if (openPath || signedIn) chain.doFilter(request, response);   // let it through
        else res.sendRedirect(req.getContextPath() + "/login");          // bounce to login
    }
}
```

Filter facts: `urlPatterns` picks the URLs, `chain.doFilter` continues, not calling it stops the request. Filters run **after** container security checks. If a filter and a security-constraint both exist, the constraint runs first. Remove the old filter's annotation when moving to container security.

---

## 15. i18n (English + Setswana)

**Files** in `src/main/resources` (save as UTF-8):

`messages.properties` (English, the default):

```properties
nav.catalogue=Catalogue
nav.myloans=My Loans
nav.logout=Logout
lang.label=Language
lang.en=English
lang.tn=Setswana
catalogue.heading=Book Catalogue
catalogue.search=Search
catalogue.search.placeholder=Search by title or author
col.title=Title
col.author=Author
col.genre=Genre
col.status=Status
status.available=Available
status.onloan=On loan
btn.borrow=Borrow
btn.return=Return
btn.reserve=Reserve
loans.heading=My Loans
loans.used=Loans in use: {0} of {1}
loans.current=Current loans
loans.history=Borrowing history
loans.borrowed=Borrowed
loans.returned=Returned
```

`messages_tn.properties` (Setswana). **Draft translations: have a Setswana speaker, your lecturer, or a trusted translator check these before you submit.** The brief wants real Setswana, not English copied across.

```properties
nav.catalogue=Lenaneo la Dibuka
nav.myloans=Dibuka tsa Me
nav.logout=Tswa
lang.label=Puo
lang.en=Sekgoa
lang.tn=Setswana
catalogue.heading=Lenaneo la Dibuka
catalogue.search=Batla
catalogue.search.placeholder=Batla ka setlhogo kana mokwadi
col.title=Setlhogo
col.author=Mokwadi
col.genre=Mofuta
col.status=Seemo
status.available=E teng
status.onloan=E adimilwe
btn.borrow=Adima
btn.return=Busetsa
btn.reserve=Beela
loans.heading=Dibuka tsa Me
loans.used=Dibuka tse {0} mo go tse {1}
loans.current=Dibuka tse ke nang natso
loans.history=Hisitori ya go adima
loans.borrowed=E adimilwe
loans.returned=E busetswe
```

Keys must be identical in both files. `{0}`, `{1}` are placeholders filled with `<fmt:param>`.

**Shared include** `WEB-INF/jspf/i18n.jspf` (put at the top of every translated page):

```jsp
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%-- a ?lang=tn / ?lang=en link stores the choice in the SESSION, so it follows the user between pages --%>
<c:if test="${not empty param.lang}">
    <fmt:setLocale value="${param.lang}" scope="session"/>
</c:if>
<fmt:setBundle basename="messages"/>
```

In each page: `<%@ include file="/WEB-INF/jspf/i18n.jspf" %>` (after the `c` taglib line).

**Use it**:

```jsp
<h1><fmt:message key="catalogue.heading"/></h1>

<input type="text" name="search" placeholder="<fmt:message key='catalogue.search.placeholder'/>">

<th><fmt:message key="col.title"/></th>

<%-- message with parameters --%>
<fmt:message key="loans.used">
    <fmt:param value="${openCount}"/>
    <fmt:param value="${maxLoans}"/>
</fmt:message>
```

**Language switch** (links that keep the current page):

```jsp
<a href="?lang=en"><fmt:message key="lang.en"/></a> |
<a href="?lang=tn"><fmt:message key="lang.tn"/></a>
```

Notes:
- Book data from the database is not translated. Everything else visible comes from the bundle.
- Every translated JSP needs `<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>`.
- The bundles must be in `src/main/resources` so they end up in `WEB-INF/classes`.
- If a Setswana key is missing, the English value is used (the fallback), which can hide a typo. Compare the two files.
- A `?lang=` parameter works on controller-forwarded pages because request parameters survive `forward`.

---

## 16. Rules checklist + evidence screenshots

**Brief rules (tick before submitting):**
- [ ] **[A]** Every query is a `PreparedStatement`; no request value is concatenated into SQL
- [ ] **[B]** Every query uses named parameters (`:name` via `@Bind`/`@BindBean`); no request value is concatenated into SQL; **lecturer approved Jdbi**
- [ ] `%` wildcards are added to the bound value
- [ ] **[A]** try-with-resources on every Connection, PreparedStatement, ResultSet
- [ ] **[B]** DAOs created through `Database.dao(...)` (`onDemand`), no manual connection code anywhere
- [ ] No stack trace reaches a page (**[A]** DAO throws `DataAccessException`; **[B]** `JdbiException` error-page added; both mapped in `web.xml`)
- [ ] No scriptlets in any JSP (search for `<%` and only allow `<%@` and `<%--`)
- [ ] Every user value printed through `<c:out>`
- [ ] Every JSP except login pages is under `WEB-INF`
- [ ] Create/edit/delete/borrow/return use POST then redirect
- [ ] Form errors re-display with the typed values
- [ ] Availability is computed, not a column
- [ ] Cascade does the cleanup (no Java loop deleting loans)
- [ ] Identity from `getRemoteUser()`, not from a request field
- [ ] `MAX_LOANS` defined once in `LoanService`
- [ ] Return sets `returned_on` (row stays as history)
- [ ] Hiding a link is not security: librarian URLs give 403 to members
- [ ] `libis.sql` re-runnable on a clean MySQL
- [ ] Both bundles have the same keys, UTF-8, real Setswana

**Evidence document (PDF, captioned screenshots):**
1. Seed data: members, books, loans in Workbench
2. Login as member and as librarian; wrong password; list of test logins (in a table)
3. Member opens a librarian URL: 403
4. Logout then Back: protected page not shown
5. Catalogue and search; search with `<b>x</b>` shown as text (escaping)
6. Borrow success; borrow a book already on loan (message); borrow a 4th book (limit message)
7. My Loans with current loans, `x of 3`, history; return a book
8. Add / edit / delete member and book: validation errors (blank, bad email, duplicate, `20x4`, future year)
9. Delete a book on loan blocked; delete your own account blocked
10. **Loans table in MySQL before and after deleting a member**; their book shows Available again
11. Language switch English <-> Setswana on catalogue and My Loans, persisting between pages
12. A note listing changes made to `Member` and `Book`

Name files `studentnumber_AWD_CA3_2026.zip` and `.pdf` (see the brief), include `libis.sql`.

---

## 17. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Correct login always fails, nothing in the log | Realm not loaded (IntelliJ Tomcat ignores `META-INF/context.xml`) | Deploy the WAR to your own Tomcat `webapps` (section 3) |
| `attempt was made to authenticate the locked user` | LockOutRealm after 5 failures | Restart Tomcat or wait 5 minutes |
| Login fails, `SEVERE` mentioning DataSourceRealm / Unknown column | `member_roles` view column names wrong | Use `SELECT username, role AS role_name FROM members` |
| `Cannot load JDBC driver class` | Connector jar not in Tomcat `lib` | Copy `mysql-connector-j-x.jar` to `TOMCAT/lib`, restart |
| `Name [jdbc/appdb] is not bound` | `context.xml` not applied or name mismatch | Check name in Resource, code and realm; redeploy as a WAR |
| `Access denied for user 'root'` | Wrong password in `context.xml` | Fix and redeploy |
| `Unknown database` / table missing | Script not run, or wrong schema name | Run whole `.sql`, check the spelling |
| HTTP 400 after logging in from `login.jsp` directly | No saved request | Open a protected URL first, or use the Valve (works when deployed as WAR) |
| `zip END header not found` | Corrupt/half-copied WAR | Delete WAR and folder, rebuild, start Tomcat first, then deploy |
| `class file has wrong version` | Compiler target 8 | `maven.compiler.release` 17+ |
| `cannot find symbol` on getters (Lombok) | Annotation processing off | Enable it in IntelliJ settings; reload Maven |
| `isBlank()` cannot find symbol | Java 8 level | Raise release to 17 or use `trim().isEmpty()` |
| `${book.title}` prints blank | Missing getter or wrong property name | Check the getter (`getTitle`) and spelling (e.g. `auther`) |
| JSP prints `${...}` literally | EL disabled or wrong web-app version | Use `version="6.0"` in `web.xml`; check the taglib `jakarta.tags.core` URI |
| Setswana shows `?` or garbled | Not UTF-8 | Save `.properties` as UTF-8, page directive `pageEncoding="UTF-8"` |
| Form values garbled / `NumberFormatException` | No input validation | Parse in try/catch and put errors on the form |
| Refresh repeats an action | Missing redirect | POST then `sendRedirect` |
| 404 on a JSP | JSP is under `WEB-INF` (correct) | Reach it via a controller `forward` |
| Browser Back shows a protected page after logout | Cached | `NoCacheFilter` on protected URLs |
| **[B]** `NoClassDefFoundError: org/jdbi/...` | Jdbi jars not in the WAR | Both `jdbi3-core` and `jdbi3-sqlobject` in the pom, same version, then `mvn clean package` |
| **[B]** A field is `null`/blank though the DB has data | Column and property names differ (`author` vs `auther`) | Alias in the SQL: `b.author AS auther` |
| **[B]** `Missing named parameter 'x'` | `:x` has no matching getter or `@Bind` name | Match the property name exactly (`:publishedYear` for `getPublishedYear()`) |
| **[B]** `Unable to ... no SqlObject plugin` | Plugin not installed | `jdbi.installPlugin(new SqlObjectPlugin())` in `Database.java` |
| **[B]** Red annotation imports | Wrong package guessed | Alt+Enter; they are in `org.jdbi.v3.sqlobject.config`, `.customizer`, `.statement` |
| **[B]** Stack trace on screen for a DB error | No `JdbiException` error-page | Add it to `web.xml` (section 13) |

---

## Practical-day order

1. Check MySQL + Workbench, find the root password; check Tomcat `lib` has the connector jar
2. Copy this file and the LIBIS project beside the new project
3. Schema + seed -> run it, `SELECT` to confirm
4. Decide the DAO style (**A** unless Jdbi is allowed), then `pom.xml`, `context.xml`, `Database.java` (A or B version), `web.xml`; deploy a trivial page to prove Tomcat works
5. Login + roles working **before** features (log in, 403 test)
6. Model -> DAO -> service -> controller -> JSP for entity 1, test, screenshot
7. Entity 2, relationship table, business rules
8. i18n if asked
9. Check section 16, zip and submit


---

## 18. Adapting this to a different project

Think of every app in this stack as three kinds of table:

| Role | LIBIS | Your new project (example) |
|---|---|---|
| **Users** (log in, have a role) | `members` | staff, students, patients, customers |
| **Main entity** (CRUD'd by the admin role) | `books` | medicines, courses, products, rooms |
| **Link / transaction** (user + entity + dates) | `loans` | appointments, enrolments, orders, bookings |

Everything below is "rename these things" or "rewrite these things". Do it **in this order**, so the project compiles as you go.

### Step 0: copy and rename the project

1. Copy the whole LIBIS folder, then delete `.idea`, `target`, `.git` from the copy and open it.
2. Package: right-click the root package in IntelliJ -> Refactor -> **Rename** (it fixes all imports). Do this first.
3. `pom.xml`: `artifactId`, `finalName`, `<name>`.
4. `deploy.bat`: `set APP=...`. URL becomes `/NEWAPP/...`.
5. `web.xml`: `<realm-name>`.

### Step 1: what you must NOT need to touch

| File | Why it survives unchanged |
|---|---|
| `config/Database.java` | Only the JNDI name string, if you rename it (see below) |
| `exception/*` | Generic |
| `filter/NoCacheFilter` | Only its `urlPatterns` list |
| `LogoutController`, `login.jsp`, `login-error.jsp`, `error.jsp` | Only titles/wording; redirect target if `/catalogue` is renamed |
| `start-tomcat.bat`, structure of `context.xml`, `web.xml` skeleton | Same structure, different names |
| DAO pattern (`query`, `map`, `bind`, `fail`), service pattern, controller pattern, JSP patterns | You copy the shape, change the nouns |

### Step 2: database (rewrite)

1. New schema name -> update it in **3 places**: the `.sql` (`CREATE SCHEMA` / `USE`), the `url=` in `context.xml`.
2. Keep the pattern: PK, `UNIQUE` on natural keys, FKs with `ON DELETE CASCADE`, nullable `returned_on`/end date for "open" rows.
3. **The users table must keep three things**: a unique username column, a password column, a role column. Names are yours, but they feed the Realm (step 3).
4. Rebuild the `member_roles` view for your names (username column name must equal the users table's username column):
```sql
CREATE VIEW user_roles AS SELECT username, role AS role_name FROM staff;
```

### Step 3: context.xml (edit values only)

| Attribute | Change to |
|---|---|
| `Resource name` | e.g. `jdbc/clinic` (optional rename) |
| `url` | `.../clinic?...` |
| `password` | the PC's MySQL password |
| Realm `dataSourceName` | **same** as `Resource name` |
| Realm `userTable` | your users table |
| Realm `userNameCol` | its username column (also used in the roles view) |
| Realm `userCredCol` | its password column |
| Realm `userRoleTable` | your roles view/table |
| Realm `roleNameCol` | its role column (`role_name` in the view) |
| Valve `landingPage` | your main page URL |

If you rename the JNDI name, change it in **three** places: `Resource name`, Realm `dataSourceName`, and the string in `Database.java` (`env.lookup("jdbc/...")`).

### Step 4: web.xml (edit values only)

1. `<security-role>` entries -> your role names (must match the role column values exactly).
2. Each `<security-constraint>`: `url-pattern`s -> your URLs, `role-name`s -> your roles.
3. `<error-page>` exception class -> your renamed package.
4. Pattern of URLs to keep: one shared page for all signed-in users, user-only actions, `/admin/*` (or `/librarian/*`) for the privileged role.
5. `NoCacheFilter.urlPatterns` -> the same URL list.

### Step 5: entity by entity (the real work)

For every entity (users, main entity, link), these files change **together**. Example: Book -> Medicine.

| # | File | What to rewrite |
|---|---|---|
| 1 | SQL table | columns |
| 2 | `model/Medicine` | fields + getters/setters (property names = what the JSP uses) |
| 3 | `dao/MedicineDao` | interface methods (add `findByX` for each UNIQUE column) |
| 4 | **[A]** `dao/MedicineDaoImpl`, or **[B]** the annotated `MedicineDao` interface | **[A]** `SELECT` constant, `INSERT`/`UPDATE` SQL, `bind()`, `map()`, table name in `DELETE`; **[B]** the SQL strings, `:param` names, `AS` aliases |
| 5 | `service/MedicineService` | `validate()` rules, `tidy()`, duplicate checks, delete rules |
| 6 | `controller/Medicine*Controller` | `@WebServlet` URL, parameter names read, attribute names, JSP paths, flash messages |
| 7 | `WEB-INF/.../medicine-*.jsp` | field names, labels, `${...}` properties, form `action` URLs |
| 8 | `messages*.properties` | keys and both languages |

**Adding or renaming ONE column touches 6 places.** Miss one and you get a blank value or a SQL error:
1. the `CREATE TABLE`
2. the model field + getter/setter
3. DAO: **[A]** `SELECT`, `INSERT`, `UPDATE`, `bind()` indexes, `map()`; **[B]** the `SELECT`/`INSERT`/`UPDATE` strings, the `:param` names, any `AS` alias
4. service `validate()`
5. controller `request.getParameter("...")`
6. form JSP and list JSP

The most common bug: **[A]** `bind()` parameter numbers no longer matching the `?` order in the SQL (count the `?` marks); **[B]** a `:name` that matches no getter, or a column and property with different names (fix with `AS`).

### Step 6: business rules (rewrite)

`LoanService` is the pattern for your own rule service (`AppointmentService`, `EnrolmentService`...):

```java
public static final int MAX_X = 3;          // the limit, defined once

public void create(int userId, int entityId) throws RuleException {
    // 1. does the entity exist?
    // 2. is it free? (computed with NOT EXISTS, not stored)
    // 3. is the user under their limit? (COUNT of open rows)
    // 4. dao.create(...)
}
public void finish(int userId, int entityId) throws RuleException {
    // UPDATE ... SET end_date = CURDATE() WHERE ... AND end_date IS NULL ; if 0 rows -> RuleException
}
```

Rename `LoanException` to something meaningful, or keep the class and change only the messages.

Derived status pattern (any "is it free / taken" flag):

```sql
NOT EXISTS (SELECT 1 FROM link_table l WHERE l.entity_id = e.entity_id AND l.end_date IS NULL) AS available
```

### Step 7: controllers and "who is the user"

- Identity: `request.getRemoteUser()` -> `findByUsername(...)`. Nothing to change except the DAO name.
- Role check in a JSP: `request.isUserInRole('yourRole')`.
- Keep the mapping: GET shows, POST changes then redirects.

### Step 8: i18n

1. Copy the bundles, keep key **naming** (`col.*`, `btn.*`, `nav.*`).
2. Replace the values; add keys for every new label.
3. Both files must have the same key set.

### Step 9: clean-up searches

IntelliJ: **Ctrl+Shift+F** (Find in Files). Search these, case-sensitive, and fix leftovers:

```
libis   Libis   LIBIS
Book    book    books
Member  member  members
Loan    loan    loans
librarian
auther
appdb / jdbc/
```

Use **Ctrl+Shift+R** (Replace in Files) with "Match case" and "Preview" ticked. Replace plural before singular (`books` -> `medicines` first, then `book` -> `medicine`), otherwise you get `medicines` turning into `medicinees`.

### Rename-vs-rewrite summary

| Rename only | Rewrite |
|---|---|
| package, artifact, app name, schema name, JNDI name | table columns, SQL strings, `bind`/`map` |
| roles, URL patterns, realm column names | validation rules, business rules |
| JSP labels, bundle values | JSP field layout |
| titles, flash messages | anything that is domain logic |

### 30-minute adaptation drill (practical day)

1. Copy project, rename package/artifact/APP (5 min)
2. New schema + seed + roles view, run it (5 min)
3. Edit `context.xml` values (names, password), `web.xml` roles/URLs (5 min)
4. Deploy and prove **login works with the seeded users** (5 min)
5. Entity 1: model, DAO, service, controller, JSPs, screenshot (rest)
