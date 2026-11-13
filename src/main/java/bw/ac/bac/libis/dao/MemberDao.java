package bw.ac.bac.libis.dao;

import java.util.List;

import bw.ac.bac.libis.model.Member;

public interface MemberDao {
    List<Member> findAll();
    Member findById(int memberId);
    Member findByUsername(String username);
    Member findByMemberNumber(String memberNumber);
    Member findByEmail(String email);
    List<Member> search(String term);
    void create(Member member);
    void update(Member member);
    void delete(int memberId);
}