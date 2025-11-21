package digitallib;

import java.sql.*;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

public class bookborrow {

    public static void main(String[] args) {
        // Load database configuration
        Properties props = new Properties();
        try (InputStream input = bookborrow.class.getResourceAsStream("/db.properties")) {
            if (input == null) {
                throw new RuntimeException("db.properties not found in resources folder!");
            }
            props.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load db.properties", e);
        }

        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String pass = props.getProperty("db.pass");

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Scanner sc = new Scanner(System.in)) {

            System.out.println("✅ Connected to Digital Library Database successfully!\n");

            while (true) {
                System.out.println("==== DIGITAL LIBRARY MENU ====");
                System.out.println("1. Add New Book");
                System.out.println("2. View All Books");
                System.out.println("3. Borrow a Book");
                System.out.println("4. Return a Book");
                System.out.println("5. View Borrowers");
                System.out.println("6. Exit");
                System.out.print("Enter your choice: ");
                int choice = sc.nextInt();
                sc.nextLine(); // consume newline

                switch (choice) {
                    case 1:
                        addBook(conn, sc);
                        break;
                    case 2:
                        viewBooks(conn);
                        break;
                    case 3:
                        borrowBook(conn, sc);
                        break;
                    case 4:
                        returnBook(conn, sc);
                        break;
                    case 5:
                        viewBorrowers(conn);
                        break;
                    case 6:
                        System.out.println("Exiting Digital Library... 👋");
                        return;
                    default:
                        System.out.println("❌ Invalid choice. Try again.");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ---------- ADD BOOK ----------
    private static void addBook(Connection conn, Scanner sc) throws SQLException {
        System.out.print("Enter book title: ");
        String title = sc.nextLine();
        System.out.print("Enter author: ");
        String author = sc.nextLine();

        String sql = "INSERT INTO books (title, author, available) VALUES (?, ?, TRUE)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, author);
            int rows = ps.executeUpdate();
            System.out.println("✅ Added " + rows + " new book(s).");
        }
    }

    // ---------- VIEW BOOKS ----------
    private static void viewBooks(Connection conn) throws SQLException {
        String sql = "SELECT id, title, author, available FROM books";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n------ Books in Library ------");
            while (rs.next()) {
                String status = rs.getBoolean("available") ? "Available" : "Borrowed";
                System.out.printf("%d | %s | %s | %s%n",
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        status);
            }
            System.out.println("--------------------------------\n");
        }
    }

    // ---------- BORROW BOOK ----------
    private static void borrowBook(Connection conn, Scanner sc) throws SQLException {
        System.out.print("Enter borrower name: ");
        String name = sc.nextLine();
        System.out.print("Enter book ID to borrow: ");
        int bookId = sc.nextInt();
        sc.nextLine();

        // Check if available
        String check = "SELECT available FROM books WHERE id = ?";
        try (PreparedStatement psCheck = conn.prepareStatement(check)) {
            psCheck.setInt(1, bookId);
            ResultSet rs = psCheck.executeQuery();
            if (!rs.next()) {
                System.out.println("❌ Book not found.");
                return;
            }
            if (!rs.getBoolean("available")) {
                System.out.println("⚠️ Book is already borrowed.");
                return;
            }
        }

        // Borrow (insert + update)
        String insert = "INSERT INTO borrowers (name, book_id, borrow_date) VALUES (?, ?, CURRENT_DATE())";
        String update = "UPDATE books SET available = FALSE WHERE id = ?";
        try (PreparedStatement ps1 = conn.prepareStatement(insert);
             PreparedStatement ps2 = conn.prepareStatement(update)) {

            ps1.setString(1, name);
            ps1.setInt(2, bookId);
            ps1.executeUpdate();

            ps2.setInt(1, bookId);
            ps2.executeUpdate();

            System.out.println("✅ Book borrowed successfully!");
        }
    }

    // ---------- RETURN BOOK ----------
    private static void returnBook(Connection conn, Scanner sc) throws SQLException {
        System.out.print("Enter book ID to return: ");
        int bookId = sc.nextInt();
        sc.nextLine();

        // Check if book exists
        String check = "SELECT available FROM books WHERE id = ?";
        try (PreparedStatement psCheck = conn.prepareStatement(check)) {
            psCheck.setInt(1, bookId);
            ResultSet rs = psCheck.executeQuery();
            if (!rs.next()) {
                System.out.println("❌ Book not found.");
                return;
            }
            if (rs.getBoolean("available")) {
                System.out.println("⚠️ Book is not currently borrowed.");
                return;
            }
        }

        // Return (update borrower + book)
        String updateBorrower = "UPDATE borrowers SET return_date = CURRENT_DATE() " +
                                "WHERE book_id = ? AND return_date IS NULL";
        String updateBook = "UPDATE books SET available = TRUE WHERE id = ?";

        try (PreparedStatement ps1 = conn.prepareStatement(updateBorrower);
             PreparedStatement ps2 = conn.prepareStatement(updateBook)) {

            ps1.setInt(1, bookId);
            ps1.executeUpdate();

            ps2.setInt(1, bookId);
            ps2.executeUpdate();

            System.out.println("✅ Book returned successfully!");
        }
    }

    // ---------- VIEW BORROWERS ----------
    private static void viewBorrowers(Connection conn) throws SQLException {
        String sql = "SELECT b.id AS borrower_id, b.name, bk.title, b.borrow_date, b.return_date " +
                     "FROM borrowers b " +
                     "JOIN books bk ON b.book_id = bk.id " +
                     "ORDER BY b.borrow_date DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("\n------ Borrower Records ------");
            while (rs.next()) {
                Date borrowDate = rs.getDate("borrow_date");
                Date returnDate = rs.getDate("return_date");
                System.out.printf("%d | %s | %s | Borrowed: %s | Returned: %s%n",
                        rs.getInt("borrower_id"),
                        rs.getString("name"),
                        rs.getString("title"),
                        borrowDate,
                        returnDate == null ? "Not Returned" : returnDate);
            }
            System.out.println("--------------------------------\n");
        }
    }
}
