package bw.ac.bac.libis.dao;

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

import bw.ac.bac.libis.config.Database;
import bw.ac.bac.libis.exception.DataAccessException;
import bw.ac.bac.libis.model.Book;

public class BookDaoImpl implements BookDao {

    private static final Logger LOG = Logger.getLogger(BookDaoImpl.class.getName());

    private static final String SELECT =
            "SELECT b.book_id, b.isbn, b.title, b.author, b.genre, b.synopsis, b.published_year, "
                    + "NOT EXISTS (SELECT 1 FROM loans l WHERE l.book_id = b.book_id AND l.returned_on IS NULL) AS available "
                    + "FROM books b";

    @Override
    public List<Book> findAll() {
        return query(SELECT + " ORDER BY b.book_id");
    }

    @Override
    public Book findById(int bookId) {
        return first(query(SELECT + " WHERE b.book_id = ?", bookId));
    }

    @Override
    public Book findByIsbn(String isbn) {
        return first(query(SELECT + " WHERE b.isbn = ?", isbn));
    }

    @Override
    public List<Book> search(String term) {
        String like = "%" + term.trim() + "%";
        return query(SELECT + " WHERE b.title LIKE ? OR b.author LIKE ? ORDER BY b.book_id", like, like);
    }

    @Override
    public void create(Book b) {
        String sql = "INSERT INTO books (isbn, title, author, genre, synopsis, published_year) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, b);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    b.setBookId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw fail("Could not create book", e);
        }
    }

    @Override
    public void update(Book b) {
        String sql = "UPDATE books SET isbn = ?, title = ?, author = ?, genre = ?, synopsis = ?, "
                + "published_year = ? WHERE book_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, b);
            ps.setInt(7, b.getBookId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw fail("Could not update book", e);
        }
    }

    @Override
    public void delete(int bookId) {
        // past loan rows are removed by ON DELETE CASCADE
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM books WHERE book_id = ?")) {
            ps.setInt(1, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw fail("Could not delete book", e);
        }
    }

    private void bind(PreparedStatement ps, Book b) throws SQLException {
        ps.setString(1, b.getIsbn());
        ps.setString(2, b.getTitle());
        ps.setString(3, b.getAuther());
        ps.setString(4, b.getGenre());
        ps.setString(5, b.getSynopsis());
        ps.setObject(6, b.getPublishedYear(), Types.INTEGER);
    }

    private List<Book> query(String sql, Object... params) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Book> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw fail("Could not read books", e);
        }
    }

    private Book first(List<Book> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    private Book map(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.setBookId(rs.getInt("book_id"));
        b.setIsbn(rs.getString("isbn"));
        b.setTitle(rs.getString("title"));
        b.setAuther(rs.getString("author"));
        b.setGenre(rs.getString("genre"));
        b.setSynopsis(rs.getString("synopsis"));
        b.setPublishedYear(rs.getObject("published_year", Integer.class));
        b.setAvailable(rs.getBoolean("available"));
        return b;
    }

    private DataAccessException fail(String message, SQLException e) {
        LOG.log(Level.SEVERE, message, e);
        return new DataAccessException(message, e);
    }
}