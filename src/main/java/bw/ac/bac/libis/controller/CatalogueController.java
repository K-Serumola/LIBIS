package bw.ac.bac.libis.controller;

import java.io.IOException;
import java.util.List;

import bw.ac.bac.libis.dao.BookDao;
import bw.ac.bac.libis.dao.BookDaoImpl;
import bw.ac.bac.libis.dao.MemberDao;
import bw.ac.bac.libis.dao.MemberDaoImpl;
import bw.ac.bac.libis.model.Book;
import bw.ac.bac.libis.model.Member;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet(name = "CatalogueController", value = "/catalogue")
public class CatalogueController extends HttpServlet {

    private final BookDao bookDao = new BookDaoImpl();
    private final MemberDao memberDao = new MemberDaoImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Member member = memberDao.findByUsername(request.getRemoteUser());
        String search = request.getParameter("search");

        List<Book> result;
        if (search == null || search.trim().isEmpty()) {
            result = bookDao.findAll();
        } else {
            result = bookDao.search(search.trim());
        }

        request.setAttribute("memberName", member != null ? member.getFirstName() : request.getRemoteUser());
        request.setAttribute("books", result);
        request.setAttribute("search", search);
        request.getRequestDispatcher("/WEB-INF/welcome.jsp").forward(request, response);
    }
}