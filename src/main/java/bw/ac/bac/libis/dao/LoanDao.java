package bw.ac.bac.libis.dao;

import java.util.List;

import bw.ac.bac.libis.model.Loan;

public interface LoanDao {
    void create(int memberId, int bookId);
    boolean markReturned(int memberId, int bookId);
    int countOpenByMember(int memberId);
    List<Loan> findOpenByMember(int memberId);
    List<Loan> findHistoryByMember(int memberId);
    boolean hasOpenLoan(int bookId);
}