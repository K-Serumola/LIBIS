package bw.ac.bac.libis.dao;

import java.util.List;

import bw.ac.bac.libis.model.Book;

public interface BookDao {
    List<Book> findAll();
    Book findById(int bookId);
    Book findByIsbn(String isbn);
    List<Book> search(String term);
    void create(Book book);
    void update(Book book);
    void delete(int bookId);
}