package kz.uniplan.app.util;

import java.util.regex.Pattern;
import java.net.URI;
import java.net.URISyntaxException;

/** Pure Java form rules, shared by repositories and JVM tests. */
public final class Validation {
    private static final Pattern LOCAL_EMAIL = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+");
    private static final Pattern DOMAIN_LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private Validation() {}
    public static boolean required(String value) { return value != null && !value.trim().isEmpty(); }
    public static boolean title(String value) { return required(value) && value.trim().length() <= 200; }
    public static boolean email(String value) {
        if (value == null) return false;
        String text=value.trim();
        if (text.length() > 254) return false;
        String[] parts=text.split("@",-1);
        if (parts.length != 2 || parts[0].length() > 64 || !LOCAL_EMAIL.matcher(parts[0]).matches()
                || parts[0].startsWith(".") || parts[0].endsWith(".") || parts[0].contains("..")) return false;
        String[] labels=parts[1].split("\\.",-1);
        if (labels.length < 2 || labels[labels.length-1].length() < 2) return false;
        for (String label:labels) if(!DOMAIN_LABEL.matcher(label).matches()) return false;
        return true;
    }
    public static boolean login(String email,String password) { return email(email) && password != null && !password.isEmpty(); }
    public static boolean password(String value) { return value != null && value.length() >= 6; }
    public static boolean registration(String name,String email,String password,String confirmation) {
        return title(name) && email(email) && password(password) && password.equals(confirmation);
    }
    public static boolean task(String title,long subjectId,int kind,long dueAt,int priority) {
        return title(title) && subjectId > 0 && (kind == 0 || kind == 1) && dueAt > 0 && priority >= 0 && priority <= 2;
    }
    public static boolean lesson(int day,int start,int end) {
        return day >= 1 && day <= 7 && start >= 0 && end <= 1439 && start < end;
    }
    public static boolean overdue(boolean completed,long dueAt,long now) { return !completed && dueAt < now; }
    /** Empty means no link. Only an absolute HTTP(S) web address can be launched by UI. */
    public static boolean webUrl(String value) {
        if(value == null || value.isEmpty()) return true;
        if(value.length() > 2048) return false;
        try {
            URI uri = new URI(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isEmpty() && uri.getRawUserInfo() == null;
        } catch (URISyntaxException invalid) { return false; }
    }
}
