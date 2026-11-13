# Jakarta Web App Cheat Sheet (Servlet + JSP + JDBC + MySQL + Tomcat FORM security)

Stack: Maven, Tomcat 11 (Jakarta Servlet 6.1), JSP + JSTL/EL (no scriptlets), JDBC with DAOs, MySQL, DataSourceRealm, JSTL fmt for i18n.
Examples use LIBIS (Book, Member, Loan). **Rename** the domain and find/replace `your.pkg` with your root package.

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

`config/Database.java`:

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
- [ ] Every query is a `PreparedStatement`; no request value is concatenated into SQL
- [ ] `%` wildcards are added to the bound value
- [ ] try-with-resources on every Connection, PreparedStatement, ResultSet
- [ ] No stack trace reaches a page (DAO throws `DataAccessException`, `web.xml` error-page)
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

---

## Practical-day order

1. Check MySQL + Workbench, find the root password; check Tomcat `lib` has the connector jar
2. Copy this file and the LIBIS project beside the new project
3. Schema + seed -> run it, `SELECT` to confirm
4. `pom.xml`, `context.xml`, `Database.java`, `web.xml`; deploy a trivial page to prove Tomcat works
5. Login + roles working **before** features (log in, 403 test)
6. Model -> DAO -> service -> controller -> JSP for entity 1, test, screenshot
7. Entity 2, relationship table, business rules
8. i18n if asked
9. Check section 16, zip and submit
