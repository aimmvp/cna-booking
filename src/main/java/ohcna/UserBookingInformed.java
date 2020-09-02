package ohcna;

public class UserBookingInformed extends AbstractEvent {

    private Long id;
    private String userId;
    private String userName;
    private String createDtm;
    private Long bookingId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
    public String getCreateDtm() {
        return createDtm;
    }

    public void setCreateDtm(String createDtm) {
        this.createDtm = createDtm;
    }

    // 추가
    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

}