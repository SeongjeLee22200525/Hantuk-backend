package app.hantteok.common.error;

public class ApiException extends RuntimeException {
    final int status;
    final String code;
    public ApiException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public static ApiException bad(String message) { return new ApiException(400, "INVALID_REQUEST", message); }
    public static ApiException conflict(String message) { return new ApiException(409, "INVALID_STATE", message); }
    public static ApiException forbidden() { return new ApiException(403, "FORBIDDEN", "접근 권한이 없습니다."); }
    public static ApiException missing() { return new ApiException(404, "NOT_FOUND", "대상을 찾을 수 없습니다."); }
    public static ApiException unauthorized() { return new ApiException(401, "UNAUTHORIZED", "로그인이 필요하거나 세션이 만료되었습니다."); }
}
