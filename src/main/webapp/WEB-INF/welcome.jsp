<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <title>Book List</title>
</head>
<body>
    <h2>Welcome, ${memberName}!</h2>
    <p><a href="${pageContext.request.contextPath}/logout">Logout</a></p>

    <form action="${pageContext.request.contextPath}/catalogue" method="GET">
        <input type="text" name="search" value="${search}" placeholder="Search by title">
        <button type="submit">Search</button>
    </form>

    <table border="1">
        <tr>
            <th>ID</th>
            <th>Title</th>
            <th>Author</th>
            <th>Genre</th>
            <th>Available</th>
            <th>Actions</th>
        </tr>
        <c:forEach var="book" items="${books}">
        <tr>
            <td>${book.bookId}</td>
            <td>${book.title}</td>
            <td>${book.auther}</td>
            <td>${book.genre}</td>
            <td>${book.available ? "Yes" : "No"}</td>
            <td>
                <a href="#######">Reserve</a> |
                <a href="#######">Borrow</a>
            </td>
        </tr>
        </c:forEach>
    </table>
</body>
</html>