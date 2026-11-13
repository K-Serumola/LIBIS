package bw.ac.bac.libis.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import bw.ac.bac.libis.config.Database;
import bw.ac.bac.libis.exception.DataAccessException;
import bw.ac.bac.libis.model.Member;

public class MemberDaoImpl implements MemberDao {

    private static final Logger LOG = Logger.getLogger(MemberDaoImpl.class.getName());

    private static final String SELECT =
            "SELECT member_id, member_number, full_name, email, username, password, role FROM members";

    @Override
    public List<Member> findAll() {
        return query(SELECT + " ORDER BY member_id");
    }

    @Override
    public Member findById(int memberId) {
        return first(query(SELECT + " WHERE member_id = ?", memberId));
    }

    @Override
    public Member findByUsername(String username) {
        return first(query(SELECT + " WHERE username = ?", username));
    }

    @Override
    public Member findByMemberNumber(String memberNumber) {
        return first(query(SELECT + " WHERE member_number = ?", memberNumber));
    }

    @Override
    public Member findByEmail(String email) {
        return first(query(SELECT + " WHERE email = ?", email));
    }

    @Override
    public List<Member> search(String term) {
        String like = "%" + term.trim() + "%";
        return query(SELECT + " WHERE full_name LIKE ? OR member_number LIKE ? ORDER BY member_id", like, like);
    }

    @Override
    public void create(Member m) {
        String sql = "INSERT INTO members (member_number, full_name, email, username, password, role) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getMemberNumber());
            ps.setString(2, m.getFullName());
            ps.setString(3, m.getEmail());
            ps.setString(4, m.getUsername());
            ps.setString(5, m.getPassword());
            ps.setString(6, m.getRole());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    m.setMemberId(keys.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw fail("Could not create member", e);
        }
    }

    @Override
    public void update(Member m) {
        String sql = "UPDATE members SET member_number = ?, full_name = ?, email = ?, username = ?, "
                + "password = ?, role = ? WHERE member_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, m.getMemberNumber());
            ps.setString(2, m.getFullName());
            ps.setString(3, m.getEmail());
            ps.setString(4, m.getUsername());
            ps.setString(5, m.getPassword());
            ps.setString(6, m.getRole());
            ps.setInt(7, m.getMemberId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw fail("Could not update member", e);
        }
    }

    @Override
    public void delete(int memberId) {
        // loans are removed by ON DELETE CASCADE, not by Java code
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM members WHERE member_id = ?")) {
            ps.setInt(1, memberId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw fail("Could not delete member", e);
        }
    }

    private List<Member> query(String sql, Object... params) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Member> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw fail("Could not read members", e);
        }
    }

    private Member first(List<Member> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    private Member map(ResultSet rs) throws SQLException {
        Member m = new Member();
        m.setMemberId(rs.getInt("member_id"));
        m.setMemberNumber(rs.getString("member_number"));
        m.setFullName(rs.getString("full_name"));
        m.setEmail(rs.getString("email"));
        m.setUsername(rs.getString("username"));
        m.setPassword(rs.getString("password"));
        m.setRole(rs.getString("role"));
        return m;
    }

    private DataAccessException fail(String message, SQLException e) {
        LOG.log(Level.SEVERE, message, e);
        return new DataAccessException(message, e);
    }
}