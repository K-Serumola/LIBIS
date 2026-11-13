package bw.ac.bac.libis.model;

import java.time.LocalDate;

public class Loan {
    private int loanId;
    private int memberId;
    private int bookId;
    private String bookTitle;
    private LocalDate borrowedOn;
    private LocalDate returnedOn;

    public Loan() {}

    public int getLoanId() { return loanId; }
    public void setLoanId(int loanId) { this.loanId = loanId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }

    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }

    public LocalDate getBorrowedOn() { return borrowedOn; }
    public void setBorrowedOn(LocalDate borrowedOn) { this.borrowedOn = borrowedOn; }

    public LocalDate getReturnedOn() { return returnedOn; }
    public void setReturnedOn(LocalDate returnedOn) { this.returnedOn = returnedOn; }
}