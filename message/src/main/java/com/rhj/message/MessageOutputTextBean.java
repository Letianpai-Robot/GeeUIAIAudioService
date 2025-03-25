package com.rhj.message;

import com.google.gson.annotations.SerializedName;

public class MessageOutputTextBean extends MessageBean{
    private String text;
    @SerializedName("skillId")
    private String skillId;
    @SerializedName("skillName")
    private String skillName;
    @SerializedName("taskName")
    private String taskName;
    @SerializedName("intentName")
    private String intentName;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getIntentName() {
        return intentName;
    }

    public void setIntentName(String intentName) {
        this.intentName = intentName;
    }

    @Override
    public String toString() {
        return "MessageOutputTextBean{" +
                "text='" + text + '\'' +
                ", skillId='" + skillId + '\'' +
                ", skillName='" + skillName + '\'' +
                ", taskName='" + taskName + '\'' +
                ", intentName='" + intentName + '\'' +
                '}';
    }
}
