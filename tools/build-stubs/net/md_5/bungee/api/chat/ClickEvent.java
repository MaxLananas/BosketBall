package net.md_5.bungee.api.chat;
/** Compile-only stub. */
public class ClickEvent {
    public enum Action { OPEN_URL, OPEN_FILE, RUN_COMMAND, SUGGEST_COMMAND, CHANGE_PAGE, COPY_TO_CLIPBOARD }
    private Action action; private String value;
    public ClickEvent(Action action, String value) { this.action = action; this.value = value; }
    public Action getAction() { return action; }
    public String getValue() { return value; }
    public void setAction(Action action) { this.action = action; }
    public void setValue(String value) { this.value = value; }
}
