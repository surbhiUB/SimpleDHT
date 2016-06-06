package edu.buffalo.cse.cse486586.simpledht;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by surbhibh on 3/29/16.
 */
public class Message implements Serializable{

    private static final long serialVersionUID = 1L;

    String senderPort="";
    HashMap<String,String> keyValueMap = new HashMap<String,String>();

    String largestId="";
    String smallestId="";
    String predecessorPort="";
    String successorPort="";
    String toPortId="";

    public String getQueryingPort() {
        return queryingPort;
    }

    public void setQueryingPort(String queryingPort) {
        this.queryingPort = queryingPort;
    }

    String queryingPort="";
    String messageType="";
    String keySelection="";
    String keyValue="";


    public String getKeyValue() {
        return keyValue;
    }

    public void setKeyValue(String keyValue) {
        this.keyValue = keyValue;
    }


    public String getKeySelection() {
        return keySelection;
    }

    public void setKeySelection(String keySelection) {
        this.keySelection = keySelection;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }




    public String getSmallestId() {
        return smallestId;
    }

    public void setSmallestId(String smallestId) {
        this.smallestId = smallestId;
    }

    public String getLargestId() {
        return largestId;
    }

    public void setLargestId(String largestId) {
        this.largestId = largestId;
    }

    public String getPredecessorPort() {
        return predecessorPort;
    }

    public void setPredecessorPort(String predecessorPort) {
        this.predecessorPort = predecessorPort;
    }

    public String getSuccessorPort() {
        return successorPort;
    }

    public void setSuccessorPort(String successorPort) {
        this.successorPort = successorPort;
    }


    public String getToPortId() {
        return toPortId;
    }

    public void setToPortId(String toPortId) {
        this.toPortId = toPortId;
    }


    public String getSenderPort() {
        return senderPort;
    }

    public void setSenderPort(String senderPort) {
        this.senderPort = senderPort;
    }

    public HashMap<String, String> getKeyValueMap() {
        return keyValueMap;
    }

    public void setKeyValueMap(HashMap<String, String> keyValueMap) {
        this.keyValueMap = keyValueMap;
    }


}