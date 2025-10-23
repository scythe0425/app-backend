package org.dcode.artificialswbackend.signup.dto;

public class LoginResponseDto {
    private final String token;
    private Long archiveId;

    public LoginResponseDto(String token) {
        this.token = token;
    }

    public LoginResponseDto(String token, Long archiveId) {
        this.token = token;
        this.archiveId = archiveId;
    }

    public Long getArchiveId() {
        return archiveId;
    }

    public void setArchiveId(Long archiveId) {
        this.archiveId = archiveId;
    }

    public String getToken() {
        return token;
    }
}
