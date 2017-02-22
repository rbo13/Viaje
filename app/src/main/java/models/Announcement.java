package models;

/**
 * Created by richard on 16/02/2017.
 */

public class Announcement {

    private String message;
    private Long timestamp;

    public Announcement() {  }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
