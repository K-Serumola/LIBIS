<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>LIBIS - Something went wrong</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
<div class="container" style="max-width: 560px; margin-top: 10vh;">
    <div class="alert alert-danger">
        <h1 class="h5">Something went wrong</h1>
        <p class="mb-0">The library system could not complete your request. Please try again in a moment.</p>
    </div>
    <a class="btn btn-secondary" href="<c:url value='/catalogue'/>">Back to catalogue</a>
</div>
</body>
</html>