package fr.bosketball.model;

/**
 * Persistent Per-Player Visual, Audio & Gameplay Preferences (editable in /bb settings & 3D Locker-Room Hub!).
 */
public final class PlayerSettings {

    private boolean customSounds = true;
    private boolean aimTrajectory = true;
    private boolean teamFloorRings = true;
    private boolean overheadNameplates = true;
    private boolean selfBodyRigInF5 = false;
    private boolean chatAnnouncements = true;

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

    private boolean customAnimations = true;

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
        return aimTrajectory;
    }

    public void setCinematicScoreCam(boolean cinematicScoreCam) {
        this.aimTrajectory = cinematicScoreCam;
    }

    public boolean partyInvites() {
        return chatAnnouncements;
    }

    public void setPartyInvites(boolean partyInvites) {
        this.chatAnnouncements = partyInvites;
    }
}
