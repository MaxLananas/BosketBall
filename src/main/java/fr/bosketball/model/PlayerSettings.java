package fr.bosketball.model;

public final class PlayerSettings {

    private boolean customSounds = true;
    private boolean aimTrajectory = true;
    private boolean teamFloorRings = true;
    private boolean overheadNameplates = true;
    private boolean selfBodyRigInF5 = true;
    private boolean chatAnnouncements = true;
    private boolean customAnimations = true;
    private boolean cinematicScoreCam = true;
    private boolean partyInvites = true;
    private String language = "en";
    private boolean profanityFilter = true;
    private boolean partyChat = true;
    private boolean showStatusInTab = true;
    private boolean showQuestNotifications = true;

    public boolean customSounds() {
        return customSounds;
    }

    public void setCustomSounds(boolean customSounds) {
        this.customSounds = customSounds;
    }

    public boolean aimTrajectory() {
        return aimTrajectory;
    }

    public void setAimTrajectory(boolean aimTrajectory) {
        this.aimTrajectory = aimTrajectory;
    }

    public boolean teamFloorRings() {
        return teamFloorRings;
    }

    public void setTeamFloorRings(boolean teamFloorRings) {
        this.teamFloorRings = teamFloorRings;
    }

    public boolean overheadNameplates() {
        return overheadNameplates;
    }

    public void setOverheadNameplates(boolean overheadNameplates) {
        this.overheadNameplates = overheadNameplates;
    }

    public boolean selfBodyRigInF5() {
        return selfBodyRigInF5;
    }

    public void setSelfBodyRigInF5(boolean selfBodyRigInF5) {
        this.selfBodyRigInF5 = selfBodyRigInF5;
    }

    public boolean chatAnnouncements() {
        return chatAnnouncements;
    }

    public void setChatAnnouncements(boolean chatAnnouncements) {
        this.chatAnnouncements = chatAnnouncements;
    }

    public boolean customAnimations() {
        return customAnimations;
    }

    public void setCustomAnimations(boolean customAnimations) {
        this.customAnimations = customAnimations;
    }

    public boolean floorAuraRings() {
        return teamFloorRings;
    }

    public void setFloorAuraRings(boolean floorAuraRings) {
        this.teamFloorRings = floorAuraRings;
    }

    public boolean cinematicScoreCam() {
        return cinematicScoreCam;
    }

    public void setCinematicScoreCam(boolean cinematicScoreCam) {
        this.cinematicScoreCam = cinematicScoreCam;
    }

    public boolean partyInvites() {
        return partyInvites;
    }

    public void setPartyInvites(boolean partyInvites) {
        this.partyInvites = partyInvites;
    }

    public String language() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = (language == null || language.isBlank()) ? "en" : language.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean profanityFilter() {
        return profanityFilter;
    }

    public void setProfanityFilter(boolean profanityFilter) {
        this.profanityFilter = profanityFilter;
    }

    public boolean partyChat() {
        return partyChat;
    }

    public void setPartyChat(boolean partyChat) {
        this.partyChat = partyChat;
    }

    public boolean showStatusInTab() {
        return showStatusInTab;
    }

    public void setShowStatusInTab(boolean showStatusInTab) {
        this.showStatusInTab = showStatusInTab;
    }

    public boolean showQuestNotifications() {
        return showQuestNotifications;
    }

    public void setShowQuestNotifications(boolean showQuestNotifications) {
        this.showQuestNotifications = showQuestNotifications;
    }
}
