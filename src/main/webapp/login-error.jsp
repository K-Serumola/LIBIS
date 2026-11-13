<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>LIBIS - Sign in failed</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
<div class="container" style="max-width: 420px; margin-top: 10vh;">
    <div class="card shadow-sm">
        <div class="card-body p-4">
            <h1 class="h4 mb-3">LIBIS Sign in</h1>
            <div class="alert alert-danger" role="alert">
                Incorrect username or password. Please try again.
            </div>
            <form method="post" action="<c:url value='/j_security_check'/>">
                <div class="mb-3">
                    <label for="j_username" class="form-label">Username</label>
                    <input type="text" class="form-control" id="j_username" name="j_username" required autofocus>
                </div>
                <div class="mb-3">
                    <label for="j_password" class="form-label">Password</label>
                    <input type="password" class="form-control" id="j_password" name="j_password" required>
                </div>
                <button type="submit" class="btn btn-primary w-100">Sign in</button>
            </form>
        </div>
    </div>
</div>
</body>
</html>