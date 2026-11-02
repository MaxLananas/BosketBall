package fr.bosketball.model;

/**
 * Persistent Per-Player Visual, Audio & Gameplay Preferences (editable in /bb settings & 3D Locker-Room Hub!).
 */
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
}
