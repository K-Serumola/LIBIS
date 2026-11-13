package bw.ac.bac.libis.model;

public class Member {
    private int memberId;
    private String memberNumber;
    private String fullName;
    private String email;
    private String username;
    private String password;
    private String role;

    public Member() {}

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getMemberNumber() { return memberNumber; }
    public void setMemberNumber(String memberNumber) { this.memberNumber = memberNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getFirstName() {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }
        return fullName.trim().split("\\s+")[0];
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}