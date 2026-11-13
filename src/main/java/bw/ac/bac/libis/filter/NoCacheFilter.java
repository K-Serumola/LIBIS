package bw.ac.bac.libis.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

@WebFilter(filterName = "NoCacheFilter",
        urlPatterns = {"/catalogue", "/loans", "/borrow", "/return", "/librarian/*"})
public class NoCacheFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");

        HttpServletResponse res = (HttpServletResponse) response;
        res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        res.setHeader("Pragma", "no-cache");
        res.setDateHeader("Expires", 0);

        chain.doFilter(request, response);
    }
}