package com.rhj.message;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Auto-generated: 2023-01-18 17:10:22
 *
 * @author json.cn (i@json.cn)
 * @website http://www.json.cn/java2pojo/
 */
public class MessageResultInputBean extends MessageBean{


    private String recordId;
    private String sessionId;
    private String topic;
    private String var;
    private String text;

    private int eof;

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getVar() {
        return var;
    }

    public void setVar(String var) {
        this.var = var;
    }

    public int getEof() {
        return eof;
    }

    public void setEof(int eof) {
        this.eof = eof;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "MessageResultInputBean{" +
                "recordId='" + recordId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", topic='" + topic + '\'' +
                ", var='" + var + '\'' +
                ", text='" + text + '\'' +
                ", eof=" + eof +
                '}';
    }
}