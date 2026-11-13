package bw.ac.bac.libis.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import bw.ac.bac.libis.config.Database;
import bw.ac.bac.libis.exception.DataAccessException;
import bw.ac.bac.libis.model.Loan;

public class LoanDaoImpl implements LoanDao {

    private static final Logger LOG = Logger.getLogger(LoanDaoImpl.class.getName());

    private static final String SELECT =
            "SELECT l.loan_id, l.member_id, l.book_id, b.title, l.borrowed_on, l.returned_on "
                    + "FROM loans l JOIN books b ON b.book_id = l.book_id ";

    @Override
    public void create(int memberId, int bookId) {
        String sql = "INSERT INTO loans (member_id, book_id, borrowed_on) VALUES (?, ?, CURDATE())";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setInt(2, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw fail("Could not record loan", e);
        }
    }

    @Override
    public boolean markReturned(int memberId, int bookId) {
        String sql = "UPDATE loans SET returned_on = CURDATE() "
                + "WHERE member_id = ? AND book_id = ? AND returned_on IS NULL";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            ps.setInt(2, bookId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw fail("Could not record return", e);
        }
    }

    @Override
    public int countOpenByMember(int memberId) {
        String sql = "SELECT COUNT(*) FROM loans WHERE member_id = ? AND returned_on IS NULL";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw fail("Could not count loans", e);
        }
    }

    @Override
    public List<Loan> findOpenByMember(int memberId) {
        return query(SELECT + "WHERE l.member_id = ? AND l.returned_on IS NULL ORDER BY l.borrowed_on", memberId);
    }

    @Override
    public List<Loan> findHistoryByMember(int memberId) {
        return query(SELECT + "WHERE l.member_id = ? AND l.returned_on IS NOT NULL ORDER BY l.returned_on DESC", memberId);
    }

    @Override
    public boolean hasOpenLoan(int bookId) {
        String sql = "SELECT 1 FROM loans WHERE book_id = ? AND returned_on IS NULL LIMIT 1";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw fail("Could not check loan status", e);
        }
    }

    private List<Loan> query(String sql, int memberId) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Loan> list = new ArrayList<>();
                while (rs.next()) {
                    Loan l = new Loan();
                    l.setLoanId(rs.getInt("loan_id"));
                    l.setMemberId(rs.getInt("member_id"));
                    l.setBookId(rs.getInt("book_id"));
                    l.setBookTitle(rs.getString("title"));
                    l.setBorrowedOn(rs.getObject("borrowed_on", LocalDate.class));
                    l.setReturnedOn(rs.getObject("returned_on", LocalDate.class));
                    list.add(l);
                }
                return list;
            }
        } catch (SQLException e) {
            throw fail("Could not read loans", e);
        }
    }

    private DataAccessException fail(String message, SQLException e) {
        LOG.log(Level.SEVERE, message, e);
        return new DataAccessException(message, e);
    }
}