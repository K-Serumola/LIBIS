package bw.ac.bac.libis.model;

public class Book {

    private int bookId;
    private String isbn;
    private String title;
    private String auther;
    private String genre;
    private String synopsis;
    private Integer publishedYear;
    private Boolean available;

    public Book() {}

    public Book(int bookId, String title, String auther, String genre, String synopsis, Boolean available) {
        this.bookId = bookId;
        this.title = title;
        this.auther = auther;
        this.genre = genre;
        this.synopsis = synopsis;
        this.available = available;
    }

    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuther() { return auther; }
    public void setAuther(String auther) { this.auther = auther; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }

    public Integer getPublishedYear() { return publishedYear; }
    public void setPublishedYear(Integer publishedYear) { this.publishedYear = publishedYear; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }
}